<template>
  <div id="music-box" :class="{ 'is-playing': isPlaying, 'is-fullscreen': isFullscreen }">
    <!-- cover (mini mode) -->
    <div class="cover-wrapper" v-show="!isFullscreen">
      <div class="cover" :class="{ spinning: isPlaying }">
        <img v-if="coverUrl" :src="coverUrl" alt="cover" @error="coverUrl = ''" />
        <div v-else class="cover-placeholder"><span>&#x1f3b5;</span></div>
      </div>
      <div class="play-indicator" v-if="isPlaying">
        <span class="bar"></span><span class="bar"></span><span class="bar"></span>
      </div>
    </div>

    <!-- fullscreen content -->
    <template v-if="isFullscreen">
      <div class="fs-top">
        <div class="fs-cover" :class="{ spinning: isPlaying }">
          <img v-if="coverUrl" :src="coverUrl" alt="cover" />
          <div v-else class="cover-placeholder-lg">&#x1f3b5;</div>
        </div>
        <div class="fs-info">
          <div class="fs-title">{{ title || '未知歌曲' }}</div>
          <div class="fs-artist">{{ artist || '未知歌手' }}</div>
        </div>
      </div>
      <div class="lyrics-panel" ref="lyricsPanel">
        <div v-if="parsedLyrics.length === 0" class="lyrics-empty">暂无歌词</div>
        <div v-for="(line, idx) in parsedLyrics" :key="idx"
          class="lyrics-line" :class="{ active: idx === currentLyricIndex }">
          {{ line.text }}
        </div>
      </div>
    </template>

    <!-- expanded info -->
    <template v-else>
      <div class="music-info">
        <div class="music-title">{{ title || '未知歌曲' }}</div>
        <div class="music-artist">{{ artist || '未知歌手' }}</div>
      </div>
    </template>

    <!-- progress bar -->
    <div class="progress-bar-wrapper" @click="handleProgressClick">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        <div class="progress-dot" :style="{ left: progressPercent + '%' }"></div>
      </div>
      <div class="progress-time">
        <span>{{ formatTime(currentTime) }}</span>
        <span>{{ formatTime(duration) }}</span>
      </div>
    </div>

    <!-- controls -->
    <div class="music-controls" :class="{ 'fs-controls': isFullscreen }">
      <button class="ctrl-btn" @click="handlePrev" title="上一首">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
        </svg>
      </button>
      <button class="ctrl-btn play-btn" @click="togglePlay" :title="isPlaying ? '暂停' : '播放'">
        <svg v-if="!isPlaying" viewBox="0 0 24 24" :width="isFullscreen ? 28 : 20" :height="isFullscreen ? 28 : 20" fill="currentColor">
          <path d="M8 5v14l11-7z"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" :width="isFullscreen ? 28 : 20" :height="isFullscreen ? 28 : 20" fill="currentColor">
          <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
        </svg>
      </button>
      <button class="ctrl-btn" @click="handleNext" title="下一首">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
          <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
        </svg>
      </button>
      <button class="ctrl-btn like-btn" @click="isLiked = !isLiked" :class="{ liked: isLiked }" title="收藏">
        <svg viewBox="0 0 24 24" width="14" height="14"
          :fill="isLiked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
        </svg>
      </button>
      <button v-if="isFullscreen" class="ctrl-btn fs-close-btn" @click="isFullscreen = false" title="收起">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </button>
    </div>

    <!-- expand button -->
    <button v-if="!isFullscreen" class="expand-btn" @click="isFullscreen = true" title="全屏播放">
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/>
        <line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

// ⚠️ 原文件只有 template，所有绑定都是未定义的（渲染时报错）。
// 这里补齐最小可用逻辑：音频源尚未配置，播放/暂停只切换 UI 状态。
const isPlaying = ref(false)
const isFullscreen = ref(false)
const isLiked = ref(false)
const coverUrl = ref('')
const title = ref('')
const artist = ref('')
const currentTime = ref(0)
const duration = ref(0)
const lyricsPanel = ref(null)
const currentLyricIndex = ref(0)

// 歌词格式：每行 `[mm:ss]歌词内容`
const rawLyrics = ref('')
const parsedLyrics = ref([])

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
    gap: 10px;
    padding: 6px;
    box-sizing: border-box;
    color: #ffffff;
    background: rgba(20, 20, 24, 0.6);
    backdrop-filter: blur(8px);
    user-select: none;
  }

  #music-box.is-fullscreen {
    flex-direction: column;
    justify-content: center;
    padding: 20px;
    gap: 14px;
    background: rgba(12, 12, 16, 0.88);
  }

  /* ===== 封面 ===== */
  .cover-wrapper {
    position: relative;
    flex: none;
  }

  .cover {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.12);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .cover img,
  .fs-cover img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .cover.spinning,
  .fs-cover.spinning {
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
  .music-info,
  .fs-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    flex: 1;
  }

  .music-title,
  .fs-title {
    font-size: 13px;
    color: #ffffff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .music-artist,
  .fs-artist {
    font-size: 11px;
    color: #b4b4b4;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .fs-title { font-size: 16px; }
  .fs-artist { font-size: 12px; }

  .fs-top {
    display: flex;
    align-items: center;
    gap: 14px;
    width: 100%;
  }

  .fs-cover {
    width: 64px;
    height: 64px;
    flex: none;
    border-radius: 50%;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.12);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .cover-placeholder-lg {
    font-size: 26px;
    line-height: 1;
  }

  /* ===== 进度条 ===== */
  .progress-bar-wrapper {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;
    cursor: pointer;
  }

  .progress-bar {
    position: relative;
    height: 3px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.2);
  }

  .progress-fill {
    position: absolute;
    left: 0;
    top: 0;
    height: 100%;
    border-radius: 999px;
    background: rgb(129, 110, 216);
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
    justify-content: space-between;
    font-size: 10px;
    color: #b4b4b4;
  }

  /* ===== 控制按钮 ===== */
  .music-controls {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .music-controls.fs-controls {
    justify-content: center;
  }

  .ctrl-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px;
    color: #d9d9d9;
    background: transparent;
    border: none;
    border-radius: 50%;
    cursor: pointer;
    transition: color 0.2s ease;
  }

  .ctrl-btn:hover {
    color: #ffffff;
  }

  .ctrl-btn.liked {
    color: rgb(129, 110, 216);
  }

  .expand-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px;
    color: #b4b4b4;
    background: transparent;
    border: none;
    cursor: pointer;
  }

  .expand-btn:hover {
    color: #ffffff;
  }

  /* ===== 歌词面板（全屏） ===== */
  .lyrics-panel {
    width: 100%;
    max-height: 140px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
    text-align: center;
  }

  .lyrics-line {
    font-size: 12px;
    color: #8a8a8a;
  }

  .lyrics-line.active {
    color: #ffffff;
  }

  .lyrics-empty {
    font-size: 12px;
    color: #8a8a8a;
  }
</style>
