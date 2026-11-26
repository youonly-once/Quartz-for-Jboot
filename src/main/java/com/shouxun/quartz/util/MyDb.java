package com.shouxun.quartz.util;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.DbPro;

public interface MyDb {
    DbPro pg = Db.use(DbType.POSTGRES);
    DbPro orcl = Db.use(DbType.ORACLE);
    DbPro road = Db.use(DbType.ORACLE_ROAD);

    interface DbType {
        String POSTGRES = "pg";
        String ORACLE = "ORCL";
        String ORACLE_ROAD = "ORCL_ROAD";
    }
}
