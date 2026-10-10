import { request } from './request'
import type { MistakeDetailVo, MistakeStatsVo, MistakeVo, PageResult } from '@/types/api'

export function listMistakes(params: {
  studentId?: number
  subject?: string
  status?: number
  page?: number
  size?: number
} = {}): Promise<PageResult<MistakeVo>> {
  const query = new URLSearchParams()
  if (params.studentId) query.set('studentId', String(params.studentId))
  if (params.subject) query.set('subject', params.subject)
  if (params.status !== undefined) query.set('status', String(params.status))
  query.set('page', String(params.page ?? 1))
  query.set('size', String(params.size ?? 20))
  return request<PageResult<MistakeVo>>({ url: `/api/v1/mistakes?${query.toString()}` })
}

/** 今日待复习（含之前欠下的），按到期时间升序。 */
export function reviewToday(params: { studentId?: number; limit?: number } = {}): Promise<MistakeVo[]> {
  const query = new URLSearchParams()
  if (params.studentId) query.set('studentId', String(params.studentId))
  query.set('limit', String(params.limit ?? 10))
  return request<MistakeVo[]>({ url: `/api/v1/mistakes/review-today?${query.toString()}` })
}

export function getMistake(id: number): Promise<MistakeDetailVo> {
  return request<MistakeDetailVo>({ url: `/api/v1/mistakes/${id}` })
}

export function reviewMistake(id: number, correct: boolean): Promise<MistakeDetailVo> {
  return request<MistakeDetailVo>({
    url: `/api/v1/mistakes/${id}/review`,
    method: 'POST',
    data: { correct },
  })
}

export function updateMistakeStatus(id: number, status: number): Promise<MistakeVo> {
  return request<MistakeVo>({ url: `/api/v1/mistakes/${id}`, method: 'PUT', data: { status } })
}

/** 重新生成错题卡片。卡片生成失败时会被容忍，这里让用户能补。 */
export function regenerateCard(id: number): Promise<MistakeVo> {
  return request<MistakeVo>({ url: `/api/v1/mistakes/${id}/card`, method: 'POST' })
}

export function mistakeStats(studentId?: number): Promise<MistakeStatsVo> {
  const query = studentId ? `?studentId=${studentId}` : ''
  return request<MistakeStatsVo>({ url: `/api/v1/mistakes/stats${query}` })
}
