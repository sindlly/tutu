import { View, Text, Input } from '@tarojs/components'
import Taro, { useDidShow } from '@tarojs/taro'
import { useState } from 'react'
import { ensureLogin } from '@/services/auth'
import { bindStudent, createStudent, listStudents, unbindStudent } from '@/services/student'
import { userStore } from '@/store/user'
import { gradeLabel } from '@/utils/format'
import { GRADES } from '@/types/api'
import type { StudentVo } from '@/types/api'
import './children.scss'

/**
 * 孩子管理：添加、用编号绑定（家人共享）、切换当前、解绑。
 * 当前孩子全局生效 —— 错题本、批改记录、统计都跟着切换。
 */
export default function Children() {
  const [students, setStudents] = useState<StudentVo[]>([])
  const [activeId, setActiveId] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)

  // 添加孩子表单
  const [name, setName] = useState('')
  const [grade, setGrade] = useState(3)
  // 绑定表单
  const [code, setCode] = useState('')
  const [codeFocus, setCodeFocus] = useState(false)

  useDidShow(() => {
    void load()
  })

  async function load() {
    try {
      await ensureLogin()
      const list = await listStudents()
      userStore.refreshStudents(list)
      setStudents(list)
      setActiveId(userStore.getActiveStudent()?.id ?? null)
    } catch {
      // 请求层已 toast
    }
  }

  async function add() {
    if (busy) return
    if (!name.trim()) {
      Taro.showToast({ title: '先写个称呼，比如「大宝」', icon: 'none' })
      return
    }
    setBusy(true)
    try {
      const created = await createStudent({ name: name.trim(), grade })
      await load()
      setName('')
      Taro.showToast({ title: `${created.name} 的档案建好了`, icon: 'none' })
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  async function bind() {
    if (busy) return
    const trimmed = code.trim().toUpperCase()
    if (trimmed.length !== 6) {
      Taro.showToast({ title: '孩子编号是 6 位', icon: 'none' })
      return
    }
    setBusy(true)
    try {
      const bound = await bindStudent(trimmed)
      await load()
      setCode('')
      Taro.showToast({ title: `已绑定「${bound.name}」`, icon: 'none' })
    } catch {
      // 请求层已 toast
    } finally {
      setBusy(false)
    }
  }

  async function removeBind(student: StudentVo) {
    if (student.owner) {
      Taro.showToast({ title: '自己创建的孩子不用解绑', icon: 'none' })
      return
    }
    const confirm = await Taro.showModal({
      title: '解除绑定',
      content: `解绑后就看不到「${student.name}」的错题数据了。`,
    })
    if (!confirm.confirm) return
    try {
      await unbindStudent(student.id)
      await load()
      Taro.showToast({ title: '已解除', icon: 'none' })
    } catch {
      // 请求层已 toast
    }
  }

  function switchTo(student: StudentVo) {
    if (student.id === activeId) return
    userStore.setActiveStudent(student.id)
    setActiveId(student.id)
    Taro.showToast({ title: `已切换到「${student.name}」`, icon: 'none' })
  }

  async function copyCode(student: StudentVo) {
    if (!student.bindCode) return
    try {
      await Taro.setClipboardData({ data: student.bindCode })
    } catch {
      // H5 端可能不支持，提示里直接带上编号
    }
    Taro.showModal({
      title: `「${student.name}」的编号`,
      content: `把编号 ${student.bindCode} 发给家人，对方在「添加孩子」里选择「用编号绑定」，就能看到同一个孩子的错题本。`,
      showCancel: false,
    })
  }

  return (
    <View className="page children">
      <View className="list-head">我的孩子</View>

      <View className="kid-list">
        {students.map((s) => (
          <View key={s.id} className={`kid-row ${s.id === activeId ? 'kid-row-on' : ''}`}>
            <View className="kid-row-main" onClick={() => switchTo(s)}>
              <View className="kid-row-avatar">{s.name.slice(0, 1)}</View>
              <View className="kid-row-info">
                <View className="kid-row-name">
                  {s.name}
                  {s.id === activeId && <Text className="kid-row-now">当前</Text>}
                </View>
                <View className="kid-row-meta">
                  {gradeLabel(s.grade)} · {s.owner ? '我创建的' : '家人共享'}
                </View>
              </View>
            </View>
            <View className="kid-row-ops">
              <Text className="kid-row-op" onClick={() => copyCode(s)}>
                编号 {s.bindCode}
              </Text>
              {!s.owner && (
                <Text className="kid-row-op kid-row-unbind" onClick={() => removeBind(s)}>
                  解绑
                </Text>
              )}
            </View>
          </View>
        ))}
      </View>

      <View className="card form-card">
        <View className="form-title">添加孩子</View>
        <View className="form-row">
          <Input
            className="form-input"
            value={name}
            maxlength={12}
            placeholder="称呼，比如「大宝」"
            onInput={(e) => setName(e.detail.value)}
          />
        </View>
        <View className="form-label">年级</View>
        <View className="grade-grid">
          {GRADES.map((g) => (
            <View
              key={g.value}
              className={`grade-cell ${grade === g.value ? 'grade-cell-on' : ''}`}
              onClick={() => setGrade(g.value)}
            >
              {g.label.replace('年级', '')}
            </View>
          ))}
        </View>
        <View className={`btn-primary form-btn ${busy ? 'disabled' : ''}`} onClick={busy ? undefined : add}>
          建立档案
        </View>
      </View>

      <View className="card form-card">
        <View className="form-title">用编号绑定</View>
        <View className="form-sub">
          家人（另一位家长、祖辈、家教）把 6 位编号发给你，绑定后共享同一个孩子的错题本。
        </View>
        <View className="form-row">
          <Input
            className={`form-input form-code ${codeFocus ? 'form-code-focus' : ''}`}
            value={code}
            maxlength={6}
            placeholder="输入 6 位编号"
            onFocus={() => setCodeFocus(true)}
            onBlur={() => setCodeFocus(false)}
            onInput={(e) => setCode(e.detail.value.toUpperCase())}
          />
        </View>
        <View className={`btn-ghost form-btn ${busy ? 'disabled' : ''}`} onClick={busy ? undefined : bind}>
          绑定
        </View>
      </View>
    </View>
  )
}
