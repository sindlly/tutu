import { request } from './request'
import type { PageResult, SubmitDetailVo, SubmitVo } from '@/types/api'

export interface CreateSubmitParams {
  /** 客户端幂等号。同一次作业重试必须复用同一个值，否则会重复批改、重复扣额度 */
  bizNo: string
  studentId: number
  subject: string
  grade: number
  fileIds: number[]
}

export function createSubmit(data: CreateSubmitParams): Promise<SubmitVo> {
  return request<SubmitVo>({ url: '/api/v1/homework/submits', method: 'POST', data })
}

/** 状态轮询用。finished 为 true 时可以直接去取结果。 */
export function getSubmit(submitId: number): Promise<SubmitVo> {
  return request<SubmitVo>({ url: `/api/v1/homework/submits/${submitId}`, showError: false })
}

export function getResult(submitId: number): Promise<SubmitDetailVo> {
  return request<SubmitDetailVo>({ url: `/api/v1/homework/submits/${submitId}/result` })
}

export function listSubmits(params: { studentId?: number; page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.studentId) query.set('studentId', String(params.studentId))
  query.set('page', String(params.page ?? 1))
  query.set('size', String(params.size ?? 20))
  return request<PageResult<SubmitVo>>({ url: `/api/v1/homework/submits?${query.toString()}` })
}

export function cancelSubmit(submitId: number): Promise<SubmitVo> {
  return request<SubmitVo>({ url: `/api/v1/homework/submits/${submitId}/cancel`, method: 'POST' })
}

export function retrySubmit(submitId: number): Promise<SubmitVo> {
  return request<SubmitVo>({ url: `/api/v1/homework/submits/${submitId}/retry`, method: 'POST' })
}
