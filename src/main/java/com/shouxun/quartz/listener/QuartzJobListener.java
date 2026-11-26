
package com.shouxun.quartz.listener;

import com.jfinal.aop.Inject;
import com.jfinal.plugin.activerecord.Record;
import com.shouxun.quartz.QuartzSchedulerManager;
import com.shouxun.quartz.annotation.QuartzSchedule;
import com.shouxun.quartz.config.QuartzScheduleConfig;
import com.shouxun.quartz.constant.QuartzScheduleType;
import com.shouxun.quartz.factory.QuartzTriggerFactory;
import com.shouxun.quartz.log.QuartzJobLogContext;
import com.shouxun.quartz.notify.QuartzJobExceptionNotifier;
import com.shouxun.quartz.service.QuartzLogService;
import com.shouxun.quartz.service.impl.QuartzLogServiceImpl;
import io.jboot.aop.annotation.Bean;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Bean
public class QuartzJobListener implements JobListener {

    @Inject
    private QuartzLogService logService;

    @Inject
    private QuartzSchedulerManager quartzSchedulerManager;

    @Override
    public String getName() {
        return "GlobalQuartzJobListener";
    }

    /**
     * 任务开始执行：插入一条 RUNNING 记录。
     */
    @Override
    public void jobToBeExecuted(JobExecutionContext context) {

        // 开启本线程的错误日志收集，任务内部 catch 后仅 log.error 的异常也能落库
        QuartzJobLogContext.begin();

        try {
            JobKey jobKey = context.getJobDetail().getKey();
            TriggerKey triggerKey = context.getTrigger().getKey();

            long startTime = System.currentTimeMillis();

            Record record = new Record();
            record.set("FIRE_INSTANCE_ID", context.getFireInstanceId());
            record.set("JOB_NAME", jobKey.getName());
            record.set("JOB_GROUP", jobKey.getGroup());
            record.set("TRIGGER_NAME", triggerKey.getName());
            record.set("TRIGGER_GROUP", triggerKey.getGroup());
            record.set("FIRE_TIME", context.getFireTime() == null
                    ? null : new Timestamp(context.getFireTime().getTime()));
            record.set("START_TIME", new Timestamp(startTime));

            String logId = logService.start(record);

            // 将本次执行的日志 ID 保存到执行上下文
            context.put("QUARTZ_LOG_ID", logId);
            context.put("QUARTZ_START_TIME", startTime);

            log.info("Quartz Job started: {}:{}",
                    jobKey.getGroup(), jobKey.getName());

        } catch (Exception e) {
            // 日志记录失败不能影响业务任务执行
            log.error("Quartz Job start log failed", e);
        }
    }

    /**
     * 任务执行被否决。
     */
    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {

        // 任务未执行，直接释放收集上下文
        QuartzJobLogContext.end();

        try {
            JobKey jobKey = context.getJobDetail().getKey();
            TriggerKey triggerKey = context.getTrigger().getKey();

            Record record = new Record();
            record.set("FIRE_INSTANCE_ID", context.getFireInstanceId());
            record.set("JOB_NAME", jobKey.getName());
            record.set("JOB_GROUP", jobKey.getGroup());
            record.set("TRIGGER_NAME", triggerKey.getName());
            record.set("TRIGGER_GROUP", triggerKey.getGroup());
            record.set("FIRE_TIME", context.getFireTime() == null
                    ? null : new Timestamp(context.getFireTime().getTime()));

            logService.vetoed(record);

            log.warn("Quartz Job vetoed: {}:{}",
                    jobKey.getGroup(), jobKey.getName());

        } catch (Exception e) {
            log.error("Quartz Job veto log failed", e);
        }
    }

    /**
     * 任务执行结束：更新执行结果。
     */
    @Override
    public void jobWasExecuted(
            JobExecutionContext context,
            JobExecutionException jobException) {

        // 取出任务执行期间收集到的 ERROR 日志（任务内部 catch 后仅打印日志的异常）
        String collectedLog = QuartzJobLogContext.end();

        try {
            String logId = (String) context.get("QUARTZ_LOG_ID");

            // 开始记录失败时，没有日志 ID
            if (logId == null) {
                log.warn("Quartz Job completion log skipped: {}",
                        context.getJobDetail().getKey());
                return;
            }

            long endTime = System.currentTimeMillis();

            Object startValue = context.get("QUARTZ_START_TIME");
            long startTime = startValue instanceof Long
                    ? (Long) startValue
                    : endTime - context.getJobRunTime();

            long duration = Math.max(0, endTime - startTime);

            String status = jobException == null
                    ? "SUCCESS" : "FAIL";

            String resultMsg = context.getResult() == null
                    ? null : String.valueOf(context.getResult());

            // 防止结果字段过长
            if (resultMsg != null && resultMsg.length() > 2000) {
                resultMsg = resultMsg.substring(0, 2000);
            }

            String exceptionMsg = null;

            if (jobException != null) {
                StringWriter writer = new StringWriter();
                jobException.printStackTrace(new PrintWriter(writer));
                exceptionMsg = writer.toString();
            }

            // 任务内部被吞掉的 ERROR 日志同样记为失败原因
            if (collectedLog != null) {
                exceptionMsg = exceptionMsg == null
                        ? collectedLog
                        : collectedLog + "\n" +exceptionMsg ;
            }

            logService.finish(
                    logId,
                    status,
                    duration,
                    resultMsg,
                    exceptionMsg
            );

            // 执行失败时按配置的异常次数发送短信/电话通知
            try {
                if ("FAIL".equals(status)) {
                    JobKey jobKey = context.getJobDetail().getKey();
                    Object configObj = context.getJobDetail().getJobDataMap().get("config");
                    if (configObj instanceof QuartzScheduleConfig config) {
                        QuartzJobExceptionNotifier.notifyIfNecessary(
                                config, jobKey.getName(), jobKey.getGroup(), exceptionMsg);
                    }
                }
            }catch (Exception e) {
                log.error("Quartz Job notify failed", e);
            }

            if (jobException == null) {
                log.info("Quartz Job succeeded: {}, duration={}ms",
                        context.getJobDetail().getKey(), duration);
            } else {
                log.error("Quartz Job failed: {}, duration={}ms",
                        context.getJobDetail().getKey(), duration,
                        jobException);
            }

        } catch (Exception e) {
            log.error("Quartz Job completion log failed", e);
        }finally {
            // 兜底清理，避免线程复用导致错误日志串到下一次执行
            try {
                QuartzJobLogContext.end();
            }catch (Exception e) {
                log.error("Quartz Job end log failed", e);
            }
            scheduleNextFixedDelay(context,System.currentTimeMillis());
        }
    }
    /**
     * FIXED_DELAY：任务完成后，延迟指定时间创建下一次执行。
     */
    private void scheduleNextFixedDelay(
            JobExecutionContext context,
            long completedTime) {

        try {
            JobDetail jobDetail = context.getJobDetail();
            JobKey jobKey = jobDetail.getKey();

            // 只处理 FIXED_DELAY 类型
            QuartzScheduleConfig config = (QuartzScheduleConfig)(jobDetail.getJobDataMap()
                    .get("config"));

            if (QuartzScheduleType.FIXED_DELAY!=config.getType()) {
                return;
            }

            TriggerKey triggerKey = quartzSchedulerManager.triggerKey(config.getJobName(), config.getJobGroup());

            if (quartzSchedulerManager.isJobPaused(triggerKey)) {
                return;
            }

            // 获取延迟时间
            Number intervalValue = config.getInterval();

            long delayMillis = intervalValue.longValue();

            if (delayMillis <= 0) {
                log.error("FIXED_DELAY interval must be greater than 0: {}",
                        jobKey);
                return;
            }

            // 计算下一次执行时间
            long millis = QuartzTriggerFactory.toMillis(delayMillis, config.getIntervalUnit());
            Date nextFireTime = new Date(
                    Math.addExact(completedTime, millis)
            );


            // 创建单次 SimpleTrigger
            SimpleTrigger nextTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .startAt(nextFireTime)
                    .withSchedule(
                            SimpleScheduleBuilder.simpleSchedule()
                                    .withRepeatCount(0)
                                    .withMisfireHandlingInstructionFireNow()
                    )
                    .build();

            // 保存到 Quartz JobStore
            context.getScheduler().rescheduleJob(triggerKey,nextTrigger);

            log.info(
                    "FIXED_DELAY next trigger scheduled: job={}, nextFireTime={}, delay={}ms",
                    jobKey,
                    nextFireTime,
                    delayMillis
            );

        } catch (Exception e) {
            log.error("FIXED_DELAY schedule next trigger failed: {}",
                    context.getJobDetail().getKey(), e);
        }
    }
}
