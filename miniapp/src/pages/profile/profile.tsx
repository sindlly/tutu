import { View, Text, Input } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureLogin, logout, setDevIdentity } from '@/services/auth'
import { clearAuth, getToken } from '@/services/request'
import { quotaToday } from '@/services/quota'
import { mistakeStats } from '@/services/mistake'
import { userStore } from '@/store/user'
import { gradeLabel } from '@/utils/format'
import type { MistakeStatsVo, QuotaVo, StudentVo, UserVo } from '@/types/api'
import './profile.scss'

export default function Profile() {
  const [user, setUser] = useState<UserVo | null>(null)
  const [student, setStudent] = useState<StudentVo | null>(null)
  const [students, setStudents] = useState<StudentVo[]>([])
  const [quota, setQuota] = useState<QuotaVo | null>(null)
  const [stats, setStats] = useState<MistakeStatsVo | null>(null)
  const [identity, setIdentity] = useState('')

  useDidShow(() => {
    void load()
  })

  async function load() {
    try {
      await ensureLogin()
      setUser(userStore.getUser())
      setStudents(userStore.getStudents())
      setStudent(userStore.getActiveStudent())
      const studentId = userStore.getActiveStudent()?.id
      const [q, s] = await Promise.all([quotaToday(), mistakeStats(studentId)])
      setQuota(q)
      setStats(s)
    } catch {
    }
  }

  async function onLogout() {
    const confirm = await Taro.showModal({ title: '退出登录', content: '确定要退出吗？' })
    if (!confirm.confirm) return
    try {
      await logout()
    } catch {
      // 服务端吊销失败也要清本地，否则用户会卡在失效态
    }
    clearAuth()
    userStore.clear()
    Taro.showToast({ title: '已退出', icon: 'none' })
    setTimeout(() => void load(), 500)
  }

  async function onSwitchIdentity() {
    if (!identity.trim()) {
      Taro.showToast({ title: '请输入一个调试身份', icon: 'none' })
      return
    }
    setDevIdentity(identity.trim())
    clearAuth()
    userStore.clear()
    Taro.showToast({ title: '已切换，正在重新登录', icon: 'none' })
    setTimeout(() => void load(), 500)
  }

  const isDev = process.env.NODE_ENV === 'development'

  return (
    <View className="page profile">
      <View className="card user-card">
        <View className="avatar">{user?.nickname?.slice(0, 1) || '家'}</View>
        <View className="user-main">
          <Text className="user-name">{user?.nickname || '未登录'}</Text>
          <Text className="muted mt8">
            {student ? `${student.name} · ${gradeLabel(student.grade)}` : '还没有孩子档案'}
          </Text>
        </View>
      </View>

      <View className="card kids-entry" onClick={() => Taro.navigateTo({ url: '/pages/children/children' })}>
        <View className="row-between">
          <Text className="title">孩子管理</Text>
          <Text className="muted">{students.length} 个孩子 ›</Text>
        </View>
        <View className="muted mt8">
          添加孩子、切换当前，或用编号绑定家人共享的孩子
        </View>
      </View>

      <View className="card">
        <View className="row-between">
          <Text className="title">今日额度</Text>
          <Text className="muted">
            已用 {quota?.used ?? 0} / {quota?.limit ?? 0}
          </Text>
        </View>
        <View className="quota-bar mt16">
          <View
            className="quota-fill"
            style={{
              width: `${quota && quota.limit > 0 ? Math.min(100, (quota.used / quota.limit) * 100) : 0}%`,
            }}
          />
        </View>
        <View className="muted mt8">还剩 {quota?.remain ?? 0} 次。批改失败会自动退回次数。</View>
      </View>

      <View className="card">
        <Text className="title">错题概览</Text>
        <View className="stat-grid mt16">
          <View className="stat-cell">
            <Text className="stat-cell-num">{stats?.active ?? 0}</Text>
            <Text className="muted">在册错题</Text>
          </View>
          <View className="stat-cell">
            <Text className="stat-cell-num warn">{stats?.reviewDueToday ?? 0}</Text>
            <Text className="muted">今日待复习</Text>
          </View>
          <View className="stat-cell">
            <Text className="stat-cell-num ok">{stats?.mastered ?? 0}</Text>
            <Text className="muted">已掌握</Text>
          </View>
        </View>
      </View>

      {isDev && (
        <View className="card dev">
          <Text className="title">开发调试</Text>
          <View className="muted mt8">
            切换调试身份可模拟多个用户（用于验证越权防护）。仅在开发构建里可见。
          </View>
          <View className="dev-row mt16">
            <Input
              className="dev-input"
              placeholder="例如 alice / bob"
              value={identity}
              onInput={(e) => setIdentity(e.detail.value)}
            />
            <View className="dev-btn" onClick={onSwitchIdentity}>
              切换
            </View>
          </View>
          <View className="muted mt8">当前 token：{getToken() ? '已登录' : '无'}</View>
        </View>
      )}

      <View className="card">
        <Text className="title">关于</Text>
        <View className="muted about mt8">
          AI 批改结果仅供参考，不能替代老师的评判。重要结论请与老师核对。{'\n\n'}
          孩子信息默认只保存称呼，请勿填写真实姓名或学校等敏感信息。
        </View>
      </View>

      <View className="btn-ghost" onClick={onLogout}>
        退出登录
      </View>
    </View>
  )
}
