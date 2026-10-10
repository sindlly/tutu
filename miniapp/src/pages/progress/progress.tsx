import { View } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { useEffect, useRef, useState } from 'react'
import { cancelSubmit, getSubmit, retrySubmit } from '@/services/homework'
import { SubmitStatus } from '@/types/api'
import type { SubmitVo } from '@/types/api'
import './progress.scss'

const POLL_INTERVAL_MS = 2000

export default function Progress() {
  const router = useRouter()
  const submitId = Number(router.params.submitId)

  const [submit, setSubmit] = useState<SubmitVo | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [working, setWorking] = useState(false)

  // 用 ref 存 timer，避免 effect 重跑时丢引用导致定时器泄漏
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const tickTimer = useRef<ReturnType<typeof setInterval> | null>(null)
  const stopped = useRef(false)

  useEffect(() => {
    if (!submitId) {
      Taro.showToast({ title: '参数错误', icon: 'none' })
      return
    }
    stopped.current = false
    void poll()

    tickTimer.current = setInterval(() => {
      setElapsed((prev) => prev + 1)
    }, 1000)

    return () => {
      stopped.current = true
      if (pollTimer.current) clearTimeout(pollTimer.current)
      if (tickTimer.current) clearInterval(tickTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitId])

  async function poll() {
    if (stopped.current) return
    try {
      const result = await getSubmit(submitId)
      setSubmit(result)

      if (result.finished) {
        if (tickTimer.current) clearInterval(tickTimer.current)
        if (result.status === SubmitStatus.SUCCESS) {
          Taro.redirectTo({ url: `/pages/result/result?submitId=${submitId}` })
        }
        // 失败/取消停留在本页，让用户看到原因并能重试
        return
      }
    } catch {
      // 单次轮询失败不中断，下一次继续
    }
    pollTimer.current = setTimeout(poll, POLL_INTERVAL_MS)
  }

  async function onRetry() {
    setWorking(true)
    try {
      await retrySubmit(submitId)
      setSubmit(null)
      setElapsed(0)
      stopped.current = false
      void poll()
    } catch {
      // 额度不足等错误已由请求层提示
    } finally {
      setWorking(false)
    }
  }

  async function onCancel() {
    setWorking(true)
    try {
      await cancelSubmit(submitId)
      Taro.showToast({ title: '已取消，次数已退回', icon: 'none' })
      setTimeout(() => Taro.navigateBack(), 800)
    } catch {
    } finally {
      setWorking(false)
    }
  }

  const failed = submit?.status === SubmitStatus.FAILED
  const canceled = submit?.status === SubmitStatus.CANCELED
  const running = !failed && !canceled

  return (
    <View className="page progress">
      <View className="stage">
        {running ? (
          <>
            <View className="spinner" />
            <View className="stage-title">{submit?.statusText || '正在提交…'}</View>
            <View className="muted mt8">
              {submit?.status === SubmitStatus.PENDING
                ? '排队中，马上开始'
                : '正在识别题目并判对错，通常 5~20 秒'}
            </View>
            <View className="muted mt8">已等待 {elapsed} 秒</View>
          </>
        ) : failed ? (
          <>
            <View className="stage-icon">😕</View>
            <View className="stage-title">这次批改没成功</View>
            <View className="muted mt8">{submit?.errorMsg || '请稍后重试'}</View>
            <View className="muted mt8">失败不会消耗次数，已自动退回</View>
          </>
        ) : (
          <>
            <View className="stage-icon">🙂</View>
            <View className="stage-title">已取消</View>
            <View className="muted mt8">次数已退回</View>
          </>
        )}
      </View>

      <View className="actions">
        {failed && (
          <View className={`btn-primary ${working ? 'disabled' : ''}`} onClick={working ? undefined : onRetry}>
            重新批改
          </View>
        )}
        {running && (
          <View className="btn-ghost mt16" onClick={working ? undefined : onCancel}>
            取消这次批改
          </View>
        )}
        {!running && (
          <View className="btn-ghost mt16" onClick={() => Taro.navigateBack()}>
            返回
          </View>
        )}
      </View>

      <View className="muted tips">批改期间可以切到别的页面，回来还在。</View>
    </View>
  )
}
