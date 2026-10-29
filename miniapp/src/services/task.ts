import { request } from './request'
import type { PageResult, PointsLogVo, PointsSummaryVo, TaskCompletionsVo, TaskItemVo, TaskTodayVo } from '@/types/api'

/** 今日任务面板（含打卡状态与积分概览）。首次访问会种下默认任务。 */
export function taskToday(studentId: number): Promise<TaskTodayVo> {
  return request<TaskTodayVo>({ url: `/api/v1/tasks/today?studentId=${studentId}` })
}

/** 打卡（幂等，重复点不重复加分）。可带凭证图：已打过时只补传/替换凭证，不重复加分。 */
export function completeTask(id: number, imageFileId?: number): Promise<TaskItemVo> {
  return request<TaskItemVo>({
    url: `/api/v1/tasks/${id}/complete`,
    method: 'POST',
    data: imageFileId ? { imageFileId } : {},
  })
}

/** 撤销今日打卡（写反向积分流水，账目可审计）。 */
export function undoTask(id: number): Promise<void> {
  return request<void>({ url: `/api/v1/tasks/${id}/undo`, method: 'POST' })
}

/** 添加任务。对该孩子有访问权的家人都能加。imageFileId 可选：任务详情图。 */
export function createTask(data: {
  studentId: number
  title: string
  points: number
  /** 任务详情图（t_file id，内容在图上的任务） */
  imageFileId?: number
}): Promise<TaskItemVo> {
  return request<TaskItemVo>({ url: '/api/v1/tasks', method: 'POST', data })
}

/** 归档任务（软删，历史流水仍可对账）。 */
export function archiveTask(id: number): Promise<void> {
  return request<void>({ url: `/api/v1/tasks/${id}/archive`, method: 'POST' })
}

/** 打卡历史。range=day/week/month，date 为区间锚点（默认今天，yyyy-MM-dd）。 */
export function taskCompletions(
  studentId: number,
  range: 'day' | 'week' | 'month',
  date?: string,
): Promise<TaskCompletionsVo> {
  const qs = `studentId=${studentId}&range=${range}${date ? `&date=${date}` : ''}`
  return request<TaskCompletionsVo>({ url: `/api/v1/tasks/completions?${qs}` })
}

export function pointsSummary(studentId: number): Promise<PointsSummaryVo> {
  return request<PointsSummaryVo>({ url: `/api/v1/points/summary?studentId=${studentId}` })
}

export function pointsLogs(
  studentId: number,
  page = 1,
  size = 20,
): Promise<PageResult<PointsLogVo>> {
  return request<PageResult<PointsLogVo>>({
    url: `/api/v1/points/logs?studentId=${studentId}&page=${page}&size=${size}`,
  })
}

/** 家长调整积分：正数奖励，负数兑换消耗（必须写备注）。 */
export function adjustPoints(data: {
  studentId: number
  delta: number
  reason?: string
}): Promise<PointsSummaryVo> {
  return request<PointsSummaryVo>({ url: '/api/v1/points/adjust', method: 'POST', data })
}
