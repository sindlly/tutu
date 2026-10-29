import path from 'node:path'
import { defineConfig } from '@tarojs/cli'
import devConfig from './dev'
import prodConfig from './prod'

/**
 * Taro 构建配置。
 *
 * 设计宽度按 750（iPhone 6 基准）设置，样式里统一写 rpx 即可自适应。
 */
export default defineConfig(async (merge) => {
  const baseConfig = {
    projectName: 'homework-miniapp',
    date: '2026-10-9',
    designWidth: 750,
    deviceRatio: {
      640: 2.34 / 2,
      750: 1,
      375: 2,
      828: 1.81 / 2,
    },
    sourceRoot: 'src',
    outputRoot: 'dist',
    // tsconfig 的 paths 只管类型检查，打包器要单独配别名，否则 @/xxx 会被当成 npm 包去找
    alias: {
      '@': path.resolve(__dirname, '..', 'src'),
    },
    plugins: [],
    defineConstants: {},
    copy: {
      patterns: [
        // tabBar 图标不走 webpack（app.config 里的 iconPath 不会被打包器处理），直接整目录拷进 dist
        { from: 'src/assets/tabbar/', to: 'dist/assets/tabbar/' },
      ],
      options: {},
    },
    framework: 'react',
    compiler: 'webpack5',
    // 缓存偶尔会导致改了代码不生效，开发期关掉更省心
    cache: { enable: false },
    mini: {
      postcss: {
        pxtransform: { enable: true, config: {} },
        url: { enable: true, config: { limit: 1024 } },
        cssModules: { enable: false },
      },
    },
    h5: {
      publicPath: '/',
      staticDirectory: 'static',
      esnextModules: [],
      postcss: {
        autoprefixer: { enable: true, config: {} },
        cssModules: { enable: false },
      },
      // H5 只用于本地调试。跨域由后端的 app.cors.allowed-origins 控制，
      // 这里不做代理 —— 两种机制并存时容易搞不清请求到底走了哪条路。
      devServer: {
        port: 10086,
      },
    },
  }

  if (process.env.NODE_ENV === 'development') {
    return merge({}, baseConfig, devConfig)
  }
  return merge({}, baseConfig, prodConfig)
})
