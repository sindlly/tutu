import { View, Text, Image } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { listMistakes, mistakeStats, reviewToday } from '@/services/mistake'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import { userStore } from '@/store/user'
import { subjectLabel, masteryStars, formatDate } from '@/utils/format'
import { SUBJECTS } from '@/types/api'
import type { MistakeStatsVo, MistakeVo } from '@/types/api'
import './mistakes.scss'

const PAGE_SIZE = 20

export default function Mistakes() {
  const [stats, setStats] = useState<MistakeStatsVo | null>(null)
  const [dueList, setDueList] = useState<MistakeVo[]>([])
  const [list, setList] = useState<MistakeVo[]>([])
  const [total, setTotal] = useState(0)
  const [subject, setSubject] = useState<string>('')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [kidName, setKidName] = useState('')

  useDidShow(() => {
    void reload()
  })

  async function reload() {
    setLoading(true)
    try {
      await ensureLogin()
      // 多孩必选：错题本按孩子分册，没选孩子就跳选择页
      const active = await ensureActiveStudent()
      if (!active) return
      setKidName(active.name)
      const studentId = active.id
      const [s, due, pageResult] = await Promise.all([
        mistakeStats(studentId),
        reviewToday({ studentId, limit: 5 }),
        listMistakes({ studentId, subject: subject || undefined, status: 0, page: 1, size: PAGE_SIZE }),
      ])
      setStats(s)
      setDueList(due)
      setList(pageResult.records)
      setTotal(pageResult.total)
      setPage(1)
    } catch {
    } finally {
      setLoading(false)
    }
  }

  async function switchSubject(next: string) {
    setSubject(next)
    setLoading(true)
    try {
      const studentId = userStore.getActiveStudent()?.id
      const result = await listMistakes({
        studentId,
        subject: next || undefined,
        status: 0,
        page: 1,
        size: PAGE_SIZE,
      })
      setList(result.records)
      setTotal(result.total)
      setPage(1)
    } catch {
    } finally {
      setLoading(false)
    }
  }

  async function loadMore() {
    if (loading || list.length >= total) return
    setLoading(true)
    try {
      const studentId = userStore.getActiveStudent()?.id
      const next = page + 1
      const result = await listMistakes({
        studentId,
        subject: subject || undefined,
        status: 0,
        page: next,
        size: PAGE_SIZE,
      })
      setList([...list, ...result.records])
      setPage(next)
    } catch {
    } finally {
      setLoading(false)
    }
  }

  function openDetail(id: number) {
    Taro.navigateTo({ url: `/pages/mistake-detail/mistake-detail?id=${id}` })
  }

  return (
    <View className="page mistakes">
      {kidName && (
        <View
          className="kid-strip"
          onClick={() => Taro.navigateTo({ url: '/pages/child-select/child-select' })}
        >
          <Text>「{kidName}」的错题本</Text>
          <Text className="kid-strip-op">切换孩子</Text>
        </View>
      )}

      <View className="card stats">
        <View className="stats-row">
          <View className="stat">
            <Text className="stat-num">{stats?.active ?? 0}</Text>
            <Text className="stat-label">在册</Text>
          </View>
          <View className="stat">
            <Text className="stat-num warn">{stats?.reviewDueToday ?? 0}</Text>
            <Text className="stat-label">今日待复习</Text>
          </View>
          <View className="stat">
            <Text className="stat-num ok">{stats?.mastered ?? 0}</Text>
            <Text className="stat-label">已掌握</Text>
          </View>
        </View>
      </View>

      {dueList.length > 0 && (
        <View className="card">
          <View className="row-between">
            <Text className="title">今天该复习了</Text>
            <Text className="muted">{dueList.length} 道</Text>
          </View>
          <View className="due-list mt16">
            {dueList.map((item) => (
              <View key={item.id} className="due-item" onClick={() => openDetail(item.id)}>
                <Text className="due-stem">{item.stem || '（题干缺失）'}</Text>
                <Text className="muted">{masteryStars(item.masteryLevel)}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      <View className="filters">
        <View
          className={`filter ${subject === '' ? 'filter-active' : ''}`}
          onClick={() => switchSubject('')}
        >
          全部
        </View>
        {SUBJECTS.map((item) => (
          <View
            key={item.value}
            className={`filter ${subject === item.value ? 'filter-active' : ''}`}
            onClick={() => switchSubject(item.value)}
          >
            {item.label}
          </View>
        ))}
      </View>

      {loading && list.length === 0 ? (
        <View className="empty">加载中…</View>
      ) : list.length === 0 ? (
        <View className="empty">
          还没有错题{'\n'}去首页拍一张作业试试
        </View>
      ) : (
        <>
          {list.map((item) => (
            <View key={item.id} className="m-card" onClick={() => openDetail(item.id)}>
              {item.cardImageUrl ? (
                <Image className="m-card-img" src={item.cardImageUrl} mode="widthFix" />
              ) : (
                <View className="m-card-fallback">
                  <Text className="m-card-stem">{item.stem || '（题干缺失）'}</Text>
                  <Text className="muted mt8">上次答：{item.studentAnswer || '—'}</Text>
                </View>
              )}
              <View className="m-card-foot">
                <Text className="tag">{subjectLabel(item.subject)}</Text>
                {item.knowledgeKey && <Text className="tag">{item.knowledgeKey}</Text>}
                <Text className="m-card-mastery">{masteryStars(item.masteryLevel)}</Text>
              </View>
              <View className="muted m-card-time">
                复习 {item.reviewCount} 次 · 下次 {formatDate(item.nextReviewAt) || '—'}
              </View>
            </View>
          ))}

          {list.length < total ? (
            <View className="btn-ghost mt16" onClick={loadMore}>
              {loading ? '加载中…' : `加载更多（还有 ${total - list.length} 道）`}
            </View>
          ) : (
            <View className="muted tips">共 {total} 道，已全部加载</View>
          )}
        </>
      )}
    </View>
  )
}
