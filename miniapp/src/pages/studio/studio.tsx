import { Text, View } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import Icon from '@/components/Icon'
import type { StudentVo } from '@/types/api'
import './studio.scss'

/**
 * 工作台：AI 能力聚合入口（tabBar 中间圆钮）。
 * 后续新功能都往这里加入口卡。
 */
export default function Studio() {
  const [student, setStudent] = useState<StudentVo | null>(null)

  useDidShow(() => {
    void load()
  })

  async function load() {
    try {
      await ensureLogin()
      const active = await ensureActiveStudent()
      if (!active) return
      setStudent(active)
    } catch {
      // 请求层已 toast
    }
  }

  function goCapture() {
    Taro.navigateTo({ url: '/pages/capture/capture' })
  }

  function goEssay() {
    Taro.navigateTo({ url: '/pages/daily-essay/daily-essay' })
  }

  return (
    <View className="page studio">
      <View className="studio-hero">
        <Text className="studio-title">工作台</Text>
        {student && <Text className="studio-sub">为「{student.name}」准备的 AI 能力</Text>}
      </View>

      <View className="studio-grid">
        <View className="studio-card" onClick={goCapture}>
          <View className="studio-icon studio-icon-capture">
            <Icon name="camera" size={48} color="#faf9f5" />
          </View>
          <View className="studio-body">
            <View className="studio-card-title">作业批改</View>
            <View className="studio-card-desc">拍照上传，对错与得分几秒出</View>
          </View>
          <Text className="studio-arrow">›</Text>
        </View>

        <View className="studio-card" onClick={goEssay}>
          <View className="studio-icon studio-icon-essay">
            <Icon name="pencil" size={48} color="#faf9f5" />
          </View>
          <View className="studio-body">
            <View className="studio-card-title">每日一话</View>
            <View className="studio-card-desc">说出你的要求，AI 写一篇小美文</View>
          </View>
          <Text className="studio-arrow">›</Text>
        </View>

        <View className="studio-more">
          <View className="studio-card studio-card-disabled">
            <View className="studio-icon studio-icon-more">
              <Icon name="sparkles" size={48} color="#87867f" />
            </View>
            <View className="studio-body">
              <View className="studio-card-title studio-card-title-muted">更多 AI 能力</View>
              <View className="studio-card-desc">陆续上线，敬请期待</View>
            </View>
          </View>
        </View>
      </View>
    </View>
  )
}
