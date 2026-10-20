import Taro from '@tarojs/taro'
import type { StudentVo, UserVo } from '@/types/api'

/**
 * 用户态。
 *
 * 故意不引状态库：全应用只有「当前用户 + 孩子列表 + 当前孩子」这一份共享状态，
 * 用模块变量 + 本地存储就够了。加状态库反而多一层要维护的东西。
 * 注意 store 只在内存里，页面每次 onShow 都应调用 refresh() 重新拉取，
 * 否则改了昵称这类操作在别的页面看不到。
 *
 * 多孩模型：数据主权在孩子身上。activeStudent 是当前操作的孩子，
 * 错题本/批改记录/统计都按它过滤；家人绑定的孩子与创建者共享同一份数据。
 */

const USER_KEY = 'auth_user'
const STUDENTS_KEY = 'auth_students'
const ACTIVE_STUDENT_KEY = 'auth_active_student'

let user: UserVo | null = null
let students: StudentVo[] = []
let activeStudent: StudentVo | null = null
const listeners = new Set<() => void>()

function notify(): void {
  listeners.forEach((fn) => fn())
}

function loadFromStorage(): void {
  user = Taro.getStorageSync(USER_KEY) || null
  students = Taro.getStorageSync(STUDENTS_KEY) || []
  const activeId = Taro.getStorageSync(ACTIVE_STUDENT_KEY) as number
  activeStudent = students.find((s) => s.id === activeId) || null
}

export const userStore = {
  getUser(): UserVo | null {
    if (user === null && Taro.getStorageSync(USER_KEY)) {
      loadFromStorage()
    }
    return user
  },

  /** 可访问的全部孩子（创建的 + 绑定的） */
  getStudents(): StudentVo[] {
    if (students.length === 0 && Taro.getStorageSync(STUDENTS_KEY)) {
      loadFromStorage()
    }
    return students
  },

  getActiveStudent(): StudentVo | null {
    if (activeStudent === null && Taro.getStorageSync(ACTIVE_STUDENT_KEY)) {
      loadFromStorage()
    }
    return activeStudent
  },

  /** 登录成功后写入。active 解析规则：单孩自动选中；多孩尊重上次选择，否则留空等选择页。 */
  save(nextUser: UserVo, nextStudents: StudentVo[]): void {
    user = nextUser
    students = nextStudents
    const savedId = Taro.getStorageSync(ACTIVE_STUDENT_KEY) as number
    activeStudent = nextStudents.find((s) => s.id === savedId) || null
    if (nextStudents.length === 1) {
      activeStudent = nextStudents[0]
    }
    Taro.setStorageSync(USER_KEY, nextUser)
    Taro.setStorageSync(STUDENTS_KEY, nextStudents)
    if (activeStudent) {
      Taro.setStorageSync(ACTIVE_STUDENT_KEY, activeStudent.id)
    } else {
      Taro.removeStorageSync(ACTIVE_STUDENT_KEY)
    }
    notify()
  },

  /** 孩子列表变动后整体刷新（添加/绑定/解绑之后），当前选择尽量保持。 */
  refreshStudents(nextStudents: StudentVo[]): void {
    students = nextStudents
    activeStudent = nextStudents.find((s) => s.id === activeStudent?.id) || null
    Taro.setStorageSync(STUDENTS_KEY, nextStudents)
    if (activeStudent) {
      Taro.setStorageSync(ACTIVE_STUDENT_KEY, activeStudent.id)
    } else {
      Taro.removeStorageSync(ACTIVE_STUDENT_KEY)
    }
    notify()
  },

  /** 切换当前孩子。全局数据（错题本/统计）都会跟着变。 */
  setActiveStudent(id: number): StudentVo | null {
    const next = students.find((s) => s.id === id) || null
    if (!next) return null
    activeStudent = next
    Taro.setStorageSync(ACTIVE_STUDENT_KEY, id)
    notify()
    return next
  },

  clear(): void {
    user = null
    students = []
    activeStudent = null
    Taro.removeStorageSync(USER_KEY)
    Taro.removeStorageSync(STUDENTS_KEY)
    Taro.removeStorageSync(ACTIVE_STUDENT_KEY)
    notify()
  },

  subscribe(fn: () => void): () => void {
    listeners.add(fn)
    return () => listeners.delete(fn)
  },
}
