/**
 * 奖励中心预制卡通图标清单。
 * key 与后端白名单（RewardService.ICON_KEYS）一一对应，
 * 图片文件在 src/assets/rewards/{key}.png（构建期 copy 进 dist/assets/rewards/）。
 */
export interface RewardIconDef {
  key: string
  label: string
  src: string
}

export const REWARD_ICONS: RewardIconDef[] = [
  { key: 'icecream', label: '零食', src: '/assets/rewards/icecream.png' },
  { key: 'toy', label: '玩具', src: '/assets/rewards/toy.png' },
  { key: 'tv', label: '动画片', src: '/assets/rewards/tv.png' },
  { key: 'park', label: '出去玩', src: '/assets/rewards/park.png' },
  { key: 'book', label: '绘本', src: '/assets/rewards/book.png' },
  { key: 'ball', label: '运动', src: '/assets/rewards/ball.png' },
  { key: 'star', label: '小星星', src: '/assets/rewards/star.png' },
  { key: 'gift', label: '神秘礼物', src: '/assets/rewards/gift.png' },
  { key: 'cake', label: '蛋糕', src: '/assets/rewards/cake.png' },
  { key: 'music', label: '音乐', src: '/assets/rewards/music.png' },
  { key: 'swim', label: '游泳', src: '/assets/rewards/swim.png' },
  { key: 'game', label: '游戏', src: '/assets/rewards/game.png' },
]

export const DEFAULT_REWARD_ICON = '/assets/rewards/gift.png'

export function rewardIconSrc(icon?: string | null): string {
  return REWARD_ICONS.find((i) => i.key === icon)?.src ?? DEFAULT_REWARD_ICON
}
