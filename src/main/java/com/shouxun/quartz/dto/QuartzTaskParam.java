package com.shouxun.quartz.dto;

import lombok.Data;
import org.quartz.DateBuilder;

import java.io.Serializable;

/**
 * 新增、修改任务参数。
 * jobType 为后端登记的任务类型编码；不要将前端输入直接作为 Java 类名反射加载。
 */
@Data
public class QuartzTaskParam implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobName;
    private String jobGroup;
    private String description;
    private String jobType;
    private String cronExpression;
    private String params;
    private String scheduleType;
    private Long interval;
    private String intervalUnit;
    /**
     * 异常通知：true 开启，false 关闭
     */
    private Boolean exceptionNotify;
    /**
     * 通知方式：SMS 短信，PHONE 电话
     */
    private String notifyType;
    /**
     * 通知号码
     */
    private String notifyPhone;
    /**
     * 连续异常次数达到该值时通知
     */
    private Integer exceptionCount;
    /**
     * 自定义通知内容，支持 {{变量}} 占位符；为空时使用 AI 汇总后的异常信息
     */
    private String notifyContent;

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public Boolean getExceptionNotify() { return exceptionNotify; }
    public void setExceptionNotify(Boolean exceptionNotify) { this.exceptionNotify = exceptionNotify; }
    public String getNotifyType() { return notifyType; }
    public void setNotifyType(String notifyType) { this.notifyType = notifyType; }
    public String getNotifyPhone() { return notifyPhone; }
    public void setNotifyPhone(String notifyPhone) { this.notifyPhone = notifyPhone; }
    public Integer getExceptionCount() { return exceptionCount; }
    public void setExceptionCount(Integer exceptionCount) { this.exceptionCount = exceptionCount; }
    public String getNotifyContent() { return notifyContent; }
    public void setNotifyContent(String notifyContent) { this.notifyContent = notifyContent; }
}
