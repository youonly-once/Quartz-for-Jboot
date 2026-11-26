package com.shouxun.quartz.config;

import com.shouxun.quartz.annotation.QuartzSchedule;
import com.shouxun.quartz.constant.QuartzScheduleType;
import lombok.Data;
import org.quartz.DateBuilder;

import java.io.Serializable;

/**
 * Quartz 调度配置
 *
 * 用于运行时动态配置和修改任务调度参数。
 */
@Data
public class QuartzScheduleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 调度类型
     */
    private QuartzScheduleType type = QuartzScheduleType.CRON;

    /**
     * Cron 表达式
     */
    private String value = "";

    /**
     * 固定间隔
     */
    private int interval = 60;

    /**
     * Trigger 名称
     */
    private String triggerName = "";

    /**
     * Trigger 分组
     */
    private String triggerGroup = "DEFAULT_GROUP";

    /**
     * 间隔单位
     */
    private DateBuilder.IntervalUnit intervalUnit =
            DateBuilder.IntervalUnit.SECOND;

    /**
     * Job 名称
     */
    private String jobName = "";

    /**
     * Job 分组
     */
    private String jobGroup = "DEFAULT_GROUP";

    /**
     * 任务描述
     */
    private String description = "";

    /**
     * 是否允许并发执行
     */
    private boolean concurrent = false;

    private String params;

    private String paramsDescription;

    /**
     * 通知方式：短信
     */
    public static final String NOTIFY_TYPE_SMS = "SMS";

    /**
     * 通知方式：电话
     */
    public static final String NOTIFY_TYPE_PHONE = "PHONE";

    /**
     * 异常通知：true 开启，false 关闭
     */
    private Boolean exceptionNotify = false;

    /**
     * 通知方式：SMS 短信，PHONE 电话
     */
    private String notifyType = NOTIFY_TYPE_SMS;

    /**
     * 通知号码
     */
    private String notifyPhone = "";

    /**
     * 连续异常次数达到该值时通知
     */
    private Integer exceptionCount = 1;

    /**
     * 自定义通知内容，支持 {{变量}} 占位符；为空时使用 AI 汇总后的异常信息
     */
    private String notifyContent = "";

    public QuartzScheduleConfig() {
    }

    /**
     * 根据注解创建配置
     */
    public QuartzScheduleConfig(QuartzSchedule annotation) {
        if (annotation == null) {
            return;
        }
        this.type = annotation.type();
        this.value = annotation.value();
        this.interval = annotation.interval();
        this.triggerName = annotation.triggerName();
        this.triggerGroup = annotation.triggerGroup();
        this.intervalUnit = annotation.intervalUnit();
        this.jobName = annotation.jobName();
        this.jobGroup = annotation.jobGroup();
        this.description = annotation.description();
        this.concurrent = annotation.concurrent();
        this.params = annotation.params();
    }

}