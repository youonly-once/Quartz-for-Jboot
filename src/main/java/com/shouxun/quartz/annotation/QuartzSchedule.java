package com.shouxun.quartz.annotation;

import com.shouxun.quartz.constant.QuartzScheduleType;
import org.quartz.DateBuilder;

import java.lang.annotation.*;

/**
 * Quartz cron annotation for scheduling Job classes.
 * <p>
 * Annotate a class that implements {@link org.quartz.Job} with this
 * annotation. The QuartzSchedulerManager will auto-scan and register
 * it at application startup. Unlike Jboot native Cron annotation
 * (cron4j 5-field), this annotation uses Quartz 7-field cron
 * (second minute hour day month week year, year optional).
 * <p>
 * Example:
 * <pre>
 * public class MyJob implements Job {
 *     public void execute(JobExecutionContext context) {
 *         // do something
 *     }
 * }
 * </pre>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface QuartzSchedule {
    String params() default "";

    String paramsDescription() default "{}";
    /**
     * 调度类型
     */
    QuartzScheduleType type() default QuartzScheduleType.CRON;

    /**
     * 固定间隔，单位：秒
     * FIXED_RATE 和 FIXED_DELAY 使用
     */
    int interval() default 60;

    /**
     * Trigger 名称
     */
    String triggerName() default "";

    /**
     * Trigger 分组
     */
    String triggerGroup() default "DEFAULT_GROUP";

    /**
     * 间隔单位
     */
    DateBuilder.IntervalUnit intervalUnit() default DateBuilder.IntervalUnit.SECOND;

    /**
     * Quartz cron expression (7 fields: second minute hour day month week year).
     */
    String value() default "0 */2 * * * ?";


    /**
     * Job name to uniquely identify the Job in Scheduler.
     * Default empty string uses the class simple name.
     */
    String jobName() default "";

    /**
     * Job group, default DEFAULT_GROUP.
     */
    String jobGroup() default "DEFAULT_GROUP";

    /**
     * Description text for the Job.
     */
    String description() default "";

    /**
     * 是否允许并发执行
     */
    boolean concurrent() default false;

    /**
     * 是否开启（是否注册trigger）
     */
    boolean enabled() default true;
}


