import Taro from '@tarojs/taro'
import type { StudentVo, UserVo } from '@/types/api'

/**
 * 用户态。
 *
 * 故意不引状态库：全应用只有"当前用户 + 当前孩子"这一份共享状态，
 * 用模块变量 + 本地存储就够了。加状态库反而多一层要维护的东西。
 * 注意 store 只在内存里，页面每次 onShow 都应调用 refresh() 重新拉取，
 * 否则改了昵称这类操作在别的页面看不到。
 */

const USER_KEY = 'auth_user'
const STUDENT_KEY = 'auth_student'

let user: UserVo | null = null
let student: StudentVo | null = null
const listeners = new Set<() => void>()

function notify(): void {
  listeners.forEach((fn) => fn())
}

export const userStore = {
  getUser(): UserVo | null {
    if (!user) {
      user = Taro.getStorageSync(USER_KEY) || null
    }
    return user
  },

  getStudent(): StudentVo | null {
    if (!student) {
      student = Taro.getStorageSync(STUDENT_KEY) || null
    }
    return student
  },

  /** 登录成功后写入 */
  save(nextUser: UserVo, nextStudent: StudentVo): void {
    user = nextUser
    student = nextStudent
    Taro.setStorageSync(USER_KEY, nextUser)
    Taro.setStorageSync(STUDENT_KEY, nextStudent)
    notify()
  },

  clear(): void {
    user = null
    student = null
    Taro.removeStorageSync(USER_KEY)
    Taro.removeStorageSync(STUDENT_KEY)
    notify()
  },

  subscribe(fn: () => void): () => void {
    listeners.add(fn)
    return () => listeners.delete(fn)
  },
}
