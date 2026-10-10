import { useRef } from 'react'
import { useDidShow } from '@tarojs/taro'

/**
 * 只在首次显示时执行，返回后再次进入不重复触发。
 *
 * 为什么需要它：Taro 的 useDidShow 每次页面显示都会触发，
 * 而结果页这类页面数据是一次性的（且图片地址带时效，不该反复刷）。
 * 用 useDidShow 而不是 useEffect，是因为页面可能被切到后台再回来，
 * useEffect 在小程序里不一定重新执行。
 */
export function useDidShowOnce(effect: () => void): void {
  const fired = useRef(false)
  useDidShow(() => {
    if (fired.current) return
    fired.current = true
    effect()
  })
}
