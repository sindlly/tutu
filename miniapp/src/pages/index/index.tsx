import { View, Text } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureLogin } from '@/services/auth'
import { quotaToday } from '@/services/quota'
import { listSubmits } from '@/services/homework'
import { userStore } from '@/store/user'
import { SubmitStatus } from '@/types/api'
import type { QuotaVo, SubmitVo } from '@/types/api'
import './index.scss'

export default function Index() {
  const [quota, setQuota] = useState<QuotaVo | null>(null)
  const [records, setRecords] = useState<SubmitVo[]>([])
  const [loading, setLoading] = useState(true)

  useDidShow(() => {
    void load()
  })

  async function load() {
    setLoading(true)
    try {
      await ensureLogin()
      const [q, list] = await Promise.all([quotaToday(), listSubmits({ size: 5 })])
      setQuota(q)
      setRecords(list.records)
    } catch {
      // 具体错误已由请求层 toast，这里只需结束 loading
    } finally {
      setLoading(false)
    }
  }

  function goCapture() {
    if (quota && quota.remain <= 0) {
      Taro.showModal({
        title: '今日次数已用完',
        content: '每天可以免费批改 10 次，明天再来吧。',
        showCancel: false,
      })
      return
    }
    Taro.navigateTo({ url: '/pages/capture/capture' })
  }

  function openRecord(record: SubmitVo) {
    if (!record.finished) {
      Taro.navigateTo({ url: `/pages/progress/progress?submitId=${record.submitId}` })
      return
    }
    if (record.status === SubmitStatus.FAILED) {
      Taro.showToast({ title: record.errorMsg || '这次批改失败了', icon: 'none' })
      return
    }
    Taro.navigateTo({ url: `/pages/result/result?submitId=${record.submitId}` })
  }

  const student = userStore.getStudent()

  return (
    <View className="page index">
      <View className="hero">
        <View className="hero-title">拍照改作业</View>
        <View className="hero-sub">
          {student ? `${student.name} · 今天已批改 ${quota?.used ?? 0} 次` : '拍张照，几秒出结果'}
        </View>
        <View className="quota">
          <Text className="quota-num">{quota ? quota.remain : '-'}</Text>
          <Text className="quota-label">次可用</Text>
        </View>
      </View>

      <View className="primary-action" onClick={goCapture}>
        <View className="primary-action-icon">📷</View>
        <View className="primary-action-main">拍照批改</View>
        <View className="primary-action-sub">支持一次拍 1~4 页 · 小学语数英</View>
      </View>

      <View className="card">
        <View className="row-between">
          <Text className="title">最近批改</Text>
          <Text className="muted" onClick={() => Taro.navigateTo({ url: '/pages/mistakes/mistakes' })}>
            看错题本 ›
          </Text>
        </View>

        {loading && records.length === 0 ? (
          <View className="empty">加载中…</View>
        ) : records.length === 0 ? (
          <View className="empty">还没有批改记录{'\n'}点上面的按钮开始吧</View>
        ) : (
          records.map((item) => (
            <View key={item.submitId} className="record" onClick={() => openRecord(item)}>
              <View className="record-left">
                <View className="record-title">
                  {item.finished ? item.statusText : '批改中…'}
                </View>
                <View className="muted mt8">
                  {item.imageCount} 页
                  {item.finished && item.status === SubmitStatus.SUCCESS
                    ? ` · 共 ${item.totalCount ?? 0} 题 · 错 ${item.wrongCount ?? 0} 题`
                    : ''}
                </View>
              </View>
              <View className="record-right">
                {item.status === SubmitStatus.SUCCESS ? (
                  <Text className="score">{item.score ?? 0}</Text>
                ) : item.status === SubmitStatus.FAILED ? (
                  <Text className="tag tag-wrong">失败</Text>
                ) : (
                  <Text className="tag">进行中</Text>
                )}
              </View>
            </View>
          ))
        )}
      </View>

      <View className="tips muted">
        AI 批改结果仅供参考，重要结论请与老师核对。
      </View>
    </View>
  )
}
