import { View, Text, Image } from '@tarojs/components'
import Taro, { useRouter } from '@tarojs/taro'
import { useState } from 'react'
import { getResult } from '@/services/homework'
import { useDidShowOnce } from '@/utils/hooks'
import { questionTypeLabel } from '@/utils/format'
import type { QuestionVo, SubmitDetailVo } from '@/types/api'
import './result.scss'

type Filter = 'all' | 'wrong' | 'check'

const FILTERS: { key: Filter; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'wrong', label: '错题' },
  { key: 'check', label: '待核对' },
]

export default function Result() {
  const router = useRouter()
  const submitId = Number(router.params.submitId)

  const [detail, setDetail] = useState<SubmitDetailVo | null>(null)
  const [filter, setFilter] = useState<Filter>('all')
  const [expanded, setExpanded] = useState<number | null>(null)
  const [failed, setFailed] = useState(false)

  useDidShowOnce(() => {
    void load()
  })

  async function load() {
    try {
      setDetail(await getResult(submitId))
    } catch {
      setFailed(true)
    }
  }

  function previewImage(index: number) {
    if (!detail) return
    Taro.previewImage({ current: detail.images[index], urls: detail.images })
  }

  if (failed) {
    return (
      <View className="page">
        <View className="empty">结果加载失败，请返回重试</View>
      </View>
    )
  }

  if (!detail) {
    return (
      <View className="page">
        <View className="empty">加载中…</View>
      </View>
    )
  }

  const { submit, images, questions } = detail
  const visible = questions.filter((q) => {
    if (filter === 'wrong') return !q.correct
    if (filter === 'check') return q.needsCheck
    return true
  })
  const checkCount = questions.filter((q) => q.needsCheck).length

  return (
    <View className="page result">
      <View className="card summary">
        <View className="summary-row">
          <View className="summary-item">
            <Text className="summary-num">{submit.totalCount ?? 0}</Text>
            <Text className="summary-label">题目</Text>
          </View>
          <View className="summary-item">
            <Text className="summary-num ok">{submit.rightCount ?? 0}</Text>
            <Text className="summary-label">做对</Text>
          </View>
          <View className="summary-item">
            <Text className="summary-num bad">{submit.wrongCount ?? 0}</Text>
            <Text className="summary-label">做错</Text>
          </View>
          <View className="summary-item">
            <Text className="summary-num">{submit.score ?? 0}</Text>
            <Text className="summary-label">得分</Text>
          </View>
        </View>
        {checkCount > 0 && (
          <View className="notice mt16">
            有 {checkCount} 道题 AI 不太确定，已在题号旁标出，建议对着原图核对一下。
          </View>
        )}
      </View>

      {images.length > 0 && (
        <View className="card">
          <Text className="title">作业原图</Text>
          <View className="originals mt16">
            {images.map((url, index) => (
              <Image
                key={url}
                className="original-img"
                src={url}
                mode="aspectFill"
                onClick={() => previewImage(index)}
              />
            ))}
          </View>
        </View>
      )}

      <View className="filters">
        {FILTERS.map((item) => (
          <View
            key={item.key}
            className={`filter ${filter === item.key ? 'filter-active' : ''}`}
            onClick={() => setFilter(item.key)}
          >
            {item.label}
          </View>
        ))}
      </View>

      {visible.length === 0 ? (
        <View className="empty">{filter === 'wrong' ? '这一页全对 🎉' : '没有待核对的题目'}</View>
      ) : (
        visible.map((q) => (
          <QuestionItem
            key={q.id}
            question={q}
            expanded={expanded === q.id}
            onToggle={() => setExpanded(expanded === q.id ? null : q.id)}
          />
        ))
      )}

      <View className="btn-ghost mt24" onClick={() => Taro.navigateTo({ url: '/pages/mistakes/mistakes' })}>
        去错题本看看
      </View>

      <View className="muted tips">AI 批改结果仅供参考，重要结论请与老师核对。</View>
    </View>
  )
}

function QuestionItem({
  question,
  expanded,
  onToggle,
}: {
  question: QuestionVo
  expanded: boolean
  onToggle: () => void
}) {
  const [cropPreview, setCropPreview] = useState(false)

  return (
    <View className={`q ${question.correct ? '' : 'q-wrong'}`}>
      <View className="q-head" onClick={onToggle}>
        <View className="q-no">{question.questionNo || `第${question.seq}题`}</View>
        <View className="tag">{questionTypeLabel(question.questionType)}</View>
        {question.needsCheck && <View className="tag tag-check">待核对</View>}
        <View className={`tag ${question.correct ? 'tag-right' : 'tag-wrong'}`}>
          {question.correct ? '对' : '错'}
        </View>
        <View className="q-arrow">{expanded ? '⌃' : '⌄'}</View>
      </View>

      <View className="q-stem">{question.stem || '（未识别到题干）'}</View>

      <View className="q-line">
        <Text className="q-label">我的答案</Text>
        <Text className={question.correct ? 'q-answer' : 'q-answer bad'}>
          {question.studentAnswer || '—'}
        </Text>
      </View>

      {!question.correct && (
        <View className="q-line">
          <Text className="q-label">正确答案</Text>
          <Text className="q-answer ok">{question.correctAnswer || '—'}</Text>
        </View>
      )}

      {expanded && (
        <View className="q-detail">
          {question.correctAnswer && question.correct && (
            <View className="q-line">
              <Text className="q-label">正确答案</Text>
              <Text className="q-answer">{question.correctAnswer}</Text>
            </View>
          )}
          {question.wrongReason && (
            <View className="q-block">
              <Text className="q-label">错因</Text>
              <Text className="q-text">{question.wrongReason}</Text>
            </View>
          )}
          {question.analysis && (
            <View className="q-block">
              <Text className="q-label">解析</Text>
              <Text className="q-text">{question.analysis}</Text>
            </View>
          )}
          {question.knowledgePoints.length > 0 && (
            <View className="q-tags">
              {question.knowledgePoints.map((point) => (
                <View key={point} className="tag">
                  {point}
                </View>
              ))}
            </View>
          )}
          {question.confidence !== undefined && (
            <View className="muted q-conf">
              AI 把握度 {Math.round(question.confidence * 100)}%
            </View>
          )}
          {question.cropImageUrl && (
            <View className="q-crop">
              <Text className="q-label">原图位置</Text>
              <Image
                className="q-crop-img"
                src={question.cropImageUrl}
                mode="widthFix"
                onClick={() => setCropPreview(true)}
              />
            </View>
          )}
        </View>
      )}

      {cropPreview && question.cropImageUrl && (
        <View className="crop-mask" onClick={() => setCropPreview(false)}>
          <Image className="crop-full" src={question.cropImageUrl} mode="widthFix" />
          <View className="crop-hint">点击任意处关闭</View>
        </View>
      )}
    </View>
  )
}
