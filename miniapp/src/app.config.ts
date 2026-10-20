export default defineAppConfig({
  pages: [
    'pages/index/index',
    'pages/capture/capture',
    'pages/progress/progress',
    'pages/result/result',
    'pages/mistakes/mistakes',
    'pages/mistake-detail/mistake-detail',
    'pages/child-select/child-select',
    'pages/children/children',
    'pages/profile/profile',
  ],
  window: {
    backgroundTextStyle: 'dark',
    navigationBarBackgroundColor: '#f6f1e4',
    navigationBarTitleText: '拍照批改',
    navigationBarTextStyle: 'black',
    backgroundColor: '#f6f1e4',
  },
  tabBar: {
    color: '#8b8171',
    selectedColor: '#d2402e',
    backgroundColor: '#fffdf6',
    borderStyle: 'black',
    // 只用文字不用图标：省掉图标资源，也避免为了凑图而引入质量差的素材
    list: [
      { pagePath: 'pages/index/index', text: '批改' },
      { pagePath: 'pages/mistakes/mistakes', text: '错题本' },
      { pagePath: 'pages/profile/profile', text: '我的' },
    ],
  },
})
