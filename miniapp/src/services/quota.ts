import { request } from './request'
import type { QuotaVo } from '@/types/api'

/** 今日额度：还剩几次批改 */
export function quotaToday(): Promise<QuotaVo> {
  return request<QuotaVo>({ url: '/api/v1/quota/today' })
}
