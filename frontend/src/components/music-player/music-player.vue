<template>
  <!--
    根节点保持 .music-root：外层 #music-wrapper 有 overflow: hidden（裁剪胶囊用），
    列表浮窗是子元素、不依赖它做定位，样式无需改动。
  -->
  <div class="music-root">
    <div id="music-box" :class="{ 'is-playing': isPlaying, 'is-open': isOpen }">
      <!-- 封面 -->
      <div class="cover-wrapper">
        <div class="cover" :class="{ spinning: isPlaying }">
          <img v-if="coverUrl" :src="coverUrl" alt="cover" @error="coverUrl = ''" />
          <div v-else class="cover-placeholder"><span>&#x1f3b5;</span></div>
        </div>
        <div class="play-indicator" v-if="isPlaying">
          <span class="bar"></span><span class="bar"></span><span class="bar"></span>
        </div>
      </div>

      <!-- 曲目信息：仅在展开成胶囊时显示 -->
      <div class="music-info">
        <div class="music-title">{{ title || '未知歌曲' }}</div>
        <div class="music-artist">{{ artist || '未知歌手' }}</div>
      </div>

      <!-- progress bar -->
      <div class="progress-bar-wrapper" @click="handleProgressClick">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
          <!-- 0% 时不显示圆点，否则进度条没数据也会有个点悬在中间 -->
          <div
            v-if="progressPercent > 0"
            class="progress-dot"
            :style="{ left: progressPercent + '%' }"
          ></div>
        </div>
        <div class="progress-time">
          <span>{{ formatTime(currentTime) }}</span>
          <span>/</span>
          <span>{{ formatTime(duration) }}</span>
        </div>
      </div>

      <!-- controls -->
      <div class="music-controls">
        <button class="ctrl-btn" @click="handlePrev" title="上一首" aria-label="上一首">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
            <path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
          </svg>
        </button>
        <button
          class="ctrl-btn play-btn"
          @click="togglePlay"
          :title="isPlaying ? '暂停' : '播放'"
          :aria-label="isPlaying ? '暂停' : '播放'"
        >
          <svg v-if="!isPlaying" viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true">
            <path d="M8 5v14l11-7z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true">
            <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
          </svg>
        </button>
        <button class="ctrl-btn" @click="handleNext" title="下一首" aria-label="下一首">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
            <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
          </svg>
        </button>
        <button
          class="ctrl-btn like-btn"
          @click="isLiked = !isLiked"
          :class="{ liked: isLiked }"
          title="收藏"
          aria-label="收藏"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"
            :fill="isLiked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
          </svg>
        </button>
        <!-- 列表开关：展开/收起音乐列表 -->
        <button
          class="ctrl-btn list-btn"
          :class="{ active: showList }"
          @click="toggleList"
          :title="showList ? '收起列表' : '播放列表'"
          :aria-label="showList ? '收起播放列表' : '展开播放列表'"
          :aria-expanded="showList"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <line x1="4" y1="7" x2="20" y2="7"/>
            <line x1="4" y1="12" x2="20" y2="12"/>
            <line x1="4" y1="17" x2="14" y2="17"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- ===== 音乐列表 =====
         用 Teleport 送到 body：外层 #music-wrapper 有 overflow: hidden（原本用于裁剪
         胶囊），绝对定位子元素会被它裁掉。送到 body 后完全不受影响，
         因此播放器原有样式一行都不用改。
         代价是定位改用 fixed，坐标需要与悬浮球位置对齐（见样式注释）。 -->
    <Teleport to="body">
      <transition name="playlist">
        <div v-if="showList" ref="listEl" class="playlist" role="listbox" aria-label="播放列表">
          <div class="playlist-head">
            <span class="playlist-title">播放列表</span>
            <span class="playlist-count">{{ tracks.length }} 首</span>
          </div>
          <ul class="playlist-items">
            <li v-for="(item, idx) in tracks" :key="item.src || idx">
              <button
                class="playlist-item"
                :class="{ active: idx === currentIndex }"
                type="button"
                role="option"
                :aria-selected="idx === currentIndex"
                @click="selectTrack(idx)"
              >
                <span class="pi-index">{{ String(idx + 1).padStart(2, '0') }}</span>
                <span class="pi-main">
                  <span class="pi-title">{{ item.title }}</span>
                  <span class="pi-artist">{{ item.artist }}</span>
                </span>
                <span v-if="idx === currentIndex && isPlaying" class="pi-playing" aria-hidden="true">
                  <span class="bar"></span><span class="bar"></span><span class="bar"></span>
                </span>
              </button>
            </li>
          </ul>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  /** 由父组件控制的展开状态（点击悬浮球展开） */
  expanded: { type: Boolean, default: false }
})

// 音频源尚未配置，播放/暂停只切换 UI 状态。
const isPlaying = ref(false)
const isLiked = ref(false)
const coverUrl = ref('')
const currentTime = ref(0)
const duration = ref(0)

/**
 * 曲目数据：音频源尚未接入，src 先留空占位。
 * 以后把 src 填成真实地址即可，其余逻辑无需改动。
 */
const tracks = ref([
  { title: '起风了', artist: '买辣椒也用券', src: '' },
  { title: '夜航星', artist: '池禾', src: '' },
  { title: '云与电杆', artist: '池禾', src: '' },
  { title: '晚风', artist: '池禾', src: '' },
  { title: '未命名', artist: '池禾', src: '' }
])

const currentIndex = ref(0)
const showList = ref(false)
const listEl = ref(null)

// 展开状态由父组件控制
const isOpen = computed(() => props.expanded)

// 曲目信息从列表派生，避免两处维护同一份数据导致不同步
const currentTrack = computed(() => tracks.value[currentIndex.value] || {})
const title = computed(() => currentTrack.value.title || '')
const artist = computed(() => currentTrack.value.artist || '')

function toggleList () {
  showList.value = !showList.value
}

function selectTrack (idx) {
  currentIndex.value = idx
  currentTime.value = 0
  progressPercent.value = 0
  // 音频源接入前只切高亮，不真的播放
}

/**
 * 点击音乐列表以外的任何位置 → 自动关闭列表。
 * 用捕获阶段监听，保证在其它元素处理点击之前先判断。
 */
function onDocPointerDown (e) {
  if (listEl.value && !listEl.value.contains(e.target)) {
    showList.value = false
  }
}

watch(showList, (open) => {
  if (open) {
    document.addEventListener('pointerdown', onDocPointerDown, true)
  } else {
    document.removeEventListener('pointerdown', onDocPointerDown, true)
  }
})

// 组件卸载时务必移除监听，避免泄漏
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
})

// 收起播放条时同时关掉列表
watch(() => props.expanded, (open) => {
  if (!open) showList.value = false
})

function formatTime (seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  const m = String(Math.floor(total / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${m}:${s}`
}

const progressPercent = ref(0)

function togglePlay () {
  isPlaying.value = !isPlaying.value
}

function handlePrev () {
  currentIndex.value = (currentIndex.value - 1 + tracks.value.length) % tracks.value.length
  currentTime.value = 0
  progressPercent.value = 0
}

function handleNext () {
  currentIndex.value = (currentIndex.value + 1) % tracks.value.length
  currentTime.value = 0
  progressPercent.value = 0
}

function handleProgressClick () {
  // 音频源接入后在此换算点击位置对应的时间
}
</script>

<style scoped>
  /* 组件根节点：撑满外层 #music-wrapper。
     必须显式给尺寸，否则它作为静态块会塌陷，内层 #music-box 的
     width/height: 100% 失去参照 → 胶囊形状会出错。
     也必须继承圆角：border-radius: inherit 只认直接父元素，
     本层若为 0，内层 #music-box 就会跟着算成 0（实测过，
     表现为"内圈不是胶囊形"）。 */
  .music-root {
    width: 100%;
    height: 100%;
    position: relative;
    border-radius: inherit;
  }

  #music-box {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px;
    box-sizing: border-box;
    color: #ffffff;
    /* 外圈颜色 */
    background: #1a7891;
    /* 继承外层 #music-wrapper 的圆角：外层负责定义形状（圆形 / 悬停展开的胶囊），
       内层填满它并跟随。写死具体值会导致两层圆角不一致，内层会把外层的圆角盖住。
       overflow:hidden 让背景被裁成与外层完全相同的形状。 */
    border-radius: inherit;
    overflow: hidden;
    border: 1px solid rgba(255, 255, 255, 0.18);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
    user-select: none;
  }

  /* ===== 封面 ===== */
  .cover-wrapper {
    position: relative;
    flex: none;
  }

  .cover {
    width: 42px;
    height: 42px;
    border-radius: 50%;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.18);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .cover img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .cover.spinning {
    animation: music-spin 12s linear infinite;
  }

  @keyframes music-spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }

  .cover-placeholder {
    font-size: 18px;
    line-height: 1;
  }

  .play-indicator {
    position: absolute;
    right: -2px;
    bottom: -2px;
    display: flex;
    align-items: flex-end;
    gap: 2px;
    height: 10px;
  }

  .play-indicator .bar {
    width: 2px;
    background: rgb(129, 110, 216);
    animation: music-bar 0.9s ease-in-out infinite;
  }

  .play-indicator .bar:nth-child(1) { height: 40%; animation-delay: 0s; }
  .play-indicator .bar:nth-child(2) { height: 100%; animation-delay: 0.15s; }
  .play-indicator .bar:nth-child(3) { height: 60%; animation-delay: 0.3s; }

  @keyframes music-bar {
    0%, 100% { transform: scaleY(0.5); }
    50% { transform: scaleY(1); }
  }

  /* ===== 曲目信息 ===== */
  /* 默认（圆形悬浮球）状态下隐藏文字，否则内容会溢出圆球 */
  .music-info {
    display: none;
  }

  /* 展开态才显示曲目信息：由点击（父组件传入 expanded → .is-open）驱动，
     不再依赖 :hover（触屏没有 hover，且容易误展开）。 */
  #music-box.is-open .music-info {
    display: flex;
  }

  .music-info {
    flex-direction: column;
    gap: 0;
    min-width: 0;
    flex: 1;
  }

  .music-title {
    font-size: 12px;
    color: #ffffff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .music-artist {
    font-size: 10px;
    /* 奶米黄，与外圈 #1A7891 形成对比且不刺眼 */
    color: #f2ebc7;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* ===== 进度条 ===== */
  .progress-bar-wrapper {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
    cursor: pointer;
  }

  /* 默认（圆形悬浮球）状态只保留封面圆盘：
     胶囊内容总宽约 174px，塞进 56px 只会被裁出一条尾巴，必须显式隐藏。 */
  #music-box:not(.is-open) .progress-bar-wrapper {
    display: none;
  }

  .progress-bar {
    position: relative;
    height: 4px;
    border-radius: 999px;
    /* 与外圈同色系的深色轨道，保证在 #1A7891 上有对比 */
    background: rgba(0, 0, 0, 0.28);
  }

  .progress-fill {
    position: absolute;
    left: 0;
    top: 0;
    height: 100%;
    border-radius: 999px;
    /* 奶米黄进度，替代原来的紫色以匹配新配色 */
    background: #f2ebc7;
  }

  .progress-dot {
    position: absolute;
    top: 50%;
    width: 8px;
    height: 8px;
    margin-left: -4px;
    border-radius: 50%;
    background: #ffffff;
    transform: translateY(-50%);
  }

  .progress-time {
    display: flex;
    align-items: center;
    gap: 3px;
    font-size: 9px;
    line-height: 1;
    color: #d6f0f5;
    white-space: nowrap;
  }

  /* 默认（圆形悬浮球）状态隐藏时间：高度只有 56px，必须精简内容。
     触屏用 .is-open（类挂在 #music-box 上）判断，因此两个条件都要覆盖。 */
  #music-box:not(.is-open) .progress-time {
    display: none;
  }

  /* ===== 控制按钮 ===== */
  .music-controls {
    display: flex;
    align-items: center;
    gap: 4px;
    flex: none;
  }

  .ctrl-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    /* 触控目标：移动端建议 >= 44px。图标仍保持原视觉大小，
       靠 padding 撑大热区，再用负外边距抵消，避免撑大整个胶囊。 */
    padding: 12px;
    margin: -8px;
    color: #e8f6f9;
    background: transparent;
    border: none;
    border-radius: 50%;
    cursor: pointer;
    touch-action: manipulation;
    transition: color 0.2s ease, background 0.2s ease;
  }

  .ctrl-btn:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.16);
  }

  /* 收藏选中态：用奶米黄，与原紫色一起替换为新配色 */
  .ctrl-btn.liked {
    color: #f2ebc7;
  }

  /* ===== 小屏：胶囊展开宽度收窄，进一步精简内容 ===== */
  @media (max-width: 600px) {
    .cover {
      width: 34px;
      height: 34px;
    }

    .music-controls {
      gap: 2px;
    }

    /* 优先丢掉次要文字，保证控件不被挤压 */
    .music-info {
      display: none !important;
    }

    /* 小屏胶囊里进一步放大热区 */
    .ctrl-btn {
      padding: 14px;
      margin: -10px;
    }

    /* 小屏列表位置：对齐球在 ≤600px 时的位置与尺寸 */
    .playlist {
      left: 16px;
      bottom: calc(74px + env(safe-area-inset-bottom, 0px));
      max-height: min(42vh, 260px);
    }
  }

  /* ===== 列表开关的选中态 ===== */
  .ctrl-btn.list-btn.active {
    color: #f2ebc7;
    background: rgba(255, 255, 255, 0.16);
  }

  /* ===== 音乐列表（新增，不改动播放器既有样式） =====
     已被 Teleport 到 body，因此用 fixed 定位，坐标与悬浮球对齐：
       桌面：球 left:30px / bottom:30px，球高 56px → 列表 left:30px / bottom:96px
       小屏：球 left:16px / bottom:16px，球高 48px → 列表 left:16px / bottom:74px
     （下面媒体查询里给出小屏值） */
  .playlist {
    position: fixed;
    left: 30px;
    bottom: calc(96px + env(safe-area-inset-bottom, 0px));
    width: min(320px, calc(100vw - 32px));
    max-height: min(46vh, 300px);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: rgba(12, 42, 51, 0.94);
    border: 1px solid rgba(255, 255, 255, 0.14);
    border-radius: 16px;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
    backdrop-filter: blur(10px);
    color: #e8f6f9;
    /* 高于播放条的 z-index: 999 */
    z-index: 1000;
  }

  .playlist-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px 8px;
    flex: none;
  }

  .playlist-title {
    font-size: 13px;
    font-weight: 500;
    color: #ffffff;
  }

  .playlist-count {
    font-size: 11px;
    color: #9fc4cd;
  }

  .playlist-items {
    list-style: none;
    margin: 0;
    padding: 0 6px 8px;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    /* 滚动条美化：默认样式在深色面板上很突兀 */
    scrollbar-width: thin;                                  /* Firefox */
    scrollbar-color: rgba(159, 196, 205, 0.5) transparent;   /* Firefox */
  }

  /* WebKit（Chrome / Edge / Safari） */
  .playlist-items::-webkit-scrollbar {
    width: 6px;
  }

  .playlist-items::-webkit-scrollbar-track {
    background: transparent;
    /* 让轨道与圆角面板贴合 */
    margin: 4px 0;
  }

  .playlist-items::-webkit-scrollbar-thumb {
    background: rgba(159, 196, 205, 0.42);
    border-radius: 999px;
  }

  .playlist-items::-webkit-scrollbar-thumb:hover {
    background: rgba(242, 235, 199, 0.6);
  }

  /* 同时隐藏角落方块，避免出现一截直角 */
  .playlist-items::-webkit-scrollbar-corner {
    background: transparent;
  }

  .playlist-item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    min-height: 44px;
    box-sizing: border-box;
    padding: 9px 8px;
    background: none;
    border: none;
    border-radius: 10px;
    color: #e8f6f9;
    font: inherit;
    text-align: left;
    cursor: pointer;
    touch-action: manipulation;
  }

  .playlist-item:hover {
    background: rgba(255, 255, 255, 0.08);
  }

  .playlist-item.active {
    background: rgba(242, 235, 199, 0.14);
  }

  .pi-index {
    flex: none;
    width: 20px;
    font-size: 11px;
    color: #7fa8b3;
    font-variant-numeric: tabular-nums;
  }

  .playlist-item.active .pi-index {
    color: #f2ebc7;
  }

  .pi-main {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
    flex: 1;
  }

  .pi-title {
    font-size: 13px;
    color: #ffffff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .playlist-item.active .pi-title {
    color: #f2ebc7;
  }

  .pi-artist {
    font-size: 11px;
    color: #9fc4cd;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .pi-playing {
    display: flex;
    align-items: flex-end;
    gap: 2px;
    height: 12px;
    flex: none;
  }

  .pi-playing .bar {
    width: 2px;
    background: #f2ebc7;
    animation: music-bar 0.9s ease-in-out infinite;
  }

  .pi-playing .bar:nth-child(1) { height: 40%; animation-delay: 0s; }
  .pi-playing .bar:nth-child(2) { height: 100%; animation-delay: 0.15s; }
  .pi-playing .bar:nth-child(3) { height: 60%; animation-delay: 0.3s; }

  /* 列表弹出/收起过渡 */
  .playlist-enter-active,
  .playlist-leave-active {
    transition: opacity 0.2s ease, transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  .playlist-enter-from,
  .playlist-leave-to {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
    transform-origin: bottom left;
  }

  @media (prefers-reduced-motion: reduce) {
    .playlist-enter-active,
    .playlist-leave-active {
      transition: opacity 0.15s ease;
    }

    .playlist-enter-from,
    .playlist-leave-to {
      transform: none;
    }
  }
</style>
