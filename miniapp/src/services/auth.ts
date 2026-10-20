import Taro from '@tarojs/taro'
import { getToken, request, setToken } from './request'
import { userStore } from '@/store/user'
import type { LoginVo, StudentVo } from '@/types/api'

const DEV_IDENTITY_KEY = 'dev_identity'

/**
 * 取微信登录凭证。
 * 小程序端用 Taro.login；H5 下该接口不可用，会走开发登录分支。
 */
function wxCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    Taro.login({
      success: (res) => {
        if (res.code) {
          resolve(res.code)
        } else {
          reject(new Error('未获取到登录凭证'))
        }
      },
      fail: () => reject(new Error('微信登录失败，请重试')),
    })
  })
}

export interface LoginParams {
  grade?: number
  studentName?: string
}

/**
 * 登录。
 *
 * 开发期便利：H5 端没有 wx.login，页面可在 storage 里写一个 dev_identity，
 * 后端开启 WX_DEV_LOGIN_ENABLED 时会用它直接登录。生产构建不会带这个值，
 * 一定走真实的 code 换 openid。
 */
export async function login(params: LoginParams = {}): Promise<LoginVo> {
  let devIdentity = ''
  if (process.env.TARO_ENV === 'h5') {
    devIdentity = Taro.getStorageSync(DEV_IDENTITY_KEY) || 'dev-local'
  }

  const code = devIdentity ? '' : await wxCode()

  return request<LoginVo>({
    url: '/api/v1/auth/wx-login',
    method: 'POST',
    auth: false,
    data: { code, devIdentity, ...params },
  })
}

export async function logout(): Promise<void> {
  await request<void>({ url: '/api/v1/auth/logout', method: 'POST', showError: false })
}

/** 仅开发用：切换 H5 调试身份，用于验证多账号与越权场景。 */
export function setDevIdentity(identity: string): void {
  Taro.setStorageSync(DEV_IDENTITY_KEY, identity)
}

/**
 * 确保已登录，未登录则静默登录一次。
 * 每个页面 onShow 时调用，避免把登录流程散落到各处。
 */
export async function ensureLogin(params: LoginParams = {}): Promise<LoginVo | null> {
  if (getToken()) {
    return null
  }
  const result = await login(params)
  setToken(result.token)
  userStore.save(result.user, result.students)
  return result
}

/**
 * 确保已选中当前孩子。
 *
 * 多孩必选门槛：有多个孩子且还没选时，跳去选择页并返回 null ——
 * 调用方应当立刻中止本次加载（数据没有孩子维度就没法过滤）。
 * 单孩用户自动选中，永远不感知这一步。
 */
export async function ensureActiveStudent(): Promise<StudentVo | null> {
  const active = userStore.getActiveStudent()
  if (active) {
    return active
  }
  if (userStore.getStudents().length > 1) {
    Taro.navigateTo({ url: '/pages/child-select/child-select' })
    return null
  }
  return null
}
