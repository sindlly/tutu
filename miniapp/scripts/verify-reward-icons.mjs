/**
 * weapp 端奖励图标显示验证脚本：
 * 连接 devtools 自动化端口 9421，进入积分页，检查奖励图标 <image> 的 src
 * 是否为绝对路径 /assets/rewards/*.png，并截图保存。
 * 用法：node scripts/verify-reward-icons.mjs
 */
import automator from 'miniprogram-automator'

const WS = 'ws://127.0.0.1:9421'

function withTimeout(p, label, ms = 15000) {
  return Promise.race([
    p,
    new Promise((_, rej) => setTimeout(() => rej(new Error(`timeout: ${label}`)), ms)),
  ])
}

async function main() {
  const mini = await withTimeout(automator.connect({ wsEndpoint: WS }), 'connect')
  console.log('[1] connected')

  // 确保有活跃孩子上下文（无则先去选择页选第一个）
  const sys = await withTimeout(mini.systemInfo(), 'systemInfo')
  console.log('[2] sdkVersion:', sys.SDKVersion)

  // 尝试直达积分页
  let page = await withTimeout(mini.reLaunch('/pages/points/points'), 'reLaunch points')
  await page.waitFor(800)
  page = await mini.currentPage()
  console.log('[3] current page:', page.path)

  if (page.path !== 'pages/points/points') {
    // 上下文缺失时先选孩子
    console.log('    not on points, go child-select first')
    page = await withTimeout(mini.reLaunch('/pages/child-select/child-select'), 'reLaunch child-select')
    await page.waitFor(800)
    const kid = await page.$('.kid-card, .kid-row, .child-item')
    if (kid) {
      await kid.tap()
      await page.waitFor(1000)
    }
    page = await withTimeout(mini.navigateTo('/pages/points/points'), 'navigateTo points')
    await page.waitFor(800)
    console.log('[3b] now at:', (await mini.currentPage()).path)
  }

  page = await mini.currentPage()
  const icons = await page.$$('image.reward-icon, .reward-icon image, image')
  console.log('[4] image elements found:', icons.length)

  const srcs = []
  for (const el of icons.slice(0, 30)) {
    const src = await el.attribute('src')
    if (src) srcs.push(src)
  }
  const rewardSrcs = srcs.filter((s) => s.includes('rewards'))
  console.log('[5] reward icon srcs:', JSON.stringify(rewardSrcs.slice(0, 15), null, 2))

  const bad = rewardSrcs.filter((s) => !s.startsWith('/assets/rewards/'))
  console.log('[6] bad (non-absolute) srcs:', bad.length, bad)

  await withTimeout(mini.screenshot({ path: 'scripts/reward-icons-weapp.png' }), 'screenshot')
  console.log('[7] screenshot saved: scripts/reward-icons-weapp.png')

  const ok = rewardSrcs.length > 0 && bad.length === 0
  console.log(ok ? 'RESULT: PASS' : 'RESULT: FAIL')
  process.exit(ok ? 0 : 1)
}

main().catch((e) => {
  console.error('ERROR:', e)
  process.exit(1)
})
