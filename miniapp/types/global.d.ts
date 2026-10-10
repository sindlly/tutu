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
  }
}

/** Taro 注入的全局函数 */
declare function defineAppConfig(config: Taro.Config): Taro.Config
