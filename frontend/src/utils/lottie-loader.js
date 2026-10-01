import lottie from 'lottie-web'
import animationData from '@/assets/loading/CipherWS.json'
// 加载动画的样式随 JS 一起打包（原先是 index.html 里的 <link>，构建时不会被打包）
import '@/assets/loading/loading.css'

function loadLottieAnimation() {
  const container = document.getElementById('lottie-container')
  if (!container) return

  const anim = lottie.loadAnimation({
    container,
    renderer: 'svg',
    loop: false,
    autoplay: true,
    animationData
  })

  anim.addEventListener('complete', () => {
    container.classList.add('fade-out')
    setTimeout(() => {
      container.style.display = 'none'
      document.body.classList.add('loaded')
    }, 500)
  })
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', loadLottieAnimation)
} else {
  loadLottieAnimation()
}
