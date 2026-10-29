import { request } from './request'
import type { EssayVo } from '@/types/api'

/** 「每日一话」：按用户要求 + 孩子年级生成小美文。AI 生成需数秒，调用方配 loading。 */
export function generateEssay(studentId: number, requirements: string): Promise<EssayVo> {
  return request<EssayVo>({
    url: '/api/v1/essay/generate',
    method: 'POST',
    data: { studentId, requirements },
  })
}
