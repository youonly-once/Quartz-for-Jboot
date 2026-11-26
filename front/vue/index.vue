<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="任务名称" prop="jobName">
        <el-input v-model="queryParams.jobName" placeholder="请输入任务名称" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="任务分组" prop="jobGroup">
        <el-input v-model="queryParams.jobGroup" placeholder="请输入任务分组" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="计划状态" prop="triggerState">
        <el-select
          v-model="queryParams.triggerState"
          placeholder="计划状态"
          clearable
        >
          <el-option label="正常等待" value="WAITING" />
          <el-option label="已获取" value="ACQUIRED" />
          <el-option label="执行中" value="EXECUTING" />
          <el-option label="执行阻塞" value="BLOCKED" />
          <el-option label="已暂停" value="PAUSED" />
          <el-option label="暂停且阻塞" value="PAUSED_BLOCKED" />
          <el-option label="已完成" value="COMPLETE" />
          <el-option label="执行异常" value="ERROR" />
          <el-option label="无触发器" value="NONE" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行结果" prop="state">
        <el-select
          v-model="queryParams.executeStatus"
          placeholder="全部状态"
          clearable
        >
          <el-option
            v-for="item in executeStatusDict"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="耗时下限" prop="minDuration">
        <el-input v-model="queryParams.minDuration" placeholder="请输入耗时下限(毫秒)" clearable @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增任务</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="taskList" >
      <el-table-column label="任务名称" prop="jobName" min-width="100" show-overflow-tooltip />
      <el-table-column label="任务分组" prop="jobGroup" min-width="100" />
      <el-table-column label="计划类型" prop="scheduleTypeText" min-width="100" />
      <el-table-column label="计划描述" prop="cronDescription" min-width="100">
        <template slot-scope="scope">
          {{ scope.row.cronDescription }}{{ scope.row.cronExpression ? ` (${scope.row.cronExpression})` : '' }}
        </template>
      </el-table-column>
      <el-table-column label="计划状态" prop="triggerState" min-width="100" align="center">
        <template slot-scope="scope">
          <el-tag :type="stateTag(scope.row.triggerState)">{{ scope.row.triggerStateDesc }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="下次执行时间" prop="nextFireTime" min-width="100" />
      <el-table-column label="上次执行时间" prop="lastFireTime" min-width="100" />
      <el-table-column label="上次结果" prop="executeStatus" min-width="100" >
        <template slot-scope="scope">

          <el-tag v-if="scope.row.executeStatus" :type="stateTag(scope.row.executeStatus)">{{ scope.row.executeStatus }}</el-tag>
        </template>
     </el-table-column>
      <el-table-column label="上次耗时(ms)" prop="lastDuration" min-width="100" />
      <el-table-column label="操作" width="300" fixed="right" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button v-if="scope.row.triggerState !== 'PAUSED' && scope.row.triggerState!=='NONE'" size="mini" type="text" icon="el-icon-video-pause" @click="handlePause(scope.row)">暂停</el-button>
          <el-button v-if="scope.row.triggerState === 'PAUSED' && scope.row.triggerState!=='NONE'" v-else size="mini" type="text" icon="el-icon-video-play" @click="handleResume(scope.row)">恢复</el-button>
          <el-button size="mini" type="text" icon="el-icon-refresh" @click="handleTrigger(scope.row)">执行</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-document"
            @click="handleLog(scope.row)"
          >日志</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 任务执行日志 -->
    <el-dialog
      :title="'任务执行日志：' + form.jobName + ' - ' + form.jobGroup"
      :visible.sync="logOpen"
      width="1100px"
      append-to-body
    >
      <div class="mb8">
        <el-tag type="info">任务名称：{{ currentJob.jobName }}</el-tag>
        <el-tag type="info" class="ml10">任务分组：{{ currentJob.jobGroup }}</el-tag>
      </div>
      <el-form class="searchForm" :model="form">
        <el-row :gutter="20">

          <el-col :span="4">
            <el-form-item>
              <el-input
                placeholder="执行结果"
                v-model="form.execResult"
              ></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item>
              <el-select
                v-model="form.execStatus"
                :popper-append-to-body="false"
                placeholder="执行状态"
                clearable
              >
                <el-option
                  v-for="item in executeStatusDict"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item>
              <el-input
                placeholder="耗时下限(秒)"
                v-model="form.duration"
              ></el-input>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item>
              <el-date-picker
                v-model="form.fireTimeArr"
                type="datetimerange"
                value-format="yyyy-MM-dd HH:mm:ss"
                range-separator="至"
                start-placeholder="触发时间"
                end-placeholder="触发时间"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="30">
          <el-col :span="9">
            <el-form-item>
              <el-date-picker
                v-model="form.startTimeArr"
                type="datetimerange"
                value-format="yyyy-MM-dd HH:mm:ss"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="开始时间"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item>
              <el-date-picker
                v-model="form.endTimeArr"
                type="datetimerange"
                value-format="yyyy-MM-dd HH:mm:ss"
                range-separator="至"
                start-placeholder="结束时间"
                end-placeholder="结束时间"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>


          <el-col :span="10">
            <el-form-item>
              <el-button
                type="primary"
                class="el-icon-search"
                @click="searchLog"
              >
                搜索</el-button
              >
              <el-button class="el-icon-refresh" @click="resetLogAction">
                重置</el-button
              >
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-table v-loading="logLoading" :data="logList" border>
        <el-table-column label="触发实例ID" prop="fireInstanceId" min-width="150" show-overflow-tooltip />
        <el-table-column label="触发器名称" prop="triggerName" min-width="120" show-overflow-tooltip />
        <el-table-column label="执行状态" prop="executeStatus" width="100" align="center">
          <template slot-scope="scope">
            <el-tag :type="logStatusTag(scope.row.executeStatus)">
              {{ scope.row.executeStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发时间" prop="fireTime" min-width="155" />
        <el-table-column label="开始时间" prop="startTime" min-width="155" />
        <el-table-column label="结束时间" prop="endTime" min-width="155" />
        <el-table-column label="耗时(ms)" prop="DURATION" width="100" align="right" />
        <el-table-column label="执行结果" prop="resultMsg" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template slot-scope="scope">
            <el-button
              type="text"
              size="mini"
              @click="handleLogDetail(scope.row)"
            >详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="logTotal > 0"
        :total="logTotal"
        :page.sync="form.page"
        :limit.sync="form.limit"
        @pagination="getLogList"
      />

      <div slot="footer" class="dialog-footer">
        <el-button @click="logOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- 执行日志详情 -->
    <el-dialog
      title="执行日志详情"
      :visible.sync="logDetailOpen"
      width="750px"
      append-to-body
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务名称">
          {{ logDetail.jobName }}
        </el-descriptions-item>
        <el-descriptions-item label="任务分组">
          {{ logDetail.jobGroup }}
        </el-descriptions-item>
        <el-descriptions-item label="触发器名称">
          {{ logDetail.triggerName }}
        </el-descriptions-item>
        <el-descriptions-item label="触发器分组">
          {{ logDetail.triggerGroup }}
        </el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="logStatusTag(logDetail.executeStatus)">
            {{ logDetail.executeStatus }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行耗时">
          {{ logDetail.DURATION }} ms
        </el-descriptions-item>
        <el-descriptions-item label="触发时间">
          {{ logDetail.fireTime }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ logDetail.startTime }}
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          {{ logDetail.endTime }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ logDetail.createTime }}
        </el-descriptions-item>
        <el-descriptions-item label="执行结果" :span="2">
          {{ logDetail.resultMsg }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">异常信息</el-divider>
      <el-input
        type="textarea"
        :rows="10"
        readonly
        resize="none"
        :value="logDetail.exceptionMsg || '无异常信息'"
      />

      <div slot="footer" class="dialog-footer">
        <el-button @click="logDetailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.page"
      :limit.sync="queryParams.limit" @pagination="getList" />

    <el-dialog
      :title="title"
      :visible.sync="open"
      width="620px"
      append-to-body
    >
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        label-width="105px"
      >
        <el-row>
          <el-col :span="12">
            <el-form-item label="任务名称" prop="jobName">
                <el-select
                  v-model="form.jobName"
                  placeholder="请选择或输入搜索"
                  filterable
                  clearable
                  :disabled="formMode === 'edit'"
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in this.allList"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="任务分组" prop="jobGroup">
              <el-input
                v-model="form.jobGroup"
                placeholder="请输入任务分组"
                :disabled="formMode === 'edit'"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 调度类型 -->
        <el-form-item label="调度类型" prop="scheduleType">
          <el-select
            v-model="form.scheduleType"
            placeholder="请选择调度类型"
            min-width="100"
            @change="handleScheduleTypeChange"
          >
            <el-option label="Cron表达式" value="CRON" />
            <el-option label="固定间隔" value="SIMPLE" />
            <el-option label="日历间隔" value="CALENDAR_INTERVAL" />
            <el-option label="时间范围间隔" value="DAILY_TIME_INTERVAL" />
            <el-option label="固定延迟" value="FIXED_DELAY" />
          </el-select>
        </el-form-item>

        <!-- CRON 配置 -->
        <el-form-item
          v-if="form.scheduleType === 'CRON'"
          label="Cron表达式"
          prop="cronExpression"
        >
          <el-input
            v-model="form.cronExpression"
            placeholder="例如：0 0/5 * * * ?"
          />
        </el-form-item>

        <!-- FIXED_DELAY 配置 -->
        <template v-if="form.scheduleType === 'FIXED_DELAY'">
          <el-row>
            <el-col :span="12">
          <el-form-item label="间隔时间" prop="interval">
            <el-input-number
              v-model="form.interval"
              :min="1"
              :precision="0"
              controls-position="left"
              min-width="50"
              placeholder="请输入间隔时间"
            />
          </el-form-item>
            </el-col>
              <el-col :span="12">
          <el-form-item label="时间单位" prop="intervalUnit">
            <el-select
              v-model="form.intervalUnit"
              placeholder="请选择时间单位"
              min-width="50"
            >
              <el-option label="毫秒" value="MILLISECOND" />
              <el-option label="秒" value="SECOND" />
              <el-option label="分钟" value="MINUTE" />
              <el-option label="小时" value="HOUR" />
              <el-option label="天" value="DAY" />
              <el-option label="周" value="WEEK" />
              <el-option label="月" value="MONTH" />
              <el-option label="年" value="YEAR" />
            </el-select>
          </el-form-item>
              </el-col>
          </el-row>
        </template>

        <el-form-item label="任务描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>

        <el-form-item label="任务参数" prop="params">
          <el-input
            v-model="form.params"
            type="textarea"
            :rows="4"
            placeholder="JSON字符串，由任务处理器自行解析"
          />
        </el-form-item>

        <el-form-item label="异常通知" prop="exceptionNotify">
          <el-radio-group v-model="form.exceptionNotify">
            <el-radio :label="true">是</el-radio>
            <el-radio :label="false">否</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.exceptionNotify">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="通知方式" prop="notifyType">
                <el-radio-group v-model="form.notifyType">
                  <el-radio label="SMS">短信</el-radio>
                  <el-radio label="PHONE">电话</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="通知号码" prop="notifyPhone">
                <el-input
                  v-model="form.notifyPhone"
                  placeholder="请输入接收通知的号码"
                  maxlength="20"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item prop="exceptionCount">
            <span slot="label">
              异常次数
              <el-tooltip placement="top" content="连续异常次数达到该值时发送通知">
                <i class="el-icon-question"></i>
              </el-tooltip>
            </span>
            <el-input-number
              v-model="form.exceptionCount"
              :min="1"
              :precision="0"
              controls-position="right"
              style="width: 200px"
            />
          </el-form-item>

          <el-form-item prop="notifyContent">
            <span slot="label">
              通知内容
              <el-tooltip placement="top" :content="'支持变量：{{jobName}} 任务名称、{{jobGroup}} 任务分组、{{failCount}} 连续异常次数、{{exceptionMsg}} 异常信息、{{time}} 通知时间。不填则自动使用 AI 汇总后的异常信息'">
                <i class="el-icon-question"></i>
              </el-tooltip>
            </span>
            <el-input
              v-model="form.notifyContent"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              :placeholder="'如：定时任务{{jobName}}已连续异常{{failCount}}次，异常原因：{{exceptionMsg}}，请及时处理。不填则使用AI汇总后的异常信息'"
            />
          </el-form-item>
        </template>
      </el-form>

      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listQuartz, getQuartz, addQuartz, updateQuartz,
  pauseQuartz, resumeQuartz, triggerQuartz, removeQuartz,
  listQuartzLog,listAllJob
} from '@/api/quartz/quartz'
import fa from "element-ui/src/locale/lang/fa"

export default {
  name: 'Quartz',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      taskList: [],
      allList: [],
      open: false,
      submitLoading: false,
      // 提交时的全屏加载层实例
      submitLoadingInstance: null,
      // 行操作（删除/暂停/恢复/执行）的全屏加载层实例
      actionLoadingInstance: null,
      title: '',
      formMode: 'add',
      queryParams: { page: 1, limit: 10, jobName: undefined, jobGroup: undefined, state: undefined },
      form: {
        execResult:"",
        endTimeArr:[],
        startTimeArr:[],
        fireTimeArr:[],
        duration:0,
        jobName: "",
        jobGroup: "",
        page: 1,
        limit: 10,
      },
      executeStatusDict : [
        { label: "成功", value: "SUCCESS" },
        { label: "失败", value: "FAIL" },
        { label: "执行中", value: "RUNNING" },
      ],
      rules: {
        jobName: [
          { required: true, message: '请输入任务名称', trigger: 'blur' }
        ],
        jobGroup: [
          { required: true, message: '请输入任务分组', trigger: 'blur' }
        ],
        scheduleType: [
          { required: true, message: '请选择调度类型', trigger: 'change' }
        ],
        cronExpression: [
          {
            validator: (rule, value, callback) => {
              if (this.form.scheduleType === 'CRON' && !value) {
                callback(new Error('请输入Cron表达式'))
              } else {
                callback()
              }
            },
            trigger: 'blur'
          }
        ],
        interval: [
          {
            validator: (rule, value, callback) => {
              if (this.form.scheduleType === 'FIXED_DELAY') {
                if (value === undefined || value === null || value === '') {
                  callback(new Error('请输入间隔时间'))
                } else if (!Number.isInteger(value) || value <= 0) {
                  callback(new Error('间隔时间必须为大于0的整数'))
                } else {
                  callback()
                }
              } else {
                callback()
              }
            },
            trigger: 'blur'
          }
        ],
        intervalUnit: [
          {
            validator: (rule, value, callback) => {
              if (this.form.scheduleType === 'FIXED_DELAY' && !value) {
                callback(new Error('请选择时间单位'))
              } else {
                callback()
              }
            },
            trigger: 'change'
          }
        ],
        notifyType: [
          {
            validator: (rule, value, callback) => {
              if (this.form.exceptionNotify && !value) {
                callback(new Error('请选择通知方式'))
              } else {
                callback()
              }
            },
            trigger: 'change'
          }
        ],
        notifyPhone: [
          {
            validator: (rule, value, callback) => {
              if (!this.form.exceptionNotify) {
                callback()
              } else if (!value) {
                callback(new Error('请输入通知号码'))
              } else if (!/^[0-9-]{6,20}$/.test(value)) {
                callback(new Error('请输入正确的号码'))
              } else {
                callback()
              }
            },
            trigger: 'blur'
          }
        ],
        exceptionCount: [
          {
            validator: (rule, value, callback) => {
              if (this.form.exceptionNotify &&
                (value === undefined || value === null || value === '' || value < 1)) {
                callback(new Error('异常次数必须为大于0的整数'))
              } else {
                callback()
              }
            },
            trigger: 'blur'
          }
        ]
      },
      logOpen: false,
      logLoading: false,
      logList: [],
      logTotal: 0,

      currentJob: {
        jobName: '',
        jobGroup: ''
      },

      logQueryParams: {
        page: 1,
        limit: 10,
        jobName: undefined,
        jobGroup: undefined
      },

      logDetailOpen: false,
      logDetail: {}
    }
  },
  created() { this.getList();this.getAllJobList() },
  methods: {
    // 查询任务列表
    getList() {
      this.loading = true

      listQuartz(this.queryParams).then(response => {
        if (response.state === "ok") {
          this.taskList = response.data.list || []
          this.total = response.data.totalRow || 0
        } else {
          this.taskList = []
          this.total = 0
          this.$message.error(response.msg || "查询任务列表失败")
        }
      }).catch(error => {
        console.error("查询任务列表失败：", error)
        this.taskList = []
        this.total = 0
      }).finally(() => {
        this.loading = false
      })
    },
    getAllJobList() {

      listAllJob().then(response => {
        if (response.state === "ok") {
          this.allList = response.data || []
        } else {
          this.allList = []
        }
      }).catch(error => {
        console.error("查询任务列表失败：", error)
        this.allList = []
      })
    },
    handleQuery() { this.queryParams.page = 1; this.getList() },
    resetQuery() { this.resetForm('queryForm'); this.handleQuery() },
    reset() {
      this.form = {
        jobName: undefined, jobGroup: 'DEFAULT', jobType: undefined,
        cronExpression: undefined, description: undefined, params: undefined,
        exceptionNotify: false, notifyType: 'SMS', notifyPhone: undefined,
        exceptionCount: 1, notifyContent: ''
      }
      this.resetForm('form')
    },
    handleAdd() {
        this.reset()
        this.formMode = 'add'
        this.open = true
        this.title = '新增Quartz任务'

    },
    handleUpdate(row) {
      /* getQuartz(row.jobName, row.jobGroup).then(response => { */
        this.reset()
        this.form = {
          jobName: row.jobName,
          jobGroup: row.jobGroup,
          scheduleType: row.type || 'CRON',
          cronExpression: row.cronExpression || '',
          interval: row.interval ?? 5,
          intervalUnit: row.intervalUnit || 'MINUTE',
          description: row.description || '',
          params: row.params || '',
          exceptionNotify: row.exceptionNotify === true,
          notifyType: row.notifyType || 'SMS',
          notifyPhone: row.notifyPhone || '',
          exceptionCount: row.exceptionCount || 1,
          notifyContent: row.notifyContent || ''
        }
        this.formMode = 'edit'
        this.open = true
        this.title = '修改Quartz任务'
        this.$nextTick(() => {
          this.$refs.form.clearValidate()
        })
     /*  }) */
    },
    submitForm() {
      if (this.submitLoading) {
        return
      }
      this.$refs.form.validate(valid => {
        if (!valid) {
          return
        }
        this.submitLoading = true
        this.submitLoadingInstance = this.$loading({
          lock: true,
          text: "提交中，请稍候",
          spinner: "el-icon-loading",
          background: "rgba(0, 0, 0, 0.7)"
        })
        const data = { ...this.form }

        // 根据调度类型清理无关参数
        if (data.scheduleType !== 'CRON') {
          data.cronExpression = ''
        }

        if (data.scheduleType !== 'FIXED_DELAY') {
          data.interval = undefined
          data.intervalUnit = undefined
        }

        // 未开启异常通知时，不提交通知相关参数
        if (!data.exceptionNotify) {
          data.notifyType = ''
          data.notifyPhone = ''
          data.exceptionCount = undefined
          data.notifyContent = ''
        }
// 调用新增或修改接口
        const action = this.formMode === 'add'
          ? addQuartz(data)
          : updateQuartz(data)

        action.then(res => {
          if (res.state === "ok") {
            this.msgSuccess(this.formMode === 'add' ? '新增成功' : '修改成功')
            this.open = false
            this.getList()
          } else {
            this.$message.error(
              res.msg || (this.formMode === 'add' ? '新增失败' : '修改失败')
            )
          }
        }).catch(error => {
          console.error("新增或修改任务失败：", error)
        }).finally(() => {
          this.submitLoading = false
          if (this.submitLoadingInstance) {
            this.submitLoadingInstance.close()
            this.submitLoadingInstance = null
          }
        })
      })
    },
    cancel() { this.open = false; this.reset() },
    handlePause(row) {
      this.$confirm('确认暂停该任务吗？', '提示').then(async () => {
        this.loading = true
        try {
          const res = await pauseQuartz(row.jobName, row.jobGroup)

          if (res.state === "ok") {
            this.msgSuccess("暂停成功")
            this.getList()
          } else {
            this.loading = false
            this.$message.error(res.msg || "暂停失败")
          }
        } catch (error) {
          this.loading = false
          console.error("暂停任务失败：", error)
        } finally {
        }
      }).catch(() => {})
    },

    handleResume(row) {
      this.loading = true
      resumeQuartz(row.jobName, row.jobGroup).then(res => {
        if (res.state === "ok") {
          this.msgSuccess("恢复成功")
          this.getList()
        } else {
          this.loading = false
          this.$message.error(res.msg || "恢复失败")
        }
      }).catch(error => {
        this.loading = false
        console.error("恢复任务失败：", error)
      }).finally(() => {
      })
    },

    handleTrigger(row) {
      this.$confirm('确认立即执行该任务吗？', '提示').then(async () => {
        this.loading = true
        try {
          const res = await triggerQuartz(row.jobName, row.jobGroup)

          if (res.state === "ok") {
            this.msgSuccess("已触发执行")
            this.getList()
          } else {
            this.$message.error(res.msg || "触发失败")
            this.loading = false
          }
        } catch (error) {
          console.error("触发任务失败：", error)
          this.loading = false
        } finally {
        }
      }).catch(() => {})
    },

    handleDelete(row) {
      this.$confirm( `请确认是否删除该任务： ${row.jobName}- ${row.jobGroup} ？删除后不可恢复。`,
        '提示').then(async () => {
        this.loading = true
        try {
          const res = await removeQuartz(row.jobName, row.jobGroup)

          if (res.state === "ok") {
            this.msgSuccess("删除成功")
            this.getList()
          } else {
            this.loading = false
            this.$message.error(res.msg || "删除失败")
          }
        } catch (error) {
          this.loading = false
          console.error("删除任务失败：", error)
        } finally {

        }
      }).catch(() => {})
    },
    stateTag(state) {

      const map = {
        WAITING: 'success',
        ACQUIRED: 'warning',
        EXECUTING: 'primary',
        BLOCKED: 'warning',
        PAUSED: 'info',
        PAUSED_BLOCKED: 'warning',
        COMPLETE: 'info',
        ERROR: 'danger',
        NONE: 'warning',
        FAIL:'danger',
        SUCCESS:'success',
        RUNNING:'warning',
      }
      return map[state] || ''
    },
// 打开任务执行日志
    handleLog(row) {
      this.currentJob = {
        jobName: row.jobName,
        jobGroup: row.jobGroup
      }

      this.form = {
        page: 1,
        limit: 10,
        jobName: row.jobName,
        jobGroup: row.jobGroup
      }

      this.logOpen = true
      this.getLogList()
    },

// 查询执行日志
    getLogList() {
      this.logLoading = true

      listQuartzLog(this.form).then(response => {
        if (response.state === "ok") {
          this.logList = response.data.list || []
          this.logTotal = response.data.totalRow || 0
        } else {
          this.logList = []
          this.logTotal = 0
          this.$message.error(response.msg || "查询执行日志失败")
        }
      }).catch(error => {
        console.error("查询执行日志失败：", error)
        this.logList = []
        this.logTotal = 0
      }).finally(() => {
        this.logLoading = false
      })
    },

// 查看日志详情
    handleLogDetail(row) {
      this.logDetail = Object.assign({}, row)
      this.logDetailOpen = true
    },

// 执行状态标签
    logStatusTag(status) {
      const map = {
        RUNNING: 'warning',
        SUCCESS: 'success',
        FAIL: 'danger',
        VETOED: 'info',
        INTERRUPTED: 'danger'
      }
      return map[status] || 'info'
    },
    searchLog() {
      this.form.page = 1
      console.log("submitForm", this.form)

      this.getLogList(this.form)
    },
    resetLogAction() {
      this.form = {
        page: 1,
        limit: 10,
        jobName: this.form.jobName,
        jobGroup: this.form.jobGroup
      }
      this.getLogList()
    },
    handleScheduleTypeChange(type) {
      this.$nextTick(() => {
        if (type !== 'CRON') {
          this.form.cronExpression = ''
        }

        if (type !== 'FIXED_DELAY') {
          this.form.interval = 5
          this.form.intervalUnit = 'MINUTE'
        }

        this.$refs.form.clearValidate()
      })
    }
  }
}
</script>
