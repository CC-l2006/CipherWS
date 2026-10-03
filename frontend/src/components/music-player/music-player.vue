<template>
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
    </div>

    <!--
      收起按钮：触屏没有"移开鼠标"这个动作，展开后必须给一个明确的收起入口。
      桌面端由 CSS 隐藏（移开鼠标即收起）。
    -->
    <button class="ctrl-btn music-collapse" @click="collapse" title="收起" aria-label="收起">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
        <polyline points="6 9 12 15 18 9"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  /** 由父组件控制的展开状态（触屏点击展开用） */
  expanded: { type: Boolean, default: false }
})

const emit = defineEmits(['collapse'])

// 音频源尚未配置，播放/暂停只切换 UI 状态。
const isPlaying = ref(false)
const isLiked = ref(false)
const coverUrl = ref('')
const title = ref('')
const artist = ref('')
const currentTime = ref(0)
const duration = ref(0)

// 展开时隐藏"曲目信息/进度条/收起按钮"以外的判断都基于这个值
const isOpen = computed(() => props.expanded)

function collapse () {
  emit('collapse')
}

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
  currentTime.value = 0
  progressPercent.value = 0
}

function handleNext () {
  currentTime.value = 0
  progressPercent.value = 0
}

function handleProgressClick () {
  // 音频源接入后在此换算点击位置对应的时间
}
</script>

<style scoped>
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
  /* 默认（圆形悬浮球）状态下隐藏文字，否则内容会溢出圆球。
     展开条件同时接受父级 :hover（桌面）与组件自身 .is-open（触屏点击）。 */
  .music-info {
    display: none;
  }

  #music-wrapper:hover .music-info,
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
  #music-wrapper:not(:hover):not(.is-open) .progress-bar-wrapper {
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
  #music-wrapper:not(:hover):not(.is-open) .progress-time {
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
  }
</style>
