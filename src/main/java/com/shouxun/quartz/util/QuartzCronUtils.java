package com.shouxun.quartz.util;

import com.jfinal.kit.StrKit;
import it.burning.cron.CronExpressionDescriptor;
import it.burning.cron.CronExpressionParser;

public class QuartzCronUtils {

    /**
     * Cron表达式转换为中文描述
     */
    public static String toDescription(String cron) {
        if (StrKit.isBlank(cron)) {
            return "";
        }

        try {
            CronExpressionParser.Options options =
                    new CronExpressionParser.Options();

            options.setLocale("zh_CN");
            options.setUse24HourTimeFormat(true);

            return CronExpressionDescriptor.getDescription(
                    cron, options
            );
        } catch (Exception e) {
            return "Cron表达式无法解析";
        }
    }
}
