import { Image, Input, Picker, Text, View } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureActiveStudent, ensureLogin } from '@/services/auth'
import { uploadTaskImage } from '@/services/file'
import { checkInTask } from '@/services/task-checkin'
import { archiveTask, createTask, taskCompletions, taskToday } from '@/services/task'
import type { StudentVo, TaskCompletionVo, TaskCompletionsVo, TaskItemVo, TaskTodayVo } from '@/types/api'
import './tasks.scss'

type Mode = 'today' | 'history'
type Range = 'day' | 'week' | 'month'

function fmt(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function parse(s: string): Date {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function shift(s: string, range: Range, delta: number): string {
  const d = parse(s)
  if (range === 'day') d.setDate(d.getDate() + delta)
  else if (range === 'week') d.setDate(d.getDate() + delta * 7)
  else {
    const day = d.getDate()
    d.setDate(1)
    d.setMonth(d.getMonth() + delta)
    const dim = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
    d.setDate(Math.min(day, dim))
  }
  return fmt(d)
}

/**
 * 每日任务 tab。
 * 今日视图：点行打卡（可选拍照凭证），再点撤销，长按收起；
 * 历史视图：按天/周/月查打卡记录，凭证图可点开看大图。
 */
export default function Tasks() {
  const [student, setStudent] = useState<StudentVo | null>(null)
  const [data, setData] = useState<TaskTodayVo | null>(null)
  const [busy, setBusy] = useState(false)
  const [justDone, setJustDone] = useState<number | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [points, setPoints] = useState('5')
  /** 任务详情图：本地预览路径 + 服务端文件 id（选图即上传） */
  const [proofLocal, setProofLocal] = useState('')
  const [proofFileId, setProofFileId] = useState<number | null>(null)

  const [mode, setMode] = useState<Mode>('today')
  const [range, setRange] = useState<Range>('day')
  const [anchor, setAnchor] = useState(fmt(new Date()))
  const [history, setHistory] = useState<TaskCompletionsVo | null>(null)

  useDidShow(() => {
    void load()
  })

  async function load() {
    try {
      await ensureLogin()
      const active = await ensureActiveStudent()
      if (!active) return
      setStudent(active)
      const t = await taskToday(active.id)
      setData(t)
      if (mode === 'history') {
        const h = await taskCompletions(active.id, range, anchor)
        setHistory(h)
      }
    } catch {
      // 请求层已 toast
    }
  }

  async function loadHistory(r: Range = range, a: string = anchor) {
    if (!student) return
    try {
      const h = await taskCompletions(student.id, r, a)
      setHistory(h)
    } catch {
      // 请求层已 toast
    }
  }

  async function switchMode(m: Mode) {
    setMode(m)
    if (m === 'history' && !history) {
      setAnchor(fmt(new Date()))
      await loadHistory(range, fmt(new Date()))
    }
  }

  async function switchRange(r: Range) {
    setRange(r)
    setAnchor(fmt(new Date()))
    await loadHistory(r, fmt(new Date()))
  }

  async function shiftAnchor(delta: number) {
    const next = shift(anchor, range, delta)
    setAnchor(next)
    await loadHistory(range, next)
  }

  /** 日期选择器回调：选中的具体日期/月份直接作为新的区间锚点。 */
  async function jumpTo(value: string) {
    const next = range === 'month' ? `${value}-01` : value
    setAnchor(next)
    await loadHistory(range, next)
  }

  async function toggle(item: TaskItemVo) {
    if (busy) return
    setBusy(true)
    try {
      const r = await checkInTask(item)
      if (r === 'done' && !item.completed) {
        setJustDone(item.id)
        setTimeout(() => setJustDone(null), 900)
      }
      if (r === 'done' || r === 'undone') {
        await load()
        await loadHistory()
      }
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  async function remove(item: TaskItemVo) {
    const confirm = await Taro.showModal({
      title: '收起这个任务',
      content: `「${item.title}」不再出现在每天的任务里，历史积分不受影响。`,
    })
    if (!confirm.confirm) return
    try {
      await archiveTask(item.id)
      Taro.showToast({ title: '已收起', icon: 'none' })
      await load()
    } catch {
      // 请求层已 toast
    }
  }

  /** 选任务详情图：相册/相机选一张，选完即上传（内容在图上的任务）。 */
  async function pickImage() {
    if (busy) return
    try {
      const media = await Taro.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: ['camera', 'album'],
        sizeType: ['compressed'],
      })
      const filePath = media.tempFiles?.[0]?.tempFilePath
      if (!filePath) return
      setBusy(true)
      Taro.showLoading({ title: '上传图片中', mask: true })
      const up = await uploadTaskImage(filePath)
      Taro.hideLoading()
      setProofLocal(filePath)
      setProofFileId(up.fileId)
    } catch {
      Taro.hideLoading()
      // 请求层已 toast；选图取消静默
    } finally {
      setBusy(false)
    }
  }

  async function add() {
    if (busy || !student) return
    const name = title.trim()
    const p = parseInt(points, 10)
    if (!name) {
      Taro.showToast({ title: '先写任务名', icon: 'none' })
      return
    }
    if (!p || p < 1 || p > 999) {
      Taro.showToast({ title: '积分填 1~999', icon: 'none' })
      return
    }
    setBusy(true)
    try {
      await createTask({
        studentId: student.id,
        title: name,
        points: p,
        imageFileId: proofFileId ?? undefined,
      })
      setTitle('')
      setPoints('5')
      setProofLocal('')
      setProofFileId(null)
      setFormOpen(false)
      await load()
      Taro.showToast({ title: '已加入每天的任务', icon: 'none' })
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  function goPoints() {
    if (student) {
      Taro.navigateTo({ url: `/pages/points/points?studentId=${student.id}` })
    }
  }

  function preview(rec: TaskCompletionVo) {
    if (rec.imageUrl) {
      Taro.previewImage({ urls: [rec.imageUrl] })
    }
  }

  function rangeLabel(): string {
    if (!history) return ''
    const s = parse(history.start)
    const e = parse(history.end)
    const today = fmt(new Date())
    const md = (d: Date) => `${d.getMonth() + 1}.${d.getDate()}`
    if (range === 'day') return `${md(s)}${anchor === today ? ' · 今天' : ''}`
    if (range === 'week') return `${md(s)} - ${md(e)}`
    return `${s.getFullYear()} 年 ${s.getMonth() + 1} 月`
  }

  const s = data?.summary
  const kidName = student?.name

  return (
    <View className="page tasks">
      {kidName && (
        <View
          className="kid-strip"
          onClick={() => Taro.navigateTo({ url: '/pages/child-select/child-select' })}
        >
          <Text>「{kidName}」今天要做的事</Text>
          <Text className="kid-strip-op">切换孩子</Text>
        </View>
      )}

      <View className="seg">
        <View className={`seg-btn ${mode === 'today' ? 'seg-on' : ''}`} onClick={() => switchMode('today')}>
          今日
        </View>
        <View className={`seg-btn ${mode === 'history' ? 'seg-on' : ''}`} onClick={() => switchMode('history')}>
          历史
        </View>
      </View>

      {mode === 'today' && (
        <>
          {s && (
            <View className="card score-card" onClick={goPoints}>
              <View className="score-main">
                <Text className="score-num">{s.balance}</Text>
                <Text className="score-unit">分</Text>
              </View>
              <View className="score-side">
                <View className="score-line">
                  今天 +{s.todayEarned} · 完成 {s.todayCompleted}/{s.todayTotal}
                </View>
                <View className="score-ops">积分明细与兑换 ›</View>
              </View>
            </View>
          )}

          <View className="task-list">
            {(data?.tasks ?? []).map((item) => (
              <View
                key={item.id}
                className={`task-row ${item.completed ? 'task-done' : ''}`}
                onClick={() => toggle(item)}
                onLongPress={() => remove(item)}
              >
                <View className={`task-check ${item.completed ? 'task-check-on' : ''} ${justDone === item.id ? 'pop' : ''}`}>
                  {item.completed ? '✓' : ''}
                </View>
                <View className="task-body">
                  <View className="task-title">{item.title}</View>
                  {item.completed && item.completedAt && (
                    <View className="task-time">{item.completedAt} 完成</View>
                  )}
                </View>
                {item.imageUrl && (
                  <Image
                    className="task-thumb"
                    src={item.imageUrl}
                    mode="aspectFill"
                    onClick={(e) => {
                      e.stopPropagation()
                      Taro.previewImage({ urls: [item.imageUrl!] })
                    }}
                  />
                )}
                <View className={`task-points ${item.completed ? 'task-points-done' : ''}`}>
                  +{item.points}
                </View>
              </View>
            ))}
            {data && data.tasks.length === 0 && (
              <View className="empty">还没有每天要做的事{'\n'}点下面的「添加任务」开始</View>
            )}
          </View>

          {formOpen ? (
            <View className="card form-card">
              <Input
                className="form-input"
                value={title}
                maxlength={64}
                placeholder="任务名，如「口算练习一页」"
                onInput={(e) => setTitle(e.detail.value)}
              />
              <View className="form-row2">
                <Input
                  className="form-input form-input-points"
                  value={points}
                  type="number"
                  maxlength={3}
                  placeholder="积分"
                  onInput={(e) => setPoints(e.detail.value)}
                />
                <Text className="form-points-label">分/次</Text>
                <View className={`btn-primary form-add ${busy ? 'disabled' : ''}`} onClick={busy ? undefined : add}>
                  添加
                </View>
              </View>
              {/* 任务详情图（可选）：内容在图上的任务，如作业页照片 */}
              <View className="form-image-row">
                {proofLocal ? (
                  <View className="form-image-box" onClick={() => Taro.previewImage({ urls: [proofLocal] })}>
                    <Image className="form-image" src={proofLocal} mode="aspectFill" />
                    <View
                      className="form-image-remove"
                      onClick={(e) => {
                        e.stopPropagation()
                        setProofLocal('')
                        setProofFileId(null)
                      }}
                    >
                      ×
                    </View>
                  </View>
                ) : (
                  <View className="form-image-add" onClick={busy ? undefined : pickImage}>
                    <Text className="form-image-add-txt">＋ 任务配图{'\n'}(内容在图上时)</Text>
                  </View>
                )}
              </View>
              <View className="form-cancel muted" onClick={() => setFormOpen(false)}>
                收起
              </View>
            </View>
          ) : (
            <View className="btn-ghost add-entry" onClick={() => setFormOpen(true)}>
              ＋ 添加任务
            </View>
          )}

          <View className="tips muted">点一行打卡 · 配了图的任务可先看图 · 长按收起</View>
        </>
      )}

      {mode === 'history' && (
        <View className="history">
          <View className="range-tabs">
            <View className={`range-btn ${range === 'day' ? 'range-on' : ''}`} onClick={() => switchRange('day')}>
              天
            </View>
            <View className={`range-btn ${range === 'week' ? 'range-on' : ''}`} onClick={() => switchRange('week')}>
              周
            </View>
            <View className={`range-btn ${range === 'month' ? 'range-on' : ''}`} onClick={() => switchRange('month')}>
              月
            </View>
          </View>

          <View className="hist-nav">
            <View className="hist-arrow" onClick={() => shiftAnchor(-1)}>‹</View>
            <Picker
              mode="date"
              fields={range === 'month' ? 'month' : 'day'}
              value={range === 'month' ? anchor.slice(0, 7) : anchor}
              onChange={(e) => jumpTo(e.detail.value)}
            >
              <View className="hist-label">
                {rangeLabel()}
                <Text className="hist-caret">▾</Text>
              </View>
            </Picker>
            <View className="hist-arrow" onClick={() => shiftAnchor(1)}>›</View>
          </View>

          {history && (
            <View className="hist-summary">
              共 {history.summary.count} 次打卡 · +{history.summary.earned} 分
            </View>
          )}

          <View className="hist-list">
            {(history?.items ?? []).map((rec) => (
              <View key={rec.id} className="hist-row" onClick={() => preview(rec)}>
                {rec.imageUrl ? (
                  <Image className="hist-thumb" src={rec.imageUrl} mode="aspectFill" />
                ) : (
                  <View className="hist-thumb hist-thumb-empty">
                    <Text className="hist-thumb-txt">无图</Text>
                  </View>
                )}
                <View className="hist-body">
                  <View className="hist-title">{rec.taskTitle}</View>
                  <View className="hist-time">
                    {rec.completeDate.slice(5).replace('-', '月')}日 {rec.completedAt}
                  </View>
                </View>
                <View className="hist-points">+{rec.pointsAwarded}</View>
              </View>
            ))}
            {history && history.items.length === 0 && (
              <View className="empty small">这段时间没有打卡记录</View>
            )}
          </View>
        </View>
      )}
    </View>
  )
}
