/** 与后端 VO 一一对应的类型定义。后端改了字段，这里要同步。 */

export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface UserVo {
  id: number
  nickname?: string
  avatarUrl?: string
  quotaDaily: number
}

export interface StudentVo {
  id: number
  name: string
  grade: number
  school?: string
  /** 孩子编号，分享给家人即可绑定同一孩子、共享错题数据 */
  bindCode?: string
  /** 当前用户是否创建者（可改档案），绑定的家人只读 */
  owner: boolean
}

/** 首页每日美句（DeepSeek 生成，符合孩子年级） */
export interface DailyQuoteVo {
  date: string
  grade: number
  text: string
}

/** 「每日一话」生成的美文 */
export interface EssayVo {
  text: string
}

/** 奖励中心条目（家长预设的可兑换奖励） */
export interface RewardVo {
  id: number
  title: string
  /** 预制卡通图标 key（assets/rewards/{icon}.png；空=默认礼物图） */
  icon?: string | null
  cost: number
  /** 1 上架 2 下架 */
  status: 1 | 2
}

/** 每日任务面板（某孩子今天） */
export interface TaskTodayVo {
  date: string
  tasks: TaskItemVo[]
  summary: {
    balance: number
    todayEarned: number
    todayCompleted: number
    todayTotal: number
  }
}

export interface TaskItemVo {
  id: number
  title: string
  points: number
  completed: boolean
  completedAt?: string | null
  /** 任务详情图（作业页照片/手写任务卡；服务端 presign，短时效；无图为 null） */
  imageUrl?: string | null
}

/** 任务打卡历史（按天/周/月查询） */
export interface TaskCompletionsVo {
  range: 'day' | 'week' | 'month'
  start: string
  end: string
  items: TaskCompletionVo[]
  summary: {
    count: number
    earned: number
  }
}

export interface TaskCompletionVo {
  id: number
  taskId: number
  taskTitle: string
  pointsAwarded: number
  completeDate: string
  completedAt: string
  /** 任务详情图（服务端 presign，短时效；无图为 null） */
  imageUrl?: string | null
}

export interface PointsSummaryVo {
  balance: number
  todayEarned: number
  totalEarned: number
  totalSpent: number
}

export interface PointsLogVo {
  id: number
  delta: number
  source: number
  sourceLabel: string
  reason?: string | null
  createdAt: string
}

export interface LoginVo {
  token: string
  expiresInSeconds: number
  newUser: boolean
  user: UserVo
  /** 兼容字段：默认孩子。多孩时以 students + 前端选择为准 */
  student: StudentVo
  /** 可访问的全部孩子（创建的 + 绑定的），多孩选择靠它 */
  students: StudentVo[]
}

export interface UploadedFileVo {
  fileId: number
  width?: number
  height?: number
  size?: number
  /** 命中同内容去重 */
  reused: boolean
}

export interface PresignVo {
  url: string
  expiresInSeconds: number
}

/** 批改任务状态，与后端 HomeworkSubmit 常量一致 */
export const SubmitStatus = {
  PENDING: 0,
  REVIEWING: 1,
  GRADING: 2,
  SUCCESS: 3,
  FAILED: 4,
  CANCELED: 5,
} as const

export interface SubmitVo {
  submitId: number
  status: number
  statusText: string
  imageCount: number
  totalCount?: number
  rightCount?: number
  wrongCount?: number
  score?: number
  errorMsg?: string
  finished: boolean
}

/** 归一化 0-1 坐标，相对原图 */
export interface Bbox {
  x: number
  y: number
  w: number
  h: number
}

/** 题型，与后端 Question 常量一致 */
export const QuestionType = {
  CHOICE: 1,
  MULTI_CHOICE: 2,
  JUDGE: 3,
  FILL: 4,
  CALC: 5,
  SOLVE: 6,
  ESSAY: 7,
  OTHER: 8,
} as const

export interface QuestionVo {
  id: number
  seq: number
  questionNo?: string
  questionType: number
  stem?: string
  studentAnswer?: string
  correctAnswer?: string
  correct: boolean
  confidence?: number
  wrongReason?: string
  analysis?: string
  knowledgePoints: string[]
  difficulty?: number
  /** AI 不确定，建议用户核对而不是直接当成做错 */
  needsCheck: boolean
  /** 题目在原图中的位置，可能为空 */
  bbox?: Bbox
  cropImageUrl?: string
}

export interface SubmitDetailVo {
  submit: SubmitVo
  /** 原图可读地址，按页序，带时效 */
  images: string[]
  questions: QuestionVo[]
}

export const MistakeStatus = {
  ACTIVE: 0,
  MASTERED: 1,
  REMOVED: 2,
} as const

export interface MistakeVo {
  id: number
  subject: string
  questionType: number
  stem?: string
  studentAnswer?: string
  knowledgeKey?: string
  masteryLevel: number
  reviewCount: number
  nextReviewAt?: string
  status: number
  submitId?: number
  /** 错题卡片图，列表主视觉 */
  cardImageUrl?: string
}

export interface ReviewLog {
  id: number
  correct: boolean
  reviewedAt: string
}

export interface MistakeDetailVo {
  mistake: MistakeVo
  correctAnswer?: string
  wrongReason?: string
  analysis?: string
  knowledgePoints: string[]
  cropImageUrl?: string
  questionId?: number
  reviews: ReviewLog[]
}

export interface CountItem {
  name: string
  count: number
}

export interface MistakeStatsVo {
  total: number
  active: number
  mastered: number
  reviewDueToday: number
  bySubject: CountItem[]
  byKnowledge: CountItem[]
}

export interface QuotaVo {
  limit: number
  used: number
  remain: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export const SUBJECTS = [
  { value: 'math', label: '数学' },
  { value: 'english', label: '英语' },
  { value: 'chinese', label: '语文' },
] as const

export const GRADES = [
  { value: 1, label: '一年级' },
  { value: 2, label: '二年级' },
  { value: 3, label: '三年级' },
  { value: 4, label: '四年级' },
  { value: 5, label: '五年级' },
  { value: 6, label: '六年级' },
] as const

export const QUESTION_TYPE_LABEL: Record<number, string> = {
  1: '单选',
  2: '多选',
  3: '判断',
  4: '填空',
  5: '计算',
  6: '解答',
  7: '作文',
  8: '其他',
}
