package com.shouxun.quartz.datasource;

import org.quartz.utils.ConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Quartz ConnectionProvider 适配器，复用 Jboot 已创建好的连接池。
 * <p>
 * Quartz 在未指定 connectionProvider.class 时默认使用
 * C3p0PoolingConnectionProvider，而本项目 classpath 中没有 c3p0，
 * 会抛 NoClassDefFoundError: com/mchange/v2/c3p0/ComboPooledDataSource。
 * <p>
 * 通过静态注入 DataSource，Quartz 只负责取连接和释放连接，
 * 连接池生命周期仍由 Jboot 统一管理，无需引入 c3p0 依赖。
 */
public class JbootQuartzConnectionProvider implements ConnectionProvider {

    private static final AtomicReference<DataSource> DATA_SOURCE = new AtomicReference<>();

    /**
     * 由 QuartzSchedulerManager 在初始化 Scheduler 之前注入连接池。
     */
    public static void setDataSource(DataSource dataSource) {
        DATA_SOURCE.set(dataSource);
    }

    @Override
    public void initialize() throws SQLException {
        if (DATA_SOURCE.get() == null) {
            throw new SQLException("HikariConnectionProvider 未注入 DataSource");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataSource dataSource = DATA_SOURCE.get();
        if (dataSource == null) {
            throw new SQLException("HikariConnectionProvider 未注入 DataSource");
        }
        return dataSource.getConnection();
    }

    @Override
    public void shutdown() throws SQLException {
        // 共享连接池由 Jboot 管理，Quartz 停止时只清除引用，不能关闭连接池
        DATA_SOURCE.set(null);
    }
}
