package com.shouxun.quartz.service;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Page;
import com.jfinal.plugin.activerecord.Record;
import com.shouxun.quartz.dto.QuartzJobLog;
import io.jboot.aop.annotation.Bean;
import io.jboot.db.model.Columns;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;


public interface QuartzLogService {


    /**
     * 根据主键查找Model
     *
     * @param id
     * @return
     */
    public QuartzJobLog findById(Object id);


    /**
     * 根据 Columns 查找单条数据
     *
     * @param columns
     * @return
     */
    public QuartzJobLog findFirstByColumns(Columns columns);

    /**
     * 根据 Columns 查找单条数据
     *
     * @param columns
     * @param orderBy
     * @return
     */
    public QuartzJobLog findFirstByColumns(Columns columns, String orderBy);


    /**
     * 查找全部数据
     *
     * @return
     */
    public List<QuartzJobLog> findAll();


    /**
     * 根据 Columns 查找数据
     *
     * @param columns
     * @return
     */
    public List<QuartzJobLog> findListByColumns(Columns columns);


    /**
     * 根据 Columns 查找数据
     *
     * @param columns
     * @param orderBy
     * @return
     */
    public List<QuartzJobLog> findListByColumns(Columns columns, String orderBy);

    /**
     * 根据 Columns 查找数据
     *
     * @param columns
     * @param count
     * @return
     */
    public List<QuartzJobLog> findListByColumns(Columns columns, Integer count);

    /**
     * 根据 Columns 查找数据
     *
     * @param columns
     * @param orderBy
     * @param count
     * @return
     */
    public List<QuartzJobLog> findListByColumns(Columns columns, String orderBy, Integer count);


    /**
     * 根据提交查询数据量
     *
     * @param columns
     * @return
     */
    public long findCountByColumns(Columns columns);


    /**
     * 根据ID 删除model
     *
     * @param id
     * @return
     */
    public boolean deleteById(Object id);


    /**
     * 删除
     *
     * @param model
     * @return
     */
    public boolean delete(QuartzJobLog model);


    /**
     * 根据 多个 id 批量删除
     *
     * @param ids
     * @return
     */
    public boolean batchDeleteByIds(Object... ids);


    /**
     * 保存到数据库
     *
     * @param model
     * @return id if success
     */
    public Object save(QuartzJobLog model);


    /**
     * 保存或更新
     *
     * @param model
     * @return id if success
     */
    public Object saveOrUpdate(QuartzJobLog model);

    /**
     * 更新
     *
     * @param model
     * @return
     */
    public boolean update(QuartzJobLog model);


    /**
     * 分页
     *
     * @param page
     * @param pageSize
     * @return
     */
    public Page<QuartzJobLog> paginate(int page, int pageSize);


    /**
     * 分页
     *
     * @param page
     * @param pageSize
     * @return
     */
    public Page<QuartzJobLog> paginateByColumns(int page, int pageSize, Columns columns);


    /**
     * 分页
     *
     * @param page
     * @param pageSize
     * @param columns
     * @param orderBy
     * @return
     */
    public Page<QuartzJobLog> paginateByColumns(int page, int pageSize, Columns columns, String orderBy);

    String start(Record record);

    void finish(
            String id,
            String status,
            long duration,
            String resultMsg,
            String exceptionMsg);

    void vetoed(Record record);

    /**
     * 分批清理历史执行日志。
     * <p>
     * 清理规则：
     * 1. 已完成（SUCCESS/FAIL）按任务分组保留最近 keepCount 条，删除更早的记录；
     * 2. VETOED 保留 vetoedKeepDays 天，超过后删除；
     * 3. RUNNING 不清理。
     *
     * @param keepCount      每个任务保留的已完成日志条数
     * @param vetoedKeepDays VETOED 日志保留天数
     * @param batchSize      单批删除条数
     * @return 删除的总条数
     */
    long cleanHistory(int keepCount, int vetoedKeepDays, int batchSize);
}
