package com.shouxun.quartz.service;

import cn.hutool.core.bean.BeanUtil;
import com.jfinal.aop.Inject;
import com.jfinal.kit.Ret;
import com.jfinal.plugin.activerecord.Page;
import com.jfinal.plugin.activerecord.Record;
import com.jfinal.plugin.activerecord.SqlPara;
import com.shouxun.quartz.QuartzSchedulerManager;
import com.shouxun.quartz.config.QuartzScheduleConfig;
import com.shouxun.quartz.constant.QuartzScheduleType;
import com.shouxun.quartz.dto.QuartzTaskParam;
import com.shouxun.quartz.factory.QuartzTriggerFactory;
import com.shouxun.quartz.util.MyDb;
import com.shouxun.quartz.util.PaginateByFullSqlUtil;
import com.shouxun.quartz.util.QuartzCronUtils;
import io.jboot.aop.annotation.Bean;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.utils.Key;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.sql.Blob;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Quartz 管理业务。
 *
 * 需要项目注入已创建的 Scheduler；本类不负责 Scheduler 生命周期。
 * 任务创建时通过 QuartzJobTypeProvider 将 jobType 映射到允许使用的 JobDetail。
 */
@Bean
public class QuartzService {

    @Inject
    private QuartzSchedulerManager quartzSchedulerManager;

    private Scheduler scheduler;


    @PostConstruct
    private void init(){
        scheduler = quartzSchedulerManager.getScheduler();
    }

    public Page<Record> list(int pageNum, int pageSize,
                             String jobNameLike, String jobGroupLike, String stateFilter, String lastExecuteStatus, Integer minDuration) throws SchedulerException {

        String sql = """
                
                SELECT
                    j.JOB_NAME AS "jobName",
                    j.JOB_GROUP AS "jobGroup",
                    j.DESCRIPTION AS "description",
                    j.JOB_CLASS_NAME AS "jobClassName",
                    j.JOB_DATA AS "jobData",
                    t.TRIGGER_NAME AS "triggerName",
                    t.TRIGGER_GROUP AS "triggerGroup",
                    t.TRIGGER_TYPE AS "triggerType",
                    t.TRIGGER_STATE AS "triggerState",
                    t.NEXT_FIRE_TIME AS "nextFireTimeMillis",
                    t.PREV_FIRE_TIME AS "previousFireTimeMillis",
                    c.CRON_EXPRESSION AS "cronExpression",
                    l.EXECUTE_STATUS AS "executeStatus",
                    l.FIRE_TIME AS "lastFireTime",
                    l.DURATION AS "lastDuration"
                FROM QRTZ_JOB_DETAILS j
                
                LEFT JOIN QRTZ_TRIGGERS t
                    ON t.SCHED_NAME = j.SCHED_NAME
                    AND t.JOB_NAME = j.JOB_NAME
                    AND t.JOB_GROUP = j.JOB_GROUP
                
                LEFT JOIN QRTZ_CRON_TRIGGERS c
                    ON c.SCHED_NAME = t.SCHED_NAME
                    AND c.TRIGGER_NAME = t.TRIGGER_NAME
                    AND c.TRIGGER_GROUP = t.TRIGGER_GROUP
                
                LEFT JOIN (
                    SELECT
                        x.JOB_NAME,
                        x.JOB_GROUP,
                        x.EXECUTE_STATUS,
                        x.FIRE_TIME,x.DURATION
                    FROM (
                        SELECT
                            a.JOB_NAME,
                            a.JOB_GROUP,
                            a.EXECUTE_STATUS,
                            a.FIRE_TIME,a.DURATION,
                            ROW_NUMBER() OVER (
                                PARTITION BY a.JOB_NAME, a.JOB_GROUP
                                ORDER BY a.FIRE_TIME DESC
                            ) AS RN
                        FROM QUARTZ_JOB_LOG a
                    ) x
                    WHERE x.RN = 1
                ) l
                    ON l.JOB_NAME = j.JOB_NAME
                    AND l.JOB_GROUP = j.JOB_GROUP
                
                WHERE 1 = 1
                """;
        SqlPara sqlPara = new SqlPara();
        // QRTZ_* 表以 SCHED_NAME 区分调度器实例，push-api 与 biz-web 共用同一套表，
        // 必须只查当前 Scheduler 实例的数据，否则 getJobDetail 会取到其他实例的行而返回 null
        sql = sql + """
               AND j.SCHED_NAME = ?
            """;
        sqlPara.addPara(scheduler.getSchedulerName());
        if (minDuration!=null && minDuration>0){
            sql  = sql + """
                   AND l.DURATION> ?
                """;
            sqlPara.addPara(minDuration);
        }
        if (StringUtils.isNotBlank(jobNameLike)) {
            sql  = sql + """
                   AND j.JOB_NAME LIKE ?
                """;
            sqlPara.addPara("%"+jobNameLike+"%");
        }
        if (StringUtils.isNotBlank(jobGroupLike)) {
            sql  = sql + """
                   AND j.JOB_GROUP LIKE ?
                """;
            sqlPara.addPara("%"+jobGroupLike+"%");
        }
        if (StringUtils.isNotBlank(stateFilter)) {
            sql  = sql + """
                   AND t.TRIGGER_STATE =?
                """;
            sqlPara.addPara(stateFilter);
        }
        if (StringUtils.isNotBlank(lastExecuteStatus)) {
            sql  = sql + """
                   AND l.EXECUTE_STATUS =?
                """;
            sqlPara.addPara(lastExecuteStatus);
        }
        sql = sql+ """
                
                ORDER BY t.TRIGGER_STATE,j.JOB_NAME, j.JOB_GROUP
                """;
        sqlPara.setSql(sql);

        Page<Record> jobs = MyDb.orcl.paginateByFullSql(pageNum, pageSize, PaginateByFullSqlUtil.getTotalRowSql(sql), sql,sqlPara.getPara());
        for (Record key : jobs.getList()) {
            key.put("triggerStateDesc",stateText(key.getStr("triggerState")));
            key.put("triggerState",key.getStr("triggerState")==null?"NONE":key.getStr("triggerState"));
            key.put("nextFireTime",format(key.getBigDecimal("nextFireTimeMillis")));
            key.put("previousFireTime" ,format(key.getBigDecimal("previousFireTimeMillis")));
            key.put("cronDescription", QuartzCronUtils.toDescription(key.getStr("cronExpression")));

            key.put("triggerTypeText", key.getStr("triggerType"));
            Object jobDataO = key.get("jobData");
            if (jobDataO!=null){
                byte[] jobDataByte =  (byte[]) jobDataO;
                Map<String,Object> jobData = getMapFromBlob(jobDataByte);
                if (jobData!=null && jobData.containsKey("config")){
                    QuartzScheduleConfig config = (QuartzScheduleConfig)jobData.get("config");
                    if (config != null) {
                        key.put("scheduleTypeText", scheduleTypeDescription(config.getType()));
                        key.put("cronDescription", scheduleDescription(config));
                        key.put(BeanUtil.beanToMap(config));
                        //String cronExpression = key.getStr("cronExpression");
                        //if (StringUtils.isNotEmpty(cronExpression)){
                          //  key.put("cronDescription", key.getStr("cronDescription")+" ( "+cronExpression+" )");
                        //}
                    }
                }
                key.remove("jobData");
            }

        }

        return jobs;
    }
    private Map<String,Object> getMapFromBlob(byte[] bytes){
        if (bytes != null && bytes.length != 0L) {
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object obj = ois.readObject();
                return (Map<String,Object>) obj;
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
    private String scheduleTypeDescription(QuartzScheduleType scheduleType) {


        return switch (scheduleType) {
            case CRON ->  "CRON";
            case SIMPLE -> "单次";
            case FIXED_DELAY ->"固定延迟";
            case CALENDAR_INTERVAL ->"日历间隔";
            case DAILY_TIME_INTERVAL ->"范围内间隔";

            default -> "未知调度类型";
        };
    }
    private String scheduleDescription(QuartzScheduleConfig config) {
        if (config == null) {
            return "未获取到调度配置";
        }

        return switch (config.getType()) {
            case CRON -> {
                String cron = config.getValue();
                yield cron == null || cron.isBlank()
                        ? "Cron 表达式未配置"
                        : QuartzCronUtils.toDescription(cron);
            }

            case SIMPLE ->
                    calendarIntervalDescription(config) ;

            case FIXED_DELAY ->
                      calendarIntervalDescription(config);

            case CALENDAR_INTERVAL ->
                    calendarIntervalDescription(config);

            case DAILY_TIME_INTERVAL ->
                    dailyIntervalDescription(config);

            default -> "未知调度类型";
        };
    }
    private String calendarIntervalDescription(QuartzScheduleConfig schedule) {
        String unit = switch (schedule.getIntervalUnit()) {
            case MILLISECOND -> "毫秒";
            case SECOND -> "秒";
            case MINUTE -> "分";
            case HOUR -> "小时";
            case DAY -> "天";
            case WEEK -> "周";
            case MONTH -> "月";
            case YEAR -> "年";
        };

        return  schedule.getInterval() + " " + unit + "/次";
    }

    private String dailyIntervalDescription(QuartzScheduleConfig schedule) {
        return "每日时间间隔调度";
    }
    public Map<String, Object> info(String jobName, String jobGroup) throws SchedulerException {
        JobKey key = quartzSchedulerManager.jobKey(jobName, jobGroup);
        JobDetail detail = scheduler.getJobDetail(key);
        if (detail == null) throw new IllegalArgumentException("任务不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobName", key.getName());
        result.put("jobGroup", key.getGroup());
        result.put("description", detail.getDescription());
        result.put("params", detail.getJobDataMap().getString("params"));
        QuartzScheduleConfig config = (QuartzScheduleConfig) detail.getJobDataMap().get("config");
        if (config != null) {
            result.put("exceptionNotify", config.getExceptionNotify());
            result.put("notifyType", config.getNotifyType());
            result.put("notifyPhone", config.getNotifyPhone());
            result.put("exceptionCount", config.getExceptionCount());
            result.put("notifyContent", config.getNotifyContent());
        }
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(key);
        if (!triggers.isEmpty() && triggers.getFirst() instanceof CronTrigger) {
            result.put("cronExpression", ((CronTrigger) triggers.getFirst()).getCronExpression());
        } else {
            result.put("cronExpression", "");
        }
        return result;
    }

    public Ret save(QuartzTaskParam p) throws Exception {
        validate(p, true);
        JobKey key = quartzSchedulerManager.jobKey(p.getJobName(), p.getJobGroup());
        TriggerKey triggerKey = quartzSchedulerManager.triggerKey(p.getJobName(), p.getJobGroup());
        if (scheduler.checkExists(key)) throw new IllegalArgumentException("任务已存在");


        QuartzScheduleConfig config = new QuartzScheduleConfig();
        config.setJobName(p.getJobName());
        config.setJobGroup(p.getJobGroup());
        config.setDescription(p.getDescription());
        config.setParams(p.getParams());
        applyNotifyConfig(config, p);

        if (p.getScheduleType().equals(QuartzScheduleType.FIXED_DELAY.name())){
            config.setType(QuartzScheduleType.FIXED_DELAY);
            if (p.getInterval() == null || p.getInterval()<=0) {
                throw new IllegalArgumentException("执行间隔必须大于0");
            }
            config.setInterval(Math.toIntExact(p.getInterval()));
            config.setIntervalUnit(DateBuilder.IntervalUnit.valueOf(p.getIntervalUnit()));

        } else if (p.getScheduleType().equals(QuartzScheduleType.CRON.name())){
            if (!notBlank(p.getCronExpression())) throw new IllegalArgumentException("Cron表达式不能为空");
            try {
                CronExpression.validateExpression(p.getCronExpression());
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cron表达式格式错误：" + e.getMessage());
            }
            config.setType(QuartzScheduleType.CRON);
            config.setValue(p.getCronExpression());

        }else{
            throw new IllegalArgumentException("类型不支持：" +p.getScheduleType());
        }
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("config", config);
        JobDetail detail = quartzSchedulerManager.buildJobDetail(p.getJobName(), key, p.getDescription(),jobDataMap);

        Trigger trigger = QuartzTriggerFactory.createTrigger(key, triggerKey, config);
        Date date = scheduler.scheduleJob(detail, trigger);
       return Ret.ok("data",date);
    }

    public Ret update(QuartzTaskParam p) throws Exception {
        validate(p, false);
        JobKey key = quartzSchedulerManager.jobKey(p.getJobName(), p.getJobGroup());
        if (!scheduler.checkExists(key)) throw new IllegalArgumentException("任务不存在");
        JobDataMap jobDataMap = scheduler.getJobDetail(key).getJobDataMap();
        QuartzScheduleConfig config = (QuartzScheduleConfig)(
                jobDataMap
                        .get("config"));
        TriggerKey tk = quartzSchedulerManager.triggerKey(p.getJobName(), p.getJobGroup());
        config.setParams(p.getParams());
        config.setDescription(p.getDescription());
        applyNotifyConfig(config, p);
        if (p.getScheduleType().equals(QuartzScheduleType.FIXED_DELAY.name())){
            config.setType(QuartzScheduleType.FIXED_DELAY);
            if (p.getInterval() == null || p.getInterval() <= 0) {
                throw new IllegalArgumentException("执行间隔必须大于0");
            }
            config.setInterval(Math.toIntExact(p.getInterval()));
            config.setIntervalUnit(DateBuilder.IntervalUnit.valueOf(p.getIntervalUnit()));


        } else if (p.getScheduleType().equals(QuartzScheduleType.CRON.name())){
            if (!notBlank(p.getCronExpression())) throw new IllegalArgumentException("Cron表达式不能为空");
            try {
                CronExpression.validateExpression(p.getCronExpression());
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cron表达式格式错误：" + e.getMessage());
            }
            config.setType(QuartzScheduleType.CRON);
            config.setValue(p.getCronExpression());

        }else{
            throw new IllegalArgumentException("类型不支持：" +p.getScheduleType());
        }
        Trigger trigger = QuartzTriggerFactory.createTrigger(key, tk, config);
        jobDataMap.put("config", config);
        JobDetail jobDetail = quartzSchedulerManager
                .buildJobDetail(p.getJobName(), key, p.getDescription(),jobDataMap);
        scheduler.addJob(jobDetail, true, true);

        if (scheduler.checkExists(tk)) return Ret.ok("data",scheduler.rescheduleJob(tk, trigger));
        else  return Ret.ok("data",scheduler.scheduleJob(trigger));
    }

    public void pause(String name, String group) throws SchedulerException {
        quartzSchedulerManager.pauseJob(name, group);
    }

    public void resume(String name, String group) throws SchedulerException {
        quartzSchedulerManager.resumeJob(name, group);
    }

    public void trigger(String name, String group) throws SchedulerException {
        quartzSchedulerManager.triggerJob(name, group);
    }

    public boolean remove(String name, String group) throws SchedulerException {
        return quartzSchedulerManager.deleteJob(name, group);
    }



    private void applyNotifyConfig(QuartzScheduleConfig config, QuartzTaskParam p) {
        boolean exceptionNotify = Boolean.TRUE.equals(p.getExceptionNotify());
        if (exceptionNotify) {
            if (!QuartzScheduleConfig.NOTIFY_TYPE_SMS.equals(p.getNotifyType())
                    && !QuartzScheduleConfig.NOTIFY_TYPE_PHONE.equals(p.getNotifyType())) {
                throw new IllegalArgumentException("请选择通知方式");
            }
            if (!notBlank(p.getNotifyPhone())) {
                throw new IllegalArgumentException("通知号码不能为空");
            }
            if (p.getExceptionCount() == null || p.getExceptionCount() < 1) {
                throw new IllegalArgumentException("异常次数必须大于0");
            }
        }
        boolean notifyTypeValid = QuartzScheduleConfig.NOTIFY_TYPE_SMS.equals(p.getNotifyType())
                || QuartzScheduleConfig.NOTIFY_TYPE_PHONE.equals(p.getNotifyType());
        config.setExceptionNotify(exceptionNotify);
        config.setNotifyType(notifyTypeValid ? p.getNotifyType() : QuartzScheduleConfig.NOTIFY_TYPE_SMS);
        config.setNotifyPhone(p.getNotifyPhone() == null ? "" : p.getNotifyPhone().trim());
        config.setExceptionCount(
                p.getExceptionCount() == null || p.getExceptionCount() < 1 ? 1 : p.getExceptionCount());
        String notifyContent = p.getNotifyContent() == null ? "" : p.getNotifyContent().trim();
        if (notifyContent.length() > 500) {
            throw new IllegalArgumentException("通知内容不能超过500个字符");
        }
        config.setNotifyContent(notifyContent);
    }

    private void validate(QuartzTaskParam p, boolean create) {
        if (p == null) throw new IllegalArgumentException("请求参数不能为空");
        if (!notBlank(p.getScheduleType())) throw new IllegalArgumentException("触发器类型不能为空");
        if (!notBlank(p.getJobName())) throw new IllegalArgumentException("任务名称不能为空");
        if (!notBlank(p.getJobGroup())) throw new IllegalArgumentException("任务分组不能为空");

    }

    private String stateText(String state) {
        if (state == null || state.trim().isEmpty()) {
            return "未开启";
        }

        return switch (state) {
            case "WAITING" -> "正常等待";
            case "ACQUIRED" -> "已获取";
            case "EXECUTING" -> "执行中";
            case "BLOCKED" -> "执行阻塞";
            case "PAUSED" -> "已暂停";
            case "PAUSED_BLOCKED" -> "暂停且阻塞";
            case "COMPLETE" -> "已完成";
            case "ERROR" -> "执行异常";
            case "NONE" -> "无触发器";
            default -> "未知状态：" + state;
        };
    }

    private String triggerTypeText(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "未知状态";
        }

        return switch (type) {
            case "CRON" -> "Cron";
            case "SIMPLE" -> "固定间隔或单次";
            case "CAL_INT" -> "日历间隔";
            case "DAILY_I" -> "固定时间范围内间隔触发";
            default -> "未知状态：" + type;
        };
    }

    private Date format(BigDecimal dateTime) {
        return dateTime==null||dateTime.longValue()<0?null:new Date(dateTime.longValue());
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }


    public Set<String> getAllJobList() throws SchedulerException {
        Set<JobKey> jobKeys = quartzSchedulerManager.getScheduler().getJobKeys(GroupMatcher.anyGroup());
        return  jobKeys.stream().map(Key::getName).collect(Collectors.toSet());
    }
}
