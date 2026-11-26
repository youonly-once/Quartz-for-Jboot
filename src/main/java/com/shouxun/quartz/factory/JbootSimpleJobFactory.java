/* 
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 * use this file except in compliance with the License. You may obtain a copy 
 * of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 * 
 */
package com.shouxun.quartz.factory;

import com.jfinal.aop.Aop;
import io.jboot.aop.annotation.Bean;
import lombok.extern.slf4j.Slf4j;
import org.quartz.simpl.PropertySettingJobFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

/**
 * The default JobFactory used by Quartz - simply calls 
 * <code>newInstance()</code> on the job class.
 * 
 * @see JobFactory
 * @see PropertySettingJobFactory
 * 
 * @author jhouse
 */

@Bean
@Slf4j
public class JbootSimpleJobFactory implements JobFactory {
    
    public Job newJob(TriggerFiredBundle bundle, Scheduler Scheduler) throws SchedulerException {

        JobDetail jobDetail = bundle.getJobDetail();
        Class<? extends Job> jobClass = jobDetail.getJobClass();
        try {
            if(log.isDebugEnabled()) {
                log.debug(
                    "Producing instance of Job '" + jobDetail.getKey() + 
                    "', class=" + jobClass.getName());
            }
            return Aop.get(jobClass);
        } catch (Exception e) {
            SchedulerException se = new SchedulerException(
                    "Problem instantiating class '"
                            + jobDetail.getJobClass().getName() + "'", e);
            throw se;
        }
    }

}
