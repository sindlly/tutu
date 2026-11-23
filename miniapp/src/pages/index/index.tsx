import { Image, Text, View } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import { quotaToday } from '@/services/quota'
import { listSubmits } from '@/services/homework'
import { dailyQuote } from '@/services/quote'
import { taskToday } from '@/services/task'
import { checkInTask } from '@/services/task-checkin'
import { userStore } from '@/store/user'
import Icon from '@/components/Icon'
import { SubmitStatus } from '@/types/api'
import type { DailyQuoteVo, QuotaVo, StudentVo, SubmitVo, TaskItemVo, TaskTodayVo } from '@/types/api'
import './index.scss'

/**
 * 首页 = 全局看板与入口：孩子资产（积分）、今日任务快览、拍照批改主行动、最近批改。
 * 参考看板式布局：小标语头部 → 资产大卡（含孩子胶囊） → 任务区 → 行动横幅 → 列表。
 */
export default function Index() {
  const [quota, setQuota] = useState<QuotaVo | null>(null)
  const [records, setRecords] = useState<SubmitVo[]>([])
  const [student, setStudent] = useState<StudentVo | null>(null)
  const [today, setToday] = useState<TaskTodayVo | null>(null)
  const [quote, setQuote] = useState<DailyQuoteVo | null>(null)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)

  useDidShow(() => {
    void load()
  })

  async function load() {
    setLoading(true)
    try {
      await ensureLogin()
      // 多孩必选：没选孩子就跳选择页，本页数据先不加载
      const active = await ensureActiveStudent()
      if (!active) return
      setStudent(active)
      const [q, list, t, quote] = await Promise.all([
        quotaToday(),
        listSubmits({ studentId: active.id, size: 3 }),
        taskToday(active.id),
        // 美句失败不阻塞看板（服务端有降级池，一般也不会失败）
        dailyQuote(active.id).catch(() => null),
      ])
      setQuota(q)
      setRecords(list.records)
      setToday(t)
      setQuote(quote)
    } catch {
      // 具体错误已由请求层 toast，这里只需结束 loading
    } finally {
      setLoading(false)
    }
  }

  async function reloadTasks() {
    if (!student) return
    setToday(await taskToday(student.id))
  }

  async function toggleTask(item: TaskItemVo) {
    if (busy) return
    setBusy(true)
    try {
      // 与任务 tab 同一套交互：打卡 / 查看任务图片 / 撤销
      const r = await checkInTask(item)
      if (r === 'done' || r === 'undone') {
        await reloadTasks()
      }
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
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

  function goAllTasks() {
    Taro.switchTab({ url: '/pages/tasks/tasks' })
  }

  const studentCount = userStore.getStudents().length
  const s = today?.summary
  const preview = (today?.tasks ?? []).slice(0, 3)

  return (
    <View className="page index">
      {/* 看板头部：每日美句（孩子胶囊挪进下面的资产卡） */}
      <View className="hero">
        {quote?.text && <View className="hero-quote">{quote.text}</View>}
      </View>

      {/* 资产卡：孩子的积分（头部胶囊 = 切换孩子入口） */}
      {s && (
        <View className="asset-card" onClick={() => student && Taro.navigateTo({ url: `/pages/points/points?studentId=${student.id}` })}>
          <View className="asset-head">
            <View
              className="asset-pill"
              onClick={(e) => {
                e.stopPropagation()
                Taro.navigateTo({ url: '/pages/children/children' })
              }}
            >
              {student ? student.name : '选孩子'}
              {studentCount > 1 ? ' ›' : ''}
            </View>
          </View>
          <View className="asset-main">
            <Text className="asset-num">{s.balance}</Text>
            <Text className="asset-unit">分</Text>
          </View>
          <View className="asset-foot">
            <Text>今日 +{s.todayEarned}</Text>
            <Text className="asset-dot">·</Text>
            <Text>任务 {s.todayCompleted}/{s.todayTotal}</Text>
            <Text className="asset-more">明细 ›</Text>
          </View>
        </View>
      )}

      {/* 每日任务快览 */}
      <View className="section-head">
        <Text className="section-title">每日任务</Text>
        <Text className="section-more" onClick={goAllTasks}>全部 {s?.todayTotal ?? 0} 个 ›</Text>
      </View>
      <View className="task-brief">
        {preview.map((item) => (
          <View
            key={item.id}
            className={`brief-row ${item.completed ? 'brief-done' : ''}`}
            onClick={() => toggleTask(item)}
          >
            <View className={`brief-check ${item.completed ? 'brief-check-on' : ''}`}>
              {item.completed ? '✓' : ''}
            </View>
            <Text className="brief-title">{item.title}</Text>
            {item.imageUrl && (
              <Image
                className="brief-thumb"
                src={item.imageUrl}
                mode="aspectFill"
                onClick={(e) => {
                  e.stopPropagation()
                  Taro.previewImage({ urls: [item.imageUrl!] })
                }}
              />
            )}
            <Text className="brief-points">+{item.points}</Text>
          </View>
        ))}
        {preview.length === 0 && <View className="empty small">今天还没有任务，去「任务」页添加</View>}
      </View>

      {/* 主行动横幅 */}
      <View className="banner" onClick={goCapture}>
        <View className="banner-main">
          <Icon name="camera" size={44} color="#8a4630" />
          <Text>拍照批改作业</Text>
        </View>
        <View className="banner-sub">
          对错几秒出 · 今日还剩 {quota?.remain ?? '-'} 次
        </View>
        <View className="banner-arrow">
          <Icon name="chevron-right" size={44} color="#8a4630" />
        </View>
      </View>

      {/* 最近批改 */}
      <View className="section-head">
        <Text className="section-title">最近批改</Text>
        <Text className="section-more" onClick={() => Taro.navigateTo({ url: '/pages/mistakes/mistakes' })}>
          错题本 ›
        </Text>
      </View>
      <View className="record-list">
        {loading && records.length === 0 ? (
          <View className="empty small">加载中…</View>
        ) : records.length === 0 ? (
          <View className="empty small">还没有批改记录{'\n'}点上面的橙色卡片开始</View>
        ) : (
          records.map((item) => (
            <View key={item.submitId} className="record-card" onClick={() => openRecord(item)}>
              <View className="record-left">
                <View className="record-title">
                  {item.finished ? item.statusText : '批改中…'}
                </View>
                <View className="record-sub muted">
                  {item.imageCount} 页
                  {item.finished && item.status === SubmitStatus.SUCCESS
                    ? ` · 共 ${item.totalCount ?? 0} 题 · 错 ${item.wrongCount ?? 0} 题`
                    : ''}
                </View>
              </View>
              <View className="record-right">
                {item.status === SubmitStatus.SUCCESS ? (
                  <Text className="record-score">{item.score ?? 0}</Text>
                ) : item.status === SubmitStatus.FAILED ? (
                  <Text className="pill pill-wrong">失败</Text>
                ) : (
                  <Text className="pill pill-running">批改中</Text>
                )}
              </View>
            </View>
          ))
        )}
      </View>

      <View className="tips muted">AI 批改结果仅供参考，重要结论请与老师核对。</View>
    </View>
  )
}
