package com.shouxun.quartz.notify;

import com.jfinal.plugin.activerecord.Record;
import com.shouxun.quartz.config.QuartzScheduleConfig;
import com.shouxun.quartz.util.MyDb;
import io.jboot.Jboot;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务异常通知。
 * <p>
 * 任务执行失败后统计连续失败次数，达到配置的异常次数时发布事件，
 * 由业务模块（biz-web）监听事件后通过短信或电话发送通知。
 * 连续失败次数达到阈值只通知一次，任务执行成功后会重新计数。
 */
@Slf4j
public class QuartzJobExceptionNotifier {

    /**
     * 异常通知事件主题，业务模块通过该主题监听并发送通知
     */
    public static final String EVENT_TOPIC = "quartz_job_exception_notify";

    private QuartzJobExceptionNotifier() {
    }

    public static void notifyIfNecessary(QuartzScheduleConfig config,
                                         String jobName,
                                         String jobGroup,
                                         String exceptionMsg) {
        try {
            if (config == null || !Boolean.TRUE.equals(config.getExceptionNotify())) {
                return;
            }
            String phone = config.getNotifyPhone();
            if (phone == null || phone.trim().isEmpty()) {
                return;
            }
            int threshold = config.getExceptionCount() == null || config.getExceptionCount() < 1
                    ? 1 : config.getExceptionCount();
            int failCount = countConsecutiveFailures(jobName, jobGroup, threshold);
            // 连续异常次数达到配置值时通知一次，避免持续异常时重复通知
            if (failCount != threshold) {
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("jobName", jobName);
            data.put("jobGroup", jobGroup);
            data.put("notifyType", config.getNotifyType());
            data.put("notifyPhone", phone.trim());
            data.put("exceptionCount", threshold);
            data.put("failCount", failCount);
            data.put("exceptionMsg", exceptionMsg);
            data.put("notifyContent", config.getNotifyContent());

            Jboot.sendEvent(EVENT_TOPIC, data);
            log.info("Quartz job exception notify sent: {}:{}, failCount={}",
                    jobGroup, jobName, failCount);
        } catch (Exception e) {
            // 通知失败不能影响任务执行结果的记录
            log.error("Quartz job exception notify failed: {}:{}", jobGroup, jobName, e);
        }
    }

    /**
     * 统计任务最近连续失败次数（遇到成功记录即停止）
     */
    private static int countConsecutiveFailures(String jobName, String jobGroup, int threshold) {
        List<Record> records = MyDb.orcl.find("""
                SELECT * FROM (
                    SELECT EXECUTE_STATUS
                    FROM QUARTZ_JOB_LOG
                    WHERE JOB_NAME = ? AND JOB_GROUP = ?
                      AND EXECUTE_STATUS IN ('SUCCESS', 'FAIL')
                    ORDER BY FIRE_TIME DESC
                ) WHERE ROWNUM <= ?
                """, jobName, jobGroup, threshold + 1);

        int count = 0;
        for (Record record : records) {
            if ("FAIL".equals(record.getStr("EXECUTE_STATUS"))) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
