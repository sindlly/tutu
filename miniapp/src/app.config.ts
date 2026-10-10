export default defineAppConfig({
  pages: [
    'pages/index/index',
    'pages/capture/capture',
    'pages/progress/progress',
    'pages/result/result',
    'pages/mistakes/mistakes',
    'pages/mistake-detail/mistake-detail',
    'pages/profile/profile',
  ],
  window: {
    backgroundTextStyle: 'light',
    navigationBarBackgroundColor: '#ffffff',
    navigationBarTitleText: '拍照批改',
    navigationBarTextStyle: 'black',
    backgroundColor: '#f5f6f8',
  },
  tabBar: {
    color: '#8c929e',
    selectedColor: '#3b6ef5',
    backgroundColor: '#ffffff',
    borderStyle: 'black',
    // 只用文字不用图标：省掉图标资源，也避免为了凑图而引入质量差的素材
    list: [
      { pagePath: 'pages/index/index', text: '批改' },
      { pagePath: 'pages/mistakes/mistakes', text: '错题本' },
      { pagePath: 'pages/profile/profile', text: '我的' },
    ],
  },
})
