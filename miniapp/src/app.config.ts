export default defineAppConfig({
  pages: [
    'pages/index/index',
    'pages/capture/capture',
    'pages/progress/progress',
    'pages/result/result',
    'pages/mistakes/mistakes',
    'pages/mistake-detail/mistake-detail',
    'pages/tasks/tasks',
    'pages/points/points',
    'pages/studio/studio',
    'pages/daily-essay/daily-essay',
    'pages/child-select/child-select',
    'pages/children/children',
    'pages/profile/profile',
  ],
  window: {
    backgroundTextStyle: 'dark',
    navigationBarBackgroundColor: '#f5f4ed',
    navigationBarTitleText: '每天进步一点点',
    navigationBarTextStyle: 'black',
    backgroundColor: '#f5f4ed',
  },
  tabBar: {
    color: '#87867f',
    selectedColor: '#c96442',
    backgroundColor: '#faf9f5',
    borderStyle: 'white',
    // 图标来自 Tabler（MIT，via Iconify），81x81 PNG：石灰 #87867f / 赤陶 #c96442 两态；
    // 工作台为圆形实底按钮（第 3 位），是 AI 能力聚合入口
    list: [
      {
        pagePath: 'pages/index/index',
        text: '首页',
        iconPath: 'assets/tabbar/home.png',
        selectedIconPath: 'assets/tabbar/home-active.png',
      },
      {
        pagePath: 'pages/tasks/tasks',
        text: '任务',
        iconPath: 'assets/tabbar/calendar-check.png',
        selectedIconPath: 'assets/tabbar/calendar-check-active.png',
      },
      {
        pagePath: 'pages/studio/studio',
        text: '工作台',
        iconPath: 'assets/tabbar/studio.png',
        selectedIconPath: 'assets/tabbar/studio-active.png',
      },
      {
        pagePath: 'pages/mistakes/mistakes',
        text: '错题本',
        iconPath: 'assets/tabbar/book.png',
        selectedIconPath: 'assets/tabbar/book-active.png',
      },
      {
        pagePath: 'pages/profile/profile',
        text: '我的',
        iconPath: 'assets/tabbar/user.png',
        selectedIconPath: 'assets/tabbar/user-active.png',
      },
    ],
  },
})
