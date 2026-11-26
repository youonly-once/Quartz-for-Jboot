package com.shouxun.quartz.factory;

import com.shouxun.quartz.annotation.QuartzSchedule;
import com.shouxun.quartz.config.QuartzScheduleConfig;
import org.quartz.*;
import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder.IntervalUnit;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.impl.triggers.CalendarIntervalTriggerImpl;

import java.util.Date;
import java.util.UUID;

public final class QuartzTriggerFactory {

    private QuartzTriggerFactory() {
    }


    /**
     * 根据注解和指定 Key 创建 Trigger
     */
    public static Trigger createTrigger(
            JobKey jobKey,
            TriggerKey triggerKey,
            QuartzScheduleConfig schedule) {

        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withDescription(schedule.getDescription());

        return switch (schedule.getType()) {
            case CRON -> createCronTrigger(builder, schedule);
            case SIMPLE -> createSimpleTrigger(builder, schedule);
            case CALENDAR_INTERVAL -> createCalendarIntervalTrigger(builder, schedule);
            case DAILY_TIME_INTERVAL -> createDailyTimeIntervalTrigger(builder, schedule);
            case FIXED_DELAY -> createFixedDelayTrigger(builder, schedule);
            default -> throw new IllegalArgumentException(
                    "不支持的调度类型：" + schedule.getType()
            );
        };
    }

    /**
     * CronTrigger
     */
    private static Trigger createCronTrigger(
            TriggerBuilder<Trigger> builder,
            QuartzScheduleConfig schedule) {

        String cron = getCronExpression(schedule);

        if (cron.isEmpty()) {
            throw new IllegalArgumentException("Cron 表达式不能为空");
        }

        return builder
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(cron).withMisfireHandlingInstructionFireAndProceed()
                )
                .build();
    }

    /**
     * SimpleTrigger
     */
    private static Trigger createSimpleTrigger(
            TriggerBuilder<Trigger> builder,
            QuartzScheduleConfig schedule) {

        int interval = schedule.getInterval();

        if (interval <= 0) {
            throw new IllegalArgumentException("执行间隔必须大于 0");
        }

        long intervalMillis = toMillis(interval, schedule.getIntervalUnit());

        return builder
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(intervalMillis)
                                .repeatForever()
                )
                .startNow()
                .build();
    }

    /**
     * CalendarIntervalTrigger
     */
    private static Trigger createCalendarIntervalTrigger(
            TriggerBuilder<Trigger> builder,
            QuartzScheduleConfig schedule) {

        int interval = schedule.getInterval();

        if (interval <= 0) {
            throw new IllegalArgumentException("执行间隔必须大于 0");
        }

        IntervalUnit unit = toCalendarIntervalUnit(
                schedule.getIntervalUnit()
        );

        return builder
                .withSchedule(
                        CalendarIntervalScheduleBuilder
                                .calendarIntervalSchedule()
                                .withInterval(interval, unit).withMisfireHandlingInstructionFireAndProceed()
                )
                .startNow()
                .build();
    }

    /**
     * DailyTimeIntervalTrigger
     *
     * 默认每天 09:00 到 18:00 执行。
     */
    private static Trigger createDailyTimeIntervalTrigger(
            TriggerBuilder<Trigger> builder,
            QuartzScheduleConfig schedule) {

        int interval = schedule.getInterval();

        if (interval <= 0) {
            throw new IllegalArgumentException("执行间隔必须大于 0");
        }

        int intervalSeconds = Math.toIntExact(
                toMillis(interval, schedule.getIntervalUnit()) / 1000
        );

        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    "DailyTimeInterval 的间隔不能小于 1 秒"
            );
        }

        return builder
                .withSchedule(
                        DailyTimeIntervalScheduleBuilder
                                .dailyTimeIntervalSchedule()
                                .startingDailyAt(
                                        TimeOfDay.hourAndMinuteOfDay(9, 0)
                                )
                                .endingDailyAt(
                                        TimeOfDay.hourAndMinuteOfDay(18, 0)
                                )
                                .withIntervalInSeconds(intervalSeconds).withMisfireHandlingInstructionFireAndProceed()
                )
                .startNow()
                .build();
    }

    /**
     * FixedDelay
     *
     * 这里仅创建首次执行的 Trigger。
     * 下一次执行必须由 JobListener 在任务完成后重新创建。
     */
    private static Trigger createFixedDelayTrigger(
            TriggerBuilder<Trigger> builder,
            QuartzScheduleConfig schedule) {

        if (schedule.getInterval() <= 0) {
            throw new IllegalArgumentException("执行间隔必须大于 0");
        }

        return builder
                .startNow()
                .build();
    }

    /**
     * 获取 Cron 表达式
     */
    private static String getCronExpression(QuartzScheduleConfig schedule) {

        String value = schedule.getValue();
        return value;
    }

    /**
     * 转换为毫秒
     */
    public static long toMillis(long interval, IntervalUnit unit) {

        switch (unit) {
            case SECOND:
                return interval * 1000L;
            case MINUTE:
                return interval * 60_000L;
            case HOUR:
                return interval * 3_600_000L;
            case DAY:
                return interval * 86_400_000L;
            case WEEK:
                return interval * 604_800_000L;
            default:
                throw new IllegalArgumentException(
                        "该间隔单位不支持毫秒转换：" + unit
                );
        }
    }

    /**
     * 转换为 Quartz CalendarInterval 单位
     */
    private static IntervalUnit toCalendarIntervalUnit(
            IntervalUnit unit) {

        switch (unit) {
            case SECOND:
                return IntervalUnit.SECOND;
            case MINUTE:
                return IntervalUnit.MINUTE;
            case HOUR:
                return IntervalUnit.HOUR;
            case DAY:
                return IntervalUnit.DAY;
            case WEEK:
                return IntervalUnit.WEEK;
            case MONTH:
                return IntervalUnit.MONTH;
            case YEAR:
                return IntervalUnit.YEAR;
            default:
                throw new IllegalArgumentException(
                        "不支持的间隔单位：" + unit
                );
        }
    }
}