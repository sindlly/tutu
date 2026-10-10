import Taro from '@tarojs/taro'
import type { ApiResponse } from '@/types/api'

/**
 * 请求层。
 *
 * 两个关键约定：
 * 1. 登录态是"不透明 token"，统一放 Authorization 头。401 一律清 token 并跳登录页，
 *    避免每个页面各写一份失效处理。
 * 2. 业务错误（code != 0）抛 ApiError，由调用方决定是提示还是忽略；
 *    网络错误单独区分，因为用户看到的话术不一样。
 */

const TOKEN_KEY = 'auth_token'
const USER_KEY = 'auth_user'

/**
 * 后端地址。
 *
 * 开发时指向本机：小程序端要在微信开发者工具里勾选「不校验合法域名」。
 * 上线必须换成已备案的 HTTPS 域名，并在小程序后台把域名加进
 * request / uploadFile 合法域名列表 —— 用 IP 会直接被拒。
 *
 * H5 端跨域访问时需要后端开启 CORS（见后端 app.cors.allowed-origins）。
 */
export const API_BASE = 'http://127.0.0.1:8080'

export class ApiError extends Error {
  readonly code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

export function getToken(): string {
  return Taro.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  Taro.setStorageSync(TOKEN_KEY, token)
}

export function clearAuth(): void {
  Taro.removeStorageSync(TOKEN_KEY)
  Taro.removeStorageSync(USER_KEY)
}

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: unknown
  /** 默认 true；登录接口本身不需要带 token */
  auth?: boolean
  /** 默认 true，失败时自动弹 toast */
  showError?: boolean
}

export async function request<T>(options: RequestOptions): Promise<T> {
  const header: Record<string, string> = { 'Content-Type': 'application/json' }

  if (options.auth !== false) {
    const token = getToken()
    if (token) {
      header.Authorization = `Bearer ${token}`
    }
  }

  let res: Taro.request.SuccessCallbackResult<unknown>
  try {
    res = await Taro.request({
      url: API_BASE + options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      timeout: 60000,
    })
  } catch (err) {
    // 网络层失败：域名没配、后端没起、断网
    throw new ApiError(-1, '网络连接失败，请检查网络后重试')
  }

  const body = res.data as ApiResponse<T>
  if (!body || typeof body.code !== 'number') {
    throw new ApiError(-2, '服务返回格式异常')
  }

  if (body.code === 401) {
    // token 失效：清掉本地登录态，让用户重新登录
    clearAuth()
    throw new ApiError(401, body.msg || '登录已过期，请重新进入小程序')
  }

  if (body.code !== 0) {
    const error = new ApiError(body.code, body.msg || '请求失败')
    if (options.showError !== false) {
      Taro.showToast({ title: error.message, icon: 'none', duration: 2500 })
    }
    throw error
  }

  return body.data
}

/**
 * 上传文件。单独一个函数是因为小程序端必须走 uploadFile，
 * 它不支持 JSON body，且返回结构也不同（data 是字符串，要自己 parse）。
 */
export async function uploadFile(filePath: string, bizType = 1): Promise<unknown> {
  const token = getToken()
  const res = await Taro.uploadFile({
    url: `${API_BASE}/api/v1/files/upload`,
    filePath,
    name: 'file',
    formData: { bizType: String(bizType) },
    header: token ? { Authorization: `Bearer ${token}` } : {},
  })

  if (res.statusCode === 401) {
    clearAuth()
    throw new ApiError(401, '登录已过期，请重新进入小程序')
  }

  let body: ApiResponse<unknown>
  try {
    body = JSON.parse(res.data)
  } catch {
    throw new ApiError(-2, '上传返回格式异常')
  }
  if (!body || body.code !== 0) {
    throw new ApiError(body?.code ?? -2, body?.msg || '上传失败')
  }
  return body.data
}

/** 统一的 loading 包裹：保证异常路径也会 hideLoading，否则转圈会一直挂着。 */
export async function withLoading<T>(title: string, task: () => Promise<T>): Promise<T> {
  Taro.showLoading({ title, mask: true })
  try {
    return await task()
  } finally {
    Taro.hideLoading()
  }
}
