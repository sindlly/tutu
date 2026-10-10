// Taro 的 babel 预设已经处理了 JSX、TS 与小程序端的 polyfill，
// 这里只需要声明目标平台，不要额外叠加 preset-env 以免和它的目标设置打架。
module.exports = {
  presets: [
    ['taro', {
      framework: 'react',
      ts: true,
      compiler: 'webpack5',
      useBuiltIns: process.env.TARO_ENV === 'h5' ? 'usage' : false,
    }],
  ],
}
