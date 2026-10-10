import lottie from 'lottie-web'
import animationData from '@/assets/loading/CipherWS.json'
// 加载动画的样式随 JS 一起打包（原先是 index.html 里的 <link>，构建时不会被打包）
import '@/assets/loading/loading.css'

// 遮罩最多停留多久（毫秒）。动画本身约 3 秒，
// 这里给足余量，超时就直接放行，避免任何情况下页面被永久卡住。
const MAX_LOADING_MS = 6000

// 动画数据里带了总时长，按它推算一个更贴合的安全超时
const ANIMATION_MS =
  typeof animationData.op === 'number' && typeof animationData.fr === 'number' && animationData.fr > 0
    ? Math.ceil((animationData.op / animationData.fr) * 1000)
    : 0

let started = false

function revealApp(container) {
  if (!container) return
  container.classList.add('fade-out')
  // 立刻解除命中拦截，不让淡出的 0.8s 挡住点击
  container.style.pointerEvents = 'none'
  window.setTimeout(() => {
    container.style.display = 'none'
    document.body.classList.add('loaded')
  }, 500)
}

function loadLottieAnimation() {
  // 防止重复初始化（热更新或重复调用时）
  if (started) return
  started = true

  const container = document.getElementById('lottie-container')
  if (!container) return

  // 兜底：动画报错时也要放行，绝不停在遮罩上
  const failSafe = () => revealApp(container)

  // 兜底：无论动画是否正常触发 complete，超时一律放行。
  // 这是修复"刷新后遮罩卡死、点不了按钮"的关键——
  // 原先只依赖 complete 事件，一旦该事件丢失（监听器挂上之前动画已经播完），
  // 遮罩就会永远留在全屏拦截点击的状态。
  const timeoutMs = ANIMATION_MS > 0 ? ANIMATION_MS + 2000 : MAX_LOADING_MS
  const watchdog = window.setTimeout(failSafe, timeoutMs)

  try {
    const anim = lottie.loadAnimation({
      container,
      renderer: 'svg',
      loop: false,
      autoplay: true,
      animationData
    })

    const done = () => {
      window.clearTimeout(watchdog)
      revealApp(container)
    }

    // 两个事件都监听：complete 是播完，DOMLoaded 是首帧渲染完成。
    // 后者能覆盖"数据已就绪、complete 可能已被错过"的情况。
    anim.addEventListener('complete', done)
    anim.addEventListener('DOMLoaded', () => {
      // 动画已经不在播放中（总帧数已到达）说明 complete 已经错过，直接放行
      if (typeof anim.currentFrame === 'function' && typeof anim.totalFrames === 'number') {
        if (anim.totalFrames > 0 && anim.currentFrame() >= anim.totalFrames - 1) done()
      }
    })
    anim.addEventListener('data_failed', done)

    // 尊重"减少动态效果"偏好：直接跳过开场动画
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      anim.destroy()
      done()
    }
  } catch (err) {
    console.warn('开场动画加载失败，已跳过：', err)
    failSafe()
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', loadLottieAnimation)
} else {
  loadLottieAnimation()
}
