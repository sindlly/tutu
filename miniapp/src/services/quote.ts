import { request } from './request'
import type { DailyQuoteVo } from '@/types/api'

/** 每日美句：每天每个年级一句（服务端当天落库复用，DeepSeek 生成带降级池）。 */
export function dailyQuote(studentId: number): Promise<DailyQuoteVo> {
  return request<DailyQuoteVo>({ url: `/api/v1/quote/today?studentId=${studentId}` })
}
