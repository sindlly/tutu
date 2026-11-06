/// <reference types="@tarojs/taro" />

declare module '*.png'
declare module '*.gif'
declare module '*.jpg'
declare module '*.jpeg'
declare module '*.svg'
declare module '*.css'
declare module '*.scss'

declare namespace NodeJS {
  interface ProcessEnv {
    /** 由 Taro 在构建时注入 */
    TARO_ENV: 'weapp' | 'swan' | 'alipay' | 'h5' | 'tt' | 'qq' | 'jd'
    NODE_ENV: 'development' | 'production'
    /**
     * 后端接口地址，构建时用 TARO_APP_API_BASE=xxx 注入（TARO_APP_ 前缀才会被打进产物）。
     * 不注入时回落本机调试地址。见 package.json 的 *:online 脚本。
     */
    TARO_APP_API_BASE?: string
  }
}

/** Taro 注入的全局函数 */
declare function defineAppConfig(config: Taro.Config): Taro.Config
