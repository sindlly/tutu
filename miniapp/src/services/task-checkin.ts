import Taro from '@tarojs/taro'
import { completeTask, undoTask } from './task'
import type { TaskItemVo } from '@/types/api'

/**
 * 任务打卡交互（首页快览与任务 tab 共用）。
 *
 * 未完成·无图 → 直接打卡（最短路径）
 * 未完成·有图 → 「查看任务图片 / 打卡」（内容在图上的任务，先看图再打卡）
 * 已完成 → 撤销确认（退积分）
 *
 * 行内缩略图（tasks 页/首页快览）点击直接预览，不经此模块。
 */
export type CheckinResult = 'done' | 'undone' | 'viewed' | 'cancel'

export async function checkInTask(item: TaskItemVo): Promise<CheckinResult> {
  if (!item.completed) {
    if (!item.imageUrl) {
      await completeTask(item.id)
      return 'done'
    }
    let tapIndex: number
    try {
      ;({ tapIndex } = await Taro.showActionSheet({
        itemList: ['查看任务图片', '打卡'],
      }))
    } catch {
      return 'cancel' // 用户取消
    }
    if (tapIndex === 0) {
      Taro.previewImage({ urls: [item.imageUrl] })
      return 'viewed'
    }
    if (tapIndex === 1) {
      await completeTask(item.id)
      return 'done'
    }
    return 'cancel'
  }

  const confirm = await Taro.showModal({
    title: '撤销打卡',
    content: `「${item.title}」的 ${item.points} 分也会一起退回。`,
  })
  if (!confirm.confirm) {
    return 'cancel'
  }
  await undoTask(item.id)
  return 'undone'
}
