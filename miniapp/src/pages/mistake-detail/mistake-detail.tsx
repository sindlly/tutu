import { View, Text, Image } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { useState } from 'react'
import { getMistake, regenerateCard, reviewMistake, updateMistakeStatus } from '@/services/mistake'
import { useDidShowOnce } from '@/utils/hooks'
import { formatDateTime, masteryStars, subjectLabel } from '@/utils/format'
import { MistakeStatus } from '@/types/api'
import type { MistakeDetailVo } from '@/types/api'
import './mistake-detail.scss'

export default function MistakeDetail() {
  const router = useRouter()
  const id = Number(router.params.id)

  const [detail, setDetail] = useState<MistakeDetailVo | null>(null)
  const [busy, setBusy] = useState(false)
  const [imagePreview, setImagePreview] = useState<string | null>(null)

  useDidShowOnce(() => {
    void load()
  })

  async function load() {
    try {
      setDetail(await getMistake(id))
    } catch {
      Taro.showToast({ title: '加载失败', icon: 'none' })
    }
  }

  async function onReview(correct: boolean) {
    setBusy(true)
    try {
      const next = await reviewMistake(id, correct)
      setDetail(next)
      const level = next.mistake.masteryLevel
      if (correct && next.mistake.status === MistakeStatus.MASTERED) {
        Taro.showToast({ title: '这题已掌握，从待复习里移除', icon: 'none', duration: 2500 })
      } else {
        Taro.showToast({
          title: correct ? `掌握度提升到 ${level} 级` : '没关系，明天再练一次',
          icon: 'none',
        })
      }
    } catch {
    } finally {
      setBusy(false)
    }
  }

  async function onMastered() {
    setBusy(true)
    try {
      await updateMistakeStatus(id, MistakeStatus.MASTERED)
      Taro.showToast({ title: '已标记为掌握', icon: 'none' })
      setTimeout(() => Taro.navigateBack(), 700)
    } catch {
    } finally {
      setBusy(false)
    }
  }

  async function onRemove() {
    const confirm = await Taro.showModal({
      title: '移出错题本',
      content: '移出后不再出现在复习列表里，确定吗？',
    })
    if (!confirm.confirm) return
    setBusy(true)
    try {
      await updateMistakeStatus(id, MistakeStatus.REMOVED)
      Taro.showToast({ title: '已移出', icon: 'none' })
      setTimeout(() => Taro.navigateBack(), 700)
    } catch {
    } finally {
      setBusy(false)
    }
  }

  async function onRegenerateCard() {
    setBusy(true)
    try {
      await regenerateCard(id)
      await load()
      Taro.showToast({ title: '卡片已重新生成', icon: 'none' })
    } catch {
    } finally {
      setBusy(false)
    }
  }

  if (!detail) {
    return (
      <View className="page">
        <View className="empty">加载中…</View>
      </View>
    )
  }

  const { mistake } = detail

  return (
    <View className="page md">
      {mistake.cardImageUrl ? (
        <Image
          className="card-img"
          src={mistake.cardImageUrl}
          mode="widthFix"
          onClick={() => setImagePreview(mistake.cardImageUrl || null)}
          onError={() => Taro.showToast({ title: '卡片图已过期，可下拉刷新重试', icon: 'none' })}
        />
      ) : (
        <View className="card">
          <Text className="md-stem">{mistake.stem || '（题干缺失）'}</Text>
          <View className="btn-ghost mt24" onClick={busy ? undefined : onRegenerateCard}>
            生成错题卡片
          </View>
        </View>
      )}

      <View className="card">
        <View className="row-between">
          <Text className="title">题目信息</Text>
          <Text className="tag">{subjectLabel(mistake.subject)}</Text>
        </View>

        <View className="md-line">
          <Text className="md-label">题干</Text>
          <Text className="md-value">{mistake.stem || '—'}</Text>
        </View>
        <View className="md-line">
          <Text className="md-label">上次答</Text>
          <Text className="md-value bad">{mistake.studentAnswer || '—'}</Text>
        </View>
        {detail.correctAnswer && (
          <View className="md-line">
            <Text className="md-label">正确答案</Text>
            <Text className="md-value ok">{detail.correctAnswer}</Text>
          </View>
        )}
        {detail.wrongReason && (
          <View className="md-block">
            <Text className="md-label">错因</Text>
            <Text className="md-text">{detail.wrongReason}</Text>
          </View>
        )}
        {detail.analysis && (
          <View className="md-block">
            <Text className="md-label">解析</Text>
            <Text className="md-text">{detail.analysis}</Text>
          </View>
        )}
        {detail.knowledgePoints.length > 0 && (
          <View className="md-tags">
            {detail.knowledgePoints.map((point) => (
              <View key={point} className="tag">
                {point}
              </View>
            ))}
          </View>
        )}
      </View>

      <View className="card">
        <View className="row-between">
          <Text className="title">掌握情况</Text>
          <Text className="mastery">{masteryStars(mistake.masteryLevel)}</Text>
        </View>
        <View className="muted mt8">
          已复习 {mistake.reviewCount} 次 · 下次 {formatDateTime(mistake.nextReviewAt) || '—'}
        </View>

        <View className="review-title mt24">现在再做一遍，做对了吗？</View>
        <View className="review-actions">
          <View
            className={`review-btn good ${busy ? 'disabled' : ''}`}
            onClick={busy ? undefined : () => onReview(true)}
          >
            做对了
          </View>
          <View
            className={`review-btn bad ${busy ? 'disabled' : ''}`}
            onClick={busy ? undefined : () => onReview(false)}
          >
            没做对
          </View>
        </View>
        <View className="muted review-hint">
          连续做对会逐步拉长复习间隔（1→2→4→7→15→30 天），做错会降一级。
        </View>
      </View>

      {detail.cropImageUrl && (
        <View className="card">
          <Text className="title">原图位置</Text>
          <View className="muted mt8">从作业照片里裁出来的原始书写痕迹</View>
          <Image
            className="crop-img mt16"
            src={detail.cropImageUrl}
            mode="widthFix"
            onClick={() => setImagePreview(detail.cropImageUrl || null)}
          />
        </View>
      )}

      {detail.reviews.length > 0 && (
        <View className="card">
          <Text className="title">复习记录</Text>
          <View className="mt16">
            {detail.reviews.slice(0, 8).map((log) => (
              <View key={log.id} className="log">
                <Text className={log.correct ? 'log-ok' : 'log-bad'}>
                  {log.correct ? '✓ 做对' : '✗ 做错'}
                </Text>
                <Text className="muted">{formatDateTime(log.reviewedAt)}</Text>
              </View>
            ))}
          </View>
        </View>
      )}

      <View className="md-actions">
        <View className="btn-ghost" onClick={busy ? undefined : onMastered}>
          已掌握，不再复习
        </View>
        <View className="md-remove" onClick={busy ? undefined : onRemove}>
          移出错题本
        </View>
      </View>

      {imagePreview && (
        <View className="img-mask" onClick={() => setImagePreview(null)}>
          <Image className="img-full" src={imagePreview} mode="widthFix" />
          <View className="img-hint">点击任意处关闭</View>
        </View>
      )}
    </View>
  )
}
