package com.shouxun.quartz.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 收集 Quartz 任务执行线程内产生的 ERROR 日志，供 QuartzJobListener 写入 QUARTZ_JOB_LOG。
 * <p>
 * 由 logback 直接反射创建，不能使用 @Inject/@Bean（依赖注入不会生效）。
 * 线程名前缀在 QuartzSchedulerManager 初始化时通过
 * {@link QuartzJobLogContext#setWorkerThreadPrefix(String)} 写入。
 * <p>
 * 需要在 logback 的 root 下直接注册（不要包在 AsyncAppender 里，异步线程会丢失任务线程上下文）。
 */
public class QuartzJobErrorAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    protected void append(ILoggingEvent event) {
        if (!QuartzJobLogContext.isQuartzWorkerThread()
                || !QuartzJobLogContext.isActive() ||
                !event.getLevel().isGreaterOrEqual(Level.INFO)) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(TIME_FORMATTER.format(
                        Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault())))
                .append(" - ")
                .append(event.getFormattedMessage());

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            message.append(System.lineSeparator())
                    .append(ThrowableProxyUtil.asString(throwable));
        }

        QuartzJobLogContext.collect(message.toString());
    }
}
