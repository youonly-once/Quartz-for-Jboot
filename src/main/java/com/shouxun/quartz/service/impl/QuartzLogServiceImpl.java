package com.shouxun.quartz.service.impl;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.shouxun.quartz.dto.QuartzJobLog;
import com.shouxun.quartz.service.QuartzLogService;
import io.jboot.aop.annotation.Bean;
import io.jboot.service.JbootServiceBase;

import java.sql.Timestamp;
import java.util.UUID;

@Bean
public class QuartzLogServiceImpl extends JbootServiceBase<QuartzJobLog> implements QuartzLogService {
    static final String TABLE = "QUARTZ_JOB_LOG";

    /**
     * 创建任务执行记录。
     */

    @Override
    public String start(com.jfinal.plugin.activerecord.Record record) {
        String id = UUID.randomUUID()
                .toString()
                .replace("-", "");

        record.set("ID", id);
        record.set("EXECUTE_STATUS", "RUNNING");
        record.set("CREATE_TIME", new Timestamp(System.currentTimeMillis()));

        boolean success = Db.use("ORCL").save(TABLE, "ID", record);

        if (!success) {
            throw new RuntimeException("保存 Quartz 执行日志失败");
        }

        return id;
    }

    /**
     * 更新任务执行结果。
     */
    @Override
    public void finish(
            String id,
            String status,
            long duration,
            String resultMsg,
            String exceptionMsg) {

        com.jfinal.plugin.activerecord.Record record = new com.jfinal.plugin.activerecord.Record();
        record.set("ID", id);
        record.set("END_TIME", new Timestamp(System.currentTimeMillis()));
        record.set("DURATION", duration);
        record.set("EXECUTE_STATUS", status);
        record.set("RESULT_MSG", resultMsg);
        record.set("EXCEPTION_MSG", exceptionMsg);

        boolean success = Db.use("ORCL").update(TABLE, "ID", record);

        if (!success) {
            throw new RuntimeException("更新 Quartz 执行日志失败，ID：" + id);
        }
    }

    /**
     * 记录任务被否决。
     */
    @Override
    public void vetoed(Record record) {
        String id = UUID.randomUUID()
                .toString()
                .replace("-", "");

        record.set("ID", id);
        record.set("EXECUTE_STATUS", "VETOED");
        record.set("CREATE_TIME", new Timestamp(System.currentTimeMillis()));

        Db.use("ORCL").save(TABLE, "ID", record);
    }

    /**
     * 分批清理历史执行日志。
     * <p>
     * 已完成（SUCCESS/FAIL）按任务分组保留最近 keepCount 条，VETOED 按天数保留，
     * RUNNING 不清理。每批删除 batchSize 条，避免单条大 SQL 产生大事务。
     */
    @Override
    public long cleanHistory(int keepCount, int vetoedKeepDays, int batchSize) {
        if (keepCount <= 0) {
            throw new IllegalArgumentException("日志保留条数必须大于 0");
        }
        if (vetoedKeepDays < 0) {
            throw new IllegalArgumentException("VETOED 日志保留天数不能小于 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("批量删除条数必须大于 0");
        }

        // 已完成日志：按任务分组保留最近 keepCount 条
        String completedSql = "DELETE FROM " + TABLE + " WHERE ID IN ("
                + "SELECT ID FROM ("
                + "SELECT ID FROM ("
                + "SELECT ID, ROW_NUMBER() OVER (PARTITION BY JOB_NAME, JOB_GROUP "
                + "ORDER BY FIRE_TIME DESC NULLS LAST) rn "
                + "FROM " + TABLE + " WHERE EXECUTE_STATUS IN ('SUCCESS', 'FAIL')"
                + ") WHERE rn > ?"
                + ") WHERE ROWNUM <= ?"
                + ")";

        // 被否决日志：未真正执行，按天数保留
        String vetoedSql = "DELETE FROM " + TABLE
                + " WHERE ROWNUM <= ?"
                + " AND EXECUTE_STATUS = 'VETOED'"
                + " AND FIRE_TIME < SYSDATE - ?";

        long total = 0;
        total += deleteInBatches(completedSql,batchSize, keepCount , batchSize);
        total += deleteInBatches(vetoedSql, batchSize, batchSize, vetoedKeepDays);
        return total;
    }

    /**
     * 循环分批删除，直到某一批删除条数小于 batchSize。
     *
     * @param sql       删除 SQL，第一个参数为业务参数，最后一个参数为批次上限
     * @param batchSize 批次上限
     * @param params    业务参数
     * @return 删除的总条数
     */
    private long deleteInBatches(String sql, int batchSize, Object... params) {
        long total = 0;
        int deleted;
        do {
            deleted = Db.use("ORCL").update(sql, params);
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}