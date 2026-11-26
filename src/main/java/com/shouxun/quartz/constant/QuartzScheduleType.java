package com.shouxun.quartz.constant;

public enum QuartzScheduleType {

    /**
     * Cron 表达式调度
     */
    CRON,

    /**
     * 固定时间间隔调度
     */
    SIMPLE,

    /**
     * 日历间隔调度
     */
    CALENDAR_INTERVAL,

    /**
     * 每日时间范围间隔调度
     */
    DAILY_TIME_INTERVAL,

    /**
     * 上次执行完成后延迟指定时间
     */
    FIXED_DELAY
}