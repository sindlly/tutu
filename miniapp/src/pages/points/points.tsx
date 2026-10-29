import { Image, ScrollView, View, Text, Input } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import { adjustPoints, pointsLogs, pointsSummary } from '@/services/task'
import { createReward, listRewards, redeemReward, updateReward } from '@/services/reward'
import { userStore } from '@/store/user'
import { formatDateTime } from '@/utils/format'
import { REWARD_ICONS, rewardIconSrc } from '@/utils/reward-icons'
import type { PointsLogVo, PointsSummaryVo, RewardVo, StudentVo } from '@/types/api'
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

  // 奖励中心
  const [rewards, setRewards] = useState<RewardVo[]>([])
  const [rewardFormOpen, setRewardFormOpen] = useState(false)
  const [rewardTitle, setRewardTitle] = useState('')
  const [rewardCost, setRewardCost] = useState('')
  const [rewardIcon, setRewardIcon] = useState('gift')

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
    const [s, l, r] = await Promise.all([
      pointsSummary(studentId),
      pointsLogs(studentId, p, PAGE_SIZE),
      listRewards(studentId).catch(() => []),
    ])
    setSummary(s)
    setLogs(l.records)
    setTotal(l.total)
    setPage(p)
    setRewards(r)
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

  async function addReward() {
    if (busy || !student) return
    const title = rewardTitle.trim()
    const cost = parseInt(rewardCost, 10)
    if (!title) {
      Taro.showToast({ title: '先写奖励名，比如「冰淇淋」', icon: 'none' })
      return
    }
    if (!cost || cost < 1 || cost > 9999) {
      Taro.showToast({ title: '积分填 1~9999', icon: 'none' })
      return
    }
    setBusy(true)
    try {
      await createReward(student.id, title, cost, rewardIcon)
      setRewardTitle('')
      setRewardCost('')
      setRewardIcon('gift')
      await loadOf(student.id, 1)
      Taro.showToast({ title: '已加入奖励中心', icon: 'none' })
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  async function toggleRewardStatus(r: RewardVo) {
    if (busy || !student) return
    setBusy(true)
    try {
      await updateReward(r.id, { status: r.status === 1 ? 2 : 1 })
      await loadOf(student.id, 1)
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  async function redeem(r: RewardVo) {
    if (busy || !student) return
    const confirm = await Taro.showModal({
      title: '确认兑换',
      content: `用 ${r.cost} 分兑换「${r.title}」？`,
    })
    if (!confirm.confirm) return
    setBusy(true)
    try {
      const s = await redeemReward(r.id)
      setSummary(s)
      await loadOf(student.id, 1)
      Taro.showToast({ title: `已兑换「${r.title}」`, icon: 'none' })
    } catch {
      // 请求层已 toast（余额不足会提示还差几分）
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

      {/* 奖励中心：预设可兑换奖励，积分够就能换 */}
      <View className="log-head">奖励中心</View>
      <View className="reward-list">
        {rewards.map((r) => (
          <View key={r.id} className={`reward-row ${r.status === 2 ? 'reward-off' : ''}`}>
            <Image className="reward-icon" src={rewardIconSrc(r.icon)} mode="aspectFill" />
            <View className="reward-main">
              <View className="reward-title">{r.title}</View>
              {r.status === 2 && <View className="reward-off-tag">已下架</View>}
            </View>
            <Text className="reward-cost">{r.cost} 分</Text>
            {r.status === 1 ? (
              <View
                className={`reward-btn ${summary && summary.balance < r.cost ? 'reward-btn-poor' : ''}`}
                onClick={busy || (summary && summary.balance < r.cost) ? undefined : () => redeem(r)}
              >
                兑换
              </View>
            ) : (
              <View className="reward-btn reward-btn-restore" onClick={busy ? undefined : () => toggleRewardStatus(r)}>
                恢复
              </View>
            )}
            {r.status === 1 && (
              <Text className="reward-remove" onClick={busy ? undefined : () => toggleRewardStatus(r)}>
                下架
              </Text>
            )}
          </View>
        ))}
        {rewards.length === 0 && <View className="empty small">还没有可兑换的奖励</View>}

        {rewardFormOpen ? (
          <View className="reward-form">
            <View className="form-label">挑个图标</View>
            <ScrollView className="icon-picker" scrollX enhanced showScrollbar={false}>
              <View className="icon-picker-row">
                {REWARD_ICONS.map((icon) => (
                  <View
                    key={icon.key}
                    className={`icon-pick ${rewardIcon === icon.key ? 'icon-pick-on' : ''}`}
                    onClick={() => setRewardIcon(icon.key)}
                  >
                    <Image className="icon-pick-img" src={icon.src} mode="aspectFill" />
                    <Text className="icon-pick-label">{icon.label}</Text>
                  </View>
                ))}
              </View>
            </ScrollView>
            <Input
              className="form-input"
              value={rewardTitle}
              maxlength={20}
              placeholder="奖励名，比如「冰淇淋」「公园半日游」"
              onInput={(e) => setRewardTitle(e.detail.value)}
            />
            <View className="reward-form-cost">
              <Input
                className="form-input form-input-delta"
                value={rewardCost}
                type="number"
                maxlength={4}
                placeholder="所需积分"
                onInput={(e) => setRewardCost(e.detail.value)}
              />
              <Text className="form-label">分</Text>
            </View>
            <View className={`btn-primary reward-save ${busy ? 'disabled' : ''}`} onClick={busy ? undefined : addReward}>
              添加奖励
            </View>
            <View className="form-cancel muted" onClick={() => setRewardFormOpen(false)}>
              收起
            </View>
          </View>
        ) : (
          <View className="reward-add" onClick={() => setRewardFormOpen(true)}>
            ＋ 添加奖励
          </View>
        )}
      </View>

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
