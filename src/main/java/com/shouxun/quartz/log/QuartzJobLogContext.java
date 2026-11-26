package com.shouxun.quartz.log;

import java.util.ArrayList;
import java.util.List;

/**
 * Quartz 任务执行线程内的错误日志收集器。
 * <p>
 * 由 {@link com.shouxun.quartz.listener.QuartzJobListener} 在任务开始/结束时开启和释放，
 * 由 {@link QuartzJobErrorAppender} 收集任务线程产生的 ERROR 日志，
 * 任务结束时统一写入 QUARTZ_JOB_LOG 的 EXCEPTION_MSG 并置为 FAIL。
 */
public final class QuartzJobLogContext {

    /**
     * 单次执行最多收集的错误条数
     */
    private static final int MAX_ERROR_COUNT = 50;

    /**
     * 单条错误日志最大长度，防止堆栈过长
     */
    private static final int MAX_ERROR_LENGTH = 1000;

    /**
     * 单次执行写入数据库的错误日志总长度上限
     */
    private static final int MAX_TOTAL_LENGTH = 10000;

    private static final ThreadLocal<List<String>> HOLDER = new ThreadLocal<>();

    /**
     * Quartz 调度线程名前缀，格式为 "实例名_Worker"（或自定义 threadNamePrefix + "_Worker"）
     */
    private static volatile String workerThreadPrefix;

    private QuartzJobLogContext() {
    }

    /**
     * 调度器初始化时写入线程名前缀
     */
    public static void setWorkerThreadPrefix(String prefix) {
        workerThreadPrefix = prefix;
    }

    /**
     * 当前线程是否为 Quartz 调度器的工作线程
     */
    public static boolean isQuartzWorkerThread() {
        String prefix = workerThreadPrefix;
        return prefix != null && Thread.currentThread().getName().startsWith(prefix);
    }

    /**
     * 任务开始执行时开启收集
     */
    public static void begin() {
        HOLDER.set(new ArrayList<>());
    }

    /**
     * 当前线程是否处于任务执行收集期间
     */
    public static boolean isActive() {
        return HOLDER.get() != null;
    }

    /**
     * 收集一条错误日志（非任务执行线程调用时忽略）
     */
    public static void collect(String error) {
        List<String> errors = HOLDER.get();
        if (errors == null || error == null) {
            return;
        }
        if (errors.size() >= MAX_ERROR_COUNT) {
            return;
        }
        errors.add(error.length() > MAX_ERROR_LENGTH
                ? error.substring(0, MAX_ERROR_LENGTH)
                : error);
    }

    /**
     * 结束收集并返回已收集的错误日志，无错误时返回 null
     */
    public static String end() {
        List<String> errors = HOLDER.get();
        HOLDER.remove();
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (String error : errors) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            if (text.length() >= MAX_TOTAL_LENGTH) {
                break;
            }
            text.append(error);
        }
        return text.toString();
    }
}
