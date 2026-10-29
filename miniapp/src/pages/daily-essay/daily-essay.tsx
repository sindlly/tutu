import { Text, Textarea, View } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import { generateEssay } from '@/services/essay'
import { withLoading } from '@/services/request'
import type { StudentVo } from '@/types/api'
import './daily-essay.scss'

/**
 * 「每日一话」：用户输入要求（主题/风格/长度），AI 生成贴合孩子年级的小美文。
 * 结果可复制，供家长讲给孩子听或抄进本子。
 */
export default function DailyEssay() {
  const [student, setStudent] = useState<StudentVo | null>(null)
  const [requirements, setRequirements] = useState('')
  const [busy, setBusy] = useState(false)
  const [essay, setEssay] = useState('')
  const [fullscreen, setFullscreen] = useState(false)

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

  async function generate() {
    if (busy || !student) return
    const req = requirements.trim()
    if (!req) {
      Taro.showToast({ title: '先说说想要什么样的文章', icon: 'none' })
      return
    }
    setBusy(true)
    try {
      const res = await withLoading('AI 创作中…', () => generateEssay(student.id, req))
      setEssay(res.text)
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  async function copy() {
    await Taro.setClipboardData({ data: essay })
  }

  return (
    <View className="page essay">
      <View className="essay-hero">
        <Text className="essay-title">每日一话</Text>
        {student && <Text className="essay-sub">会按「{student.name}」的年级来写，讲给孩子听刚刚好</Text>}
      </View>

      <View className="card essay-form">
        <Textarea
          className="essay-input"
          value={requirements}
          maxlength={200}
          placeholder="想要什么样的文章？比如：写一篇关于秋天的短文，语言简单点，孩子要能背下来"
          onInput={(e) => setRequirements(e.detail?.value ?? '')}
        />
        <View className="essay-form-foot">
          <Text className="essay-count">{requirements.length}/200</Text>
          <View className={`btn-primary essay-btn ${busy ? 'disabled' : ''}`} onClick={busy ? undefined : generate}>
            {busy ? '创作中…' : '生成美文'}
          </View>
        </View>
      </View>

      {essay && (
        <View className="card essay-result">
          <View className="essay-text" onClick={() => setFullscreen(true)}>{essay}</View>
          <View className="essay-ops">
            <View className="essay-op" onClick={() => setFullscreen(true)}>
              全屏阅读
            </View>
            <View className="essay-op" onClick={copy}>
              复制全文
            </View>
          </View>
        </View>
      )}

      {!essay && (
        <View className="essay-tips muted">小技巧：写清主题、场合（睡前读/抄写）、想要的感觉，效果更好</View>
      )}

      {/* 全屏沉浸阅读：点正文进，点右上角出 */}
      {fullscreen && essay && (
        <View className="essay-fullscreen">
          <View className="essay-fullscreen-close" onClick={() => setFullscreen(false)}>
            ×
          </View>
          <View className="essay-fullscreen-body">
            <View className="essay-fullscreen-text">{essay}</View>
            <View className="essay-fullscreen-copy" onClick={copy}>
              复制全文
            </View>
          </View>
        </View>
      )}
    </View>
  )
}
