import { request } from './request'
import type { PointsSummaryVo, RewardVo } from '@/types/api'

/** 奖励列表（上架在前，含下架的供管理）。 */
export function listRewards(studentId: number): Promise<RewardVo[]> {
  return request<RewardVo[]>({ url: `/api/v1/rewards?studentId=${studentId}` })
}

/** 添加可兑换奖励。icon 可选：预制卡通图标 key。 */
export function createReward(
  studentId: number,
  title: string,
  cost: number,
  icon?: string,
): Promise<RewardVo> {
  return request<RewardVo>({ url: '/api/v1/rewards', method: 'POST', data: { studentId, title, cost, icon } })
}

/** 改奖励（名字/积分/上下架/图标）。 */
export function updateReward(
  id: number,
  data: { title?: string; cost?: number; status?: 1 | 2; icon?: string },
): Promise<RewardVo> {
  return request<RewardVo>({ url: `/api/v1/rewards/${id}`, method: 'PUT', data })
}

/** 兑换（余额不足后端会拒绝），成功返回最新积分汇总。 */
export function redeemReward(id: number): Promise<PointsSummaryVo> {
  return request<PointsSummaryVo>({ url: `/api/v1/rewards/${id}/redeem`, method: 'POST' })
}
