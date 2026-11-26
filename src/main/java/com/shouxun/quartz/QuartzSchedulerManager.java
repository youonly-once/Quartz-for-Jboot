package com.shouxun.quartz;

import cn.hutool.core.util.StrUtil;
import com.jfinal.aop.Inject;
import com.jfinal.plugin.activerecord.Config;
import com.jfinal.plugin.activerecord.DbKit;
import com.shouxun.quartz.annotation.QuartzSchedule;
import com.shouxun.quartz.config.QuartzConfig;
import com.shouxun.quartz.config.QuartzScheduleConfig;
import com.shouxun.quartz.datasource.JbootQuartzConnectionProvider;
import com.shouxun.quartz.factory.QuartzTriggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.hibernate.HikariConnectionProvider;
import io.jboot.Jboot;
import io.jboot.aop.annotation.Bean;
import io.jboot.db.datasource.DataSourceConfig;
import io.jboot.db.datasource.DataSourceConfigManager;
import io.jboot.utils.ClassScanner;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import com.shouxun.quartz.log.QuartzJobLogContext;
import org.quartz.impl.jdbcjobstore.oracle.OracleDelegate;
import org.quartz.impl.matchers.EverythingMatcher;
import org.quartz.spi.JobFactory;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.impl.StdSchedulerFactory.PROPERTIES_FILE;

/**
 * Quartz scheduler manager (singleton).
 * <p>
 * Provides full lifecycle management: init, start, shutdown, plus
 * Job CRUD operations (register, pause, resume, delete, reschedule,
 * trigger-now). Auto-scans classes annotated with
 * {@link QuartzSchedule} at startup.
 * <p>
 * Usage:
 * <pre>
 * // 1. Auto-scan (recommended)
 * public class MyJob implements Job { ... }
 *
 * // 2. Manual dynamic registration
 * QuartzSchedulerManager.me().registerJob(MyJob.class, "0 0 * * * ?", "myDynamicJob");
 *
 * // 3. Pause / resume
 * QuartzSchedulerManager.me().pauseJob("myJob", "DEFAULT_GROUP");
 * QuartzSchedulerManager.me().resumeJob("myJob", "DEFAULT_GROUP");
 *
 * // 4. Manual one-shot trigger
 * QuartzSchedulerManager.me().triggerJob("myJob", "DEFAULT_GROUP");
 * </pre>
 */
@Slf4j
@Bean
public class QuartzSchedulerManager {

    /**
     * -- GETTER --
     * Get the underlying Scheduler instance.
     */
    @Getter
    private Scheduler scheduler;

    @Inject
    private JobListener jobListener;

    @Inject
    private JobFactory jobFactory;

    private boolean initialized = false;
    public static final String CONFIG_KEY_ENABLE = "quartz.enable";
    /** JDBCJobStore 模式下的专用连接池，RAMJobStore 模式为 null */
    private HikariDataSource internalDataSource;

    /** Cached Job metadata, key = jobGroup + ":" + jobName */
    private final Map<String, JobMeta> jobMetaCache = new ConcurrentHashMap<>();
    private final Map<String, JobMeta> jobCache = new ConcurrentHashMap<>();


    // ==================== Lifecycle ====================

    /**
     * Initialize and start the Quartz Scheduler.
     * <p>
     * 根据 {@link QuartzConfig#isPersist()} 选择：
     * <ul>
     *     <li>false - RAMJobStore，全部任务放内存，重启丢失</li>
     *     <li>true - JDBCJobStore + OracleDelegate，任务持久化到 Oracle，重启保留</li>
     * </ul>
     */
    @PostConstruct
    public void init() {
        String enableStr = Jboot.configValue(CONFIG_KEY_ENABLE);
        boolean enable = "true".equalsIgnoreCase(enableStr);
        if (!enable) {
            log.info("Quartz module disabled (quartz.enable=false), skip init");
            return;
        }
        if (initialized) {
            log.warn("QuartzSchedulerManager already initialized, skip duplicate call");
            return;
        }
        try {
            QuartzConfig cfg = QuartzConfig.get();
            Properties props = buildSchedulerProperties(cfg);
            StdSchedulerFactory factory = new StdSchedulerFactory();
            factory.initialize(props);
            scheduler = factory.getScheduler();

            // 记录 Quartz 工作线程名前缀，供日志收集器识别任务线程
            String threadNamePrefix = props.getProperty(
                    "org.quartz.threadPool.threadNamePrefix",
                    scheduler.getSchedulerName());
            QuartzJobLogContext.setWorkerThreadPrefix(threadNamePrefix + "_Worker");

            scheduler.setJobFactory(jobFactory);
            // Register global Job listener
            scheduler.getListenerManager().addJobListener(jobListener,
                    EverythingMatcher.allJobs());

            scheduler.start();

            log.info("Quartz Scheduler started successfully, store={}",
                    props.get(StdSchedulerFactory.PROP_JOB_STORE_CLASS));

            // Auto-scan and register annotated Jobs
            scanAndRegisterAnnotatedJobs();

            initialized = true;
        } catch (SchedulerException e) {
            log.error("Failed to initialize Quartz Scheduler", e);
            shutdownInternalDataSource();
            throw new RuntimeException("Quartz Scheduler init failed", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 组装 StdSchedulerFactory 所需的 Properties。
     * persist=true 时使用 JDBCJobStore + OracleDelegate，从 Jboot
     * DataSourceConfigManager 读取 datasourceKey 对应的连接参数，
     * 填到 Quartz Properties 里由 Quartz 内部连接池管理。
     * persist=false 时使用默认 RAMJobStore。
     */
    private Properties buildSchedulerProperties(QuartzConfig cfg) throws SchedulerException, IOException {
        Properties props = initialize();
        return buildJdbcProperties(props, cfg);
    }
    public Properties initialize() throws SchedulerException, IOException {
        String requestedFile = System.getProperty(PROPERTIES_FILE);
        String filename = requestedFile != null ? requestedFile
                : "quartz.properties";
        InputStream is;
        Properties props = new Properties();

        is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);

        try {
            if(is != null) {
                is = new BufferedInputStream(is);
            } else {
                is = new BufferedInputStream(new FileInputStream(filename));
            }
            props.load(is);
        }
        finally {
            if(is != null)
                try { is.close(); } catch(IOException ignore) {}
        }
        return props;
    }
    /**
     * JDBCJobStore + OracleDelegate 配置。
     * 从 Jboot DataSourceConfigManager 取 datasourceKey（默认 ORCL），
     * 把 url/user/password/driverClassName 和连接池参数填进 Quartz Properties。
     */
    private Properties buildJdbcProperties(Properties props, QuartzConfig cfg) {

        // 2) 从 Jboot DataSourceConfigManager 取数据源参数
        String dsKey = cfg.getDatasourceKey();
        Map<String, DataSourceConfig> allDs =
                DataSourceConfigManager.me().getDatasourceConfigs();
        DataSourceConfig dsCfg = allDs.get(dsKey);
        if (dsCfg == null) {
            throw new IllegalStateException(
                    "Quartz persist enabled but datasource '" + dsKey
                            + "' not found in jboot.datasource.* config. "
                            + "Available keys: " + allDs.keySet());
        }
        if (!dsCfg.isOracleType()) {
            log.warn("Quartz OracleDelegate expects Oracle datasource, "
                    + "but datasource '{}' type is '{}'. "
                    + "If this is intentional you can ignore this warning.",
                    dsKey, dsCfg.getType());
        }

        // 3) 复用 Jboot 已为该数据源创建的连接池，Quartz 只借用和归还连接
        Config jfinalConfig = DbKit.getConfig(dsKey);
        if (jfinalConfig == null) {
            throw new IllegalStateException(
                    "Quartz persist enabled but JFinal datasource config '" + dsKey
                            + "' not found in DbKit. Check that the datasource has been initialized.");
        }
        DataSource dataSource = jfinalConfig.getDataSource();
        JbootQuartzConnectionProvider.setDataSource(dataSource);

        // 4) 让 JDBCJobStore 使用自定义 ConnectionProvider
        props.setProperty("org.quartz.dataSource.jbootQuartz.connectionProvider.class",
                JbootQuartzConnectionProvider.class.getName());
        props.setProperty("org.quartz.jobStore.dataSource", "jbootQuartz");

        log.info("Quartz JDBCJobStore will reuse Jboot datasource, key={}, url={}",
                dsKey, dsCfg.getUrl());

        return props;
    }
    private Properties buildManualJdbcProperties(Properties props, QuartzConfig cfg) {


        // 2) 从 Jboot DataSourceConfigManager 取数据源参数
        String dsKey = cfg.getDatasourceKey();
        Map<String, DataSourceConfig> allDs =
                DataSourceConfigManager.me().getDatasourceConfigs();
        DataSourceConfig dsCfg = allDs.get(dsKey);
        if (dsCfg == null) {
            throw new IllegalStateException(
                    "Quartz persist enabled but datasource '" + dsKey
                            + "' not found in jboot.datasource.* config. "
                            + "Available keys: " + allDs.keySet());
        }
        if (!dsCfg.isOracleType()) {
            log.warn("Quartz OracleDelegate expects Oracle datasource, "
                            + "but datasource '{}' type is '{}'. "
                            + "If this is intentional you can ignore this warning.",
                    dsKey, dsCfg.getType());
        }

        // 3) 用项目已有的 HikariCP 自建连接池
        //    Quartz 未指定 connectionProvider.class 时默认用 C3p0PoolingConnectionProvider，
        //    项目 classpath 中没有 c3p0，会抛 NoClassDefFoundError，
        //    因此这里显式指定自定义 Provider 并注入 HikariDataSource。
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("quartz-" + dsKey);
        hikariConfig.setJdbcUrl(dsCfg.getUrl());
        hikariConfig.setUsername(dsCfg.getUser());
        hikariConfig.setPassword(dsCfg.getPassword());
        hikariConfig.setDriverClassName(dsCfg.getDriverClassName());
        hikariConfig.setMaximumPoolSize(Math.max(cfg.getMaxPoolSize(), 3));
        hikariConfig.setMinimumIdle(cfg.getMinIdle());
        hikariConfig.setMaxLifetime(cfg.getMaxLifetime());
        hikariConfig.setConnectionTimeout(cfg.getConnectionTimeout());
        hikariConfig.setConnectionTestQuery("select 1 from dual");
        hikariConfig.setAutoCommit(false);

        internalDataSource = new HikariDataSource(hikariConfig);
        JbootQuartzConnectionProvider.setDataSource(internalDataSource);

        // 4) 让 JDBCJobStore 使用自定义 ConnectionProvider
        props.setProperty("org.quartz.dataSource.cmServerQuartz.connectionProvider.class",
                JbootQuartzConnectionProvider.class.getName());
        props.setProperty("org.quartz.jobStore.dataSource", "cmServerQuartz");

        log.info("Quartz JDBCJobStore Properties built from Jboot datasource key={}, url={}, maxPoolSize={}",
                dsKey, dsCfg.getUrl(), cfg.getMaxPoolSize());

        return props;
    }
    /**
     * Gracefully shut down the Scheduler, waiting for running Jobs to finish.
     */
    public synchronized void shutdown() {
        if (!initialized || scheduler == null) {
            return;
        }
        try {
            if (!scheduler.isShutdown()) {
                scheduler.shutdown(true);
                log.info("Quartz Scheduler gracefully shut down");
            }
        } catch (SchedulerException e) {
            log.error("Error shutting down Quartz Scheduler", e);
        } finally {
            initialized = false;
            shutdownInternalDataSource();
        }
    }

    /**
     * 关闭 Quartz 专用连接池。
     * 正常情况下由 HikariConnectionProvider.shutdown() 关闭，
     * 这里兜底处理初始化失败或重复关闭的场景。
     */
    private void shutdownInternalDataSource() {
        if (internalDataSource != null && !internalDataSource.isClosed()) {
            try {
                internalDataSource.close();
            } catch (Exception e) {
                log.error("Error closing Quartz internal DataSource", e);
            }
        }
        internalDataSource = null;
    }

    // ==================== Job Registration ====================

    /**
     * Auto-scan all classes implementing {@link Job} and annotated with
     * {@link QuartzSchedule}, then register them.
     */
    private void scanAndRegisterAnnotatedJobs() {
        List<Class<Job>> jobClasses = ClassScanner.scanSubClass(Job.class, true);
        int registeredCount = 0;
        for (Class<Job> jobClass : jobClasses) {
            QuartzSchedule annotation =
                    jobClass.getAnnotation(QuartzSchedule.class);
            if (annotation == null) {
                continue;
            }
            try {
                registerJob(jobClass,annotation);
                registeredCount++;
            } catch (Exception e) {
                log.error("Failed to register annotated Job: class={}, error={}",
                        jobClass.getName(), e.getMessage(), e);
            }
        }
        log.info("Quartz auto-scan complete, {} annotated Jobs registered", registeredCount);
    }

    public JobDetail buildJobDetail(String name, JobKey key, String description,JobDataMap jobDataMap) {

        return newJob(jobCache.get(name).getJobClass())
                .withIdentity(key)
                .withDescription(description)
                .storeDurably()
                .setJobData(jobDataMap)
                .requestRecovery(false)
                .build();
    }
    public JobDetail buildJobDetail(String name, JobKey key, String description) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("jobType", name);
        return buildJobDetail(name, key, description, jobDataMap);
    }

    public TriggerKey triggerKey(String jobName, String jobGroup) {
        return TriggerKey.triggerKey(jobName + "_trigger", jobGroup);
    }

    public JobKey jobKey(String name, String group) {
        if (!StrUtil.isNotBlank(name) || !StrUtil.isNotBlank(group)) throw new IllegalArgumentException("任务名称和分组不能为空");
        return JobKey.jobKey(name, group);
    }
    /**
     * Manually register a Job with full parameters.
     *
     * @param jobClass    Quartz Job implementation class
     * @return the created JobDetail
     */
    public void registerJob(Class<? extends Job> jobClass,QuartzSchedule annotation)
            throws SchedulerException {
        if (scheduler == null) {
            throw new IllegalStateException(
                    "Quartz Scheduler not initialized, call QuartzSchedulerManager.me().init() first");
        }
        String cronExpr = annotation.value();
        String jobName = annotation.jobName();
        String jobGroup = annotation.jobGroup();
        String description = annotation.description();
        // Cache metadata
        jobMetaCache.put(jobGroup + ":" + jobName,
                new JobMeta(jobClass, cronExpr, jobName, jobGroup, description));
        jobCache.put(jobName,
                new JobMeta(jobClass, cronExpr, jobName, jobGroup, description));
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);



        QuartzScheduleConfig config =
                new QuartzScheduleConfig(annotation);
        TriggerKey triggerKey = triggerKey(jobName, jobGroup);
        Trigger trigger = QuartzTriggerFactory.createTrigger(jobKey, triggerKey, config);
// 1. 判断 Job 是否存在
        if (scheduler.checkExists(jobKey)) {
            // 更新已有 Job
            //scheduler.addJob(jobDetail, true);
            // 2. 判断 Trigger 是否存在
            if (scheduler.checkExists(triggerKey)) {
                // Trigger 存在：更新 Trigger（例如修改 Cron）
                //不更新Trigger 网页为准
               //scheduler.rescheduleJob(triggerKey, trigger);

            } else {
                // Trigger 不存在：补充 Trigger
                if (annotation.enabled()) {
                    //不注册trigger，默认关闭情况下
                    scheduler.scheduleJob(trigger);
                }

            }

        } else {
            // Build JobDetail
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("config", config);
            JobDetail jobDetail = newJob(jobClass)
                    .withIdentity(jobKey)
                    .withDescription(description)
                    .storeDurably()
                    .setJobData(jobDataMap)
                    .requestRecovery(false)
                    .build();
            // 3. Job 不存在：同时注册 Job 和 Trigger
            if (!annotation.enabled()){
                //不注册trigger，默认关闭情况下
                scheduler.addJob(jobDetail, true);
            }else{
                scheduler.scheduleJob(jobDetail, trigger);
            }
        }



        log.info("Quartz Job registered: {}:{}, cron={}", jobGroup, jobName, cronExpr);

    }

    // ==================== Job Operations ====================

    /** Pause a Job (default group DEFAULT_GROUP). */
    public void pauseJob(String jobName) throws SchedulerException {
        pauseJob(jobName, "DEFAULT_GROUP");
    }

    /** Pause a Job. */
    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.pauseJob(JobKey.jobKey(jobName, jobGroup));
        log.info("Quartz Job paused: {}:{}", jobGroup, jobName);
    }

    /** Resume a Job (default group DEFAULT_GROUP). */
    public void resumeJob(String jobName) throws SchedulerException {
        resumeJob(jobName, "DEFAULT_GROUP");
    }

    /** Resume a Job. */
    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.resumeJob(JobKey.jobKey(jobName, jobGroup));
        log.info("Quartz Job resumed: {}:{}", jobGroup, jobName);
    }

    /** Delete a Job (default group DEFAULT_GROUP). */
    public boolean deleteJob(String jobName) throws SchedulerException {
        return deleteJob(jobName, "DEFAULT_GROUP");
    }

    /** Delete a Job from Scheduler. */
    public boolean deleteJob(String jobName, String jobGroup) throws SchedulerException {
        boolean deleted = scheduler.deleteJob(JobKey.jobKey(jobName, jobGroup));
        if (deleted) {
            jobMetaCache.remove(jobGroup + ":" + jobName);
            log.info("Quartz Job deleted: {}:{}", jobGroup, jobName);
        }
        return deleted;
    }

    /** Manually trigger a Job once (default group DEFAULT_GROUP). */
    public void triggerJob(String jobName) throws SchedulerException {
        triggerJob(jobName, "DEFAULT_GROUP");
    }

    /** Manually trigger a Job one-shot execution. */
    public void triggerJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.triggerJob(JobKey.jobKey(jobName, jobGroup));
        log.info("Quartz Job manually triggered: {}:{}", jobGroup, jobName);
    }

    /** Update the cron expression of an existing Job. */
    public void rescheduleJob(String jobName, String jobGroup, String newCronExpr) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey("trigger_" + jobName, jobGroup);
        CronTrigger oldTrigger = (CronTrigger) scheduler.getTrigger(triggerKey);
        if (oldTrigger == null) {
            throw new SchedulerException("Trigger not found: " + triggerKey);
        }
        CronScheduleBuilder newSchedule = cronSchedule(newCronExpr)
                .withMisfireHandlingInstructionFireAndProceed();
        Trigger newTrigger = oldTrigger.getTriggerBuilder()
                .withSchedule(newSchedule)
                .build();
        scheduler.rescheduleJob(triggerKey, newTrigger);

        JobMeta meta = jobMetaCache.get(jobGroup + ":" + jobName);
        if (meta != null) {
            meta.setCronExpr(newCronExpr);
        }

        log.info("Quartz Job cron updated: {}:{}, newCron={}", jobGroup, jobName, newCronExpr);
    }

    // ==================== Query ====================

    /** Check whether a Job is registered. */
    public boolean jobExists(String jobName, String jobGroup) throws SchedulerException {
        return scheduler.checkExists(JobKey.jobKey(jobName, jobGroup));
    }

    /** Whether the Scheduler is up and running. */
    public boolean isStarted() {
        if (scheduler == null || !initialized) {
            return false;
        }
        try {
            return !scheduler.isShutdown();
        } catch (SchedulerException e) {
            return false;
        }
    }

    // ==================== Inner Class ====================

    /** Cached Job metadata. */
    @Getter
    public static class JobMeta {
        private final Class<? extends Job> jobClass;
        @Setter
        private String cronExpr;
        private final String jobName;
        private final String jobGroup;
        private final String description;

        public JobMeta(Class<? extends Job> jobClass, String cronExpr,
                       String jobName, String jobGroup, String description) {
            this.jobClass = jobClass;
            this.cronExpr = cronExpr;
            this.jobName = jobName;
            this.jobGroup = jobGroup;
            this.description = description;
        }
    }
    public boolean isJobPaused( TriggerKey triggerKey)
            throws SchedulerException {

        return scheduler.getTriggerState(triggerKey)
                == Trigger.TriggerState.PAUSED;
    }
}
