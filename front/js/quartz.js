import request from '@/utils/request'

export function listQuartz(query) {
  return request({ url: '/quartz/list', method: 'get', params: query })
}

export function listAllJob(query) {
  return request({ url: '/quartz/getAllJobList', method: 'get', params: query })
}

export function getQuartz(jobName, jobGroup) {
  return request({ url: '/quartz/info', method: 'get', params: { jobName, jobGroup } })
}

export function addQuartz(data) {
  return request({ url: '/quartz/save', method: 'post', data })
}

export function updateQuartz(data) {
  return request({ url: '/quartz/update', method: 'post', data })
}

export function pauseQuartz(jobName, jobGroup) {
  return request({ url: '/quartz/pause', method: 'post', data: { jobName, jobGroup } })
}

export function resumeQuartz(jobName, jobGroup) {
  return request({ url: '/quartz/resume', method: 'post', data: { jobName, jobGroup } })
}

export function triggerQuartz(jobName, jobGroup) {
  return request({ url: '/quartz/trigger', method: 'post', data: { jobName, jobGroup } })
}

export function removeQuartz(jobName, jobGroup) {
  return request({ url: '/quartz/remove', method: 'post', data: { jobName, jobGroup } })
}
// 查询任务执行日志
export function listQuartzLog(query) {
  return request({
    url: '/quartz/pageLogNew',
    method: 'post',
    data: query
  })
}


