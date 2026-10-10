export default {
  env: {
    NODE_ENV: '"production"',
  },
  defineConstants: {},
  mini: {},
  h5: {
    /**
     * 生产环境必须用已备案的 HTTPS 域名，不能用 IP。
     * 在小程序后台「开发管理 → 服务器域名」里把它加进 request / uploadFile 合法域名。
     * 目标域名改这里即可，不要在业务代码里硬编码。
     */
    publicPath: '/',
  },
}
