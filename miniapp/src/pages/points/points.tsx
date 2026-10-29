import { View, Text, Input } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import { adjustPoints, pointsLogs, pointsSummary } from '@/services/task'
import { userStore } from '@/store/user'
import { formatDateTime } from '@/utils/format'
import type { PointsLogVo, PointsSummaryVo, StudentVo } from '@/types/api'
import './points.scss'

const PAGE_SIZE = 20

/** 积分明细：余额、流水、家长奖励/兑换。 */
export default function Points() {
  const [student, setStudent] = useState<StudentVo | null>(null)
  const [summary, setSummary] = useState<PointsSummaryVo | null>(null)
  const [logs, setLogs] = useState<PointsLogVo[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [delta, setDelta] = useState('')
  const [reason, setReason] = useState('')
  const [formOpen, setFormOpen] = useState(false)

  useDidShow(() => {
    void load()
  })

  async function load() {
    setLoading(true)
    try {
      await ensureLogin()
      const active = userStore.getActiveStudent()
      if (!active) {
        const picked = await ensureActiveStudent()
        if (!picked) return
        setStudent(picked)
        await loadOf(picked.id, 1)
      } else if (student?.id !== active.id || !summary) {
        setStudent(active)
        await loadOf(active.id, 1)
      }
    } catch {
      // 请求层已 toast
    } finally {
      setLoading(false)
    }
  }

  async function loadOf(studentId: number, p: number) {
    const [s, l] = await Promise.all([pointsSummary(studentId), pointsLogs(studentId, p, PAGE_SIZE)])
    setSummary(s)
    setLogs(l.records)
    setTotal(l.total)
    setPage(p)
  }

  async function loadMore() {
    if (loading || !student || logs.length >= total) return
    setLoading(true)
    try {
      const next = page + 1
      const l = await pointsLogs(student.id, next, PAGE_SIZE)
      setLogs((prev) => [...prev, ...l.records])
      setPage(next)
    } catch {
      // 请求层已 toast
    } finally {
      setLoading(false)
    }
  }

  function setQuick(value: number) {
    setDelta(String(value))
  }

  async function submit() {
    if (busy || !student) return
    const d = parseInt(delta, 10)
    if (!d || d === 0 || Math.abs(d) > 9999) {
      Taro.showToast({ title: '填一个 1~9999 的数', icon: 'none' })
      return
    }
    if (d < 0 && !reason.trim()) {
      Taro.showToast({ title: '兑换要写清楚换了什么', icon: 'none' })
      return
    }
    setBusy(true)
    try {
      const s = await adjustPoints({ studentId: student.id, delta: d, reason: reason.trim() })
      setSummary(s)
      setDelta('')
      setReason('')
      setFormOpen(false)
      await loadOf(student.id, 1)
      Taro.showToast({ title: d > 0 ? `奖励 +${d} 分` : `兑换 ${d} 分`, icon: 'none' })
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  return (
    <View className="page points">
      {student && (
        <View className="kid-strip">
          <Text>「{student.name}」的积分</Text>
        </View>
      )}

      {summary && (
        <View className="card sum-card">
          <View className="sum-main">
            <Text className="sum-num">{summary.balance}</Text>
            <Text className="sum-unit">分</Text>
          </View>
          <View className="sum-stats">
            <View className="sum-stat">
              <Text className="sum-stat-num">+{summary.totalEarned}</Text>
              <Text className="sum-stat-label">累计获得</Text>
            </View>
            <View className="sum-stat">
              <Text className="sum-stat-num">-{summary.totalSpent}</Text>
              <Text className="sum-stat-label">累计兑换</Text>
            </View>
            <View className="sum-stat">
              <Text className="sum-stat-num">+{summary.todayEarned}</Text>
              <Text className="sum-stat-label">今日</Text>
            </View>
          </View>
        </View>
      )}

      {formOpen ? (
        <View className="card form-card">
          <View className="form-title">奖励或兑换</View>
          <View className="quick-row">
            {[-10, -5, 5, 10].map((v) => (
              <View key={v} className={`quick-cell ${v < 0 ? 'quick-spend' : ''}`} onClick={() => setQuick(v)}>
                {v > 0 ? `+${v}` : v}
              </View>
            ))}
          </View>
          <View className="form-line">
            <Input
              className="form-input form-input-delta"
              value={delta}
              type="number"
              placeholder="±分数"
              onInput={(e) => setDelta(e.detail.value)}
            />
            <Text className="form-label">分</Text>
          </View>
          <Input
            className="form-input"
            value={reason}
            maxlength={128}
            placeholder={parseInt(delta, 10) < 0 ? '兑换了什么（必填）' : '备注，如「主动帮做家务」'}
            onInput={(e) => setReason(e.detail.value)}
          />
          <View className={`btn-primary form-btn ${busy ? 'disabled' : ''}`} onClick={busy ? undefined : submit}>
            记一笔
          </View>
          <View className="form-cancel muted" onClick={() => setFormOpen(false)}>
            收起
          </View>
        </View>
      ) : (
        <View className="btn-ghost adjust-entry" onClick={() => setFormOpen(true)}>
          ＋ 奖励 / 兑换积分
        </View>
      )}

      <View className="log-head">积分明细</View>
      <View className="log-list">
        {logs.map((log) => (
          <View key={log.id} className="log-row">
            <View className="log-main">
              <View className="log-title">
                <Text className="log-tag">{log.sourceLabel}</Text>
                <Text className="log-reason">{log.reason || (log.delta > 0 ? '积分奖励' : '积分兑换')}</Text>
              </View>
              <View className="log-time muted">{formatDateTime(log.createdAt)}</View>
            </View>
            <View className={`log-delta ${log.delta >= 0 ? 'log-plus' : 'log-minus'}`}>
              {log.delta > 0 ? `+${log.delta}` : log.delta}
            </View>
          </View>
        ))}
        {logs.length === 0 && !loading && <View className="empty">还没有积分记录</View>}
        {logs.length < total && (
          <View className="load-more muted" onClick={loadMore}>
            加载更多（{logs.length}/{total}）
          </View>
        )}
      </View>
    </View>
  )
}
