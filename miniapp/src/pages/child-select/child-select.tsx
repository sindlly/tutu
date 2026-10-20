import { View, Text } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureLogin } from '@/services/auth'
import { userStore } from '@/store/user'
import { gradeLabel } from '@/utils/format'
import type { StudentVo } from '@/types/api'
import './child-select.scss'

/**
 * 多孩选择页。有多个孩子且未选择时，各页面的数据加载会跳到这里。
 * 选完 navigateBack，原页面的 useDidShow 会带着新选择重新加载。
 */
export default function ChildSelect() {
  const [students, setStudents] = useState<StudentVo[]>([])
  const [activeId, setActiveId] = useState<number | null>(null)

  useDidShow(() => {
    void load()
  })

  async function load() {
    try {
      await ensureLogin()
      setStudents(userStore.getStudents())
      setActiveId(userStore.getActiveStudent()?.id ?? null)
    } catch {
      // 请求层已 toast
    }
  }

  function choose(student: StudentVo) {
    userStore.setActiveStudent(student.id)
    Taro.navigateBack()
  }

  function goManage() {
    Taro.navigateTo({ url: '/pages/children/children' })
  }

  return (
    <View className="page child-select">
      <View className="select-head">
        <View className="select-title">今天看谁的作业？</View>
        <View className="select-sub">孩子的错题本和批改记录是分开的</View>
      </View>

      <View className="select-list">
        {students.map((s) => (
          <View
            key={s.id}
            className={`kid ${activeId === s.id ? 'kid-on' : ''}`}
            onClick={() => choose(s)}
          >
            <View className="kid-avatar">{s.name.slice(0, 1)}</View>
            <View className="kid-main">
              <View className="kid-name">{s.name}</View>
              <View className="kid-meta">
                <Text>{gradeLabel(s.grade)}</Text>
                <Text className="kid-dot">·</Text>
                <Text>{s.owner ? '我创建的' : '家人共享'}</Text>
              </View>
            </View>
            <View className="kid-check">{activeId === s.id ? '✓' : ''}</View>
          </View>
        ))}
      </View>

      <View className="select-foot" onClick={goManage}>
        添加孩子，或用编号绑定家人共享的孩子 →
      </View>
    </View>
  )
}
