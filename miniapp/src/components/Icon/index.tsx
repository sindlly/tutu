/**
 * 页内图标组件：SVG data-URI 背景（weapp 的 <image> 不支持 SVG，
 * 但 CSS background-image 支持 data-URI，两端通用）。
 *
 * 图标来自 Tabler（MIT，via Iconify），stroke 风格，线宽 2、viewBox 24。
 * weapp 不认 currentColor，颜色必须显式传入。
 */
import { View } from '@tarojs/components'
import Taro from '@tarojs/taro'

const ICONS = {
  camera:
    '<path d="M5 7h1a2 2 0 0 0 2-2a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1a2 2 0 0 0 2 2h1a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2"/><path d="M9 13a3 3 0 1 0 6 0a3 3 0 0 0-6 0"/>',
  'chevron-right': '<path d="m9 6l6 6l-6 6"/>',
  coin: '<path d="M15 11v.01"/><path d="M5.173 8.378a3 3 0 1 1 4.656 -1.377"/><path d="M16 4v3.803a6.019 6.019 0 0 1 2.658 3.197h1.341a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-1.342a6.008 6.008 0 0 1 -1.658 2.473v2.027a1.5 1.5 0 0 1 -3 0v-.583a6.04 6.04 0 0 1 -1 .083h-4v1.5a1.5 1.5 0 0 1 -3 0v-2.027a6 6 0 0 1 -4 -5.659v-.5a3 3 0 0 1 3 -3h1.5z"/><path d="M4 11.5h4"/><path d="M9 5.5a3 3 0 0 1 3 -3h4"/>',
  pencil:
    '<path d="M4 20h4L18.5 9.5a2.828 2.828 0 1 0-4-4L4 16zm9.5-13.5l4 4"/>',
  sparkles:
    '<path d="M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2-2a2 2 0 0 1-2-2a2 2 0 0 1-2 2m0-12a2 2 0 0 1 2 2a2 2 0 0 1 2-2a2 2 0 0 1-2-2a2 2 0 0 1-2 2M9 18a6 6 0 0 1 6-6a6 6 0 0 1-6-6a6 6 0 0 1-6 6a6 6 0 0 1 6 6"/>',
} as const

export type IconName = keyof typeof ICONS

interface IconProps {
  name: IconName
  /** 尺寸（rpx），默认 48 */
  size?: number
  /** 图标颜色（十六进制），默认墨绿黑 */
  color?: string
}

export default function Icon({ name, size = 48, color = '#141413' }: IconProps) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[name]}</svg>`
  // 内联样式里直接写 rpx 在 H5 不会转换（宽度变 0），必须走 pxTransform 做两端适配
  const wh = Taro.pxTransform(size)
  const style = `width:${wh};height:${wh};background:center/contain no-repeat url("data:image/svg+xml,${encodeURIComponent(svg)}");display:inline-block;`
  return <View style={style} />
}
