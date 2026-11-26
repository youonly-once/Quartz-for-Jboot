package com.shouxun.quartz.dto;

import io.jboot.db.annotation.Table;

@Table(tableName = "QUARTZ_JOB_LOG", primaryKey = "ID")
public class QuartzJobLog extends BaseQuartzJobLog<QuartzJobLog> {
	
}