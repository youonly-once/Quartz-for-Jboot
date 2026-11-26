package com.shouxun.quartz.config;

import io.jboot.Jboot;
import lombok.Data;
import lombok.ToString;

/**
 * Quartz 模块配置模型，对应 jboot.properties 里所有以 quartz.* 开头的配置。
 * <p>
 * 配置示例（持久化到 Oracle）：
 * <pre>
 * quartz.enable=true
 * quartz.persist=true
 * quartz.datasource.key=ORCL
 * quartz.table.prefix=QRTZ_
 * quartz.scheduler.name=CmServerScheduler
 * quartz.jobStore.isClustered=false
 * </pre>
 * 配置示例（仅内存，不持久化）：
 * <pre>
 * quartz.enable=true
 * quartz.persist=false
 * </pre>
 */
@Data
@ToString
public class QuartzConfig {

    /** 总开关，默认 false */
    private boolean enable = false;

    /**
     * 复用 Jboot DataSourceConfig 的 key，默认 ORCL。
     * 仅当 persist=true 时生效。
     * 对应 jboot.datasource.{dsKey}.url / user / password / driverClassName。
     */
    private String datasourceKey = "ORCL";


    /** DataSource 内部连接池最大连接数 */
    private int maxPoolSize = 5;

    /** DataSource 内部连接池最小空闲连接数 */
    private int minIdle = 1;

    /** DataSource 连接最大存活时间（毫秒） */
    private long maxLifetime = 1800000L;

    /** 连接等待超时（毫秒） */
    private long connectionTimeout = 30000L;

    /** 获取当前配置（从 Jboot 配置中心绑定） */
    public static QuartzConfig get() {
        return Jboot.config(QuartzConfig.class, "quartz");
    }
}
