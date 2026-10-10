import Taro from '@tarojs/taro'

const PENDING_BIZ_NO = 'pending_biz_no'

/**
 * 生成幂等号。
 *
 * 生成后会立刻存到本地，直到这次提交成功才清除 —— 原因是用户可能
 * 「提交 → 网络超时 → 再点一次」，如果两次生成不同的 bizNo，服务端会当成
 * 两次不同的作业，重复批改并重复扣额度。复用同一个 bizNo 才能拿到同一任务。
 */
export function currentBizNo(): string {
  const existing = Taro.getStorageSync(PENDING_BIZ_NO)
  if (existing) {
    return existing
  }
  return renewBizNo()
}

export function renewBizNo(): string {
  const value = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
  Taro.setStorageSync(PENDING_BIZ_NO, value)
  return value
}

/** 提交成功后调用，下次提交会拿到新的幂等号 */
export function clearBizNo(): void {
  Taro.removeStorageSync(PENDING_BIZ_NO)
}
