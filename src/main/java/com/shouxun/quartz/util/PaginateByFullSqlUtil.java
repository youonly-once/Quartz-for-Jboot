package com.shouxun.quartz.util;

import com.jfinal.plugin.activerecord.PageSqlKit;

public class PaginateByFullSqlUtil {
    public static String getTotalRowSql(String sqlString) {
        String[] sqls = PageSqlKit.parsePageSql(sqlString);
        String totalRowSql = "select count(*) " + sqls[1];
        int lastIndex = totalRowSql.lastIndexOf("ORDER BY");
        if (lastIndex != -1) {
            totalRowSql = totalRowSql.substring(0,lastIndex);
        }
        return totalRowSql;
    }
}
