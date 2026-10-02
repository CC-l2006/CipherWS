<template>
  <button
    class="gear-btn"
    type="button"
    :aria-label="ariaLabel"
    :aria-expanded="ariaExpanded"
    @click="handleClick"
  >
    <!-- key 绑定点击次数：每次点击重建该元素，从而重新播放扩散动画
         （animation 只在元素首次渲染时触发，不换 key 的话重复点击不会重播） -->
    <span v-if="clickCount > 0" :key="clickCount" class="gear-btn__pulse" aria-hidden="true"></span>

    <svg
      class="gear-btn__icon"
      :style="iconStyle"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <!-- 齿轮外廓：8 齿，仅描边不填充 -->
      <path
        d="M19.44 10.46L22.18 9.89L22.18 14.11L19.44 13.54A7.60 7.60 0 0 0 18.35 16.17L20.69 17.71L17.71 20.69L16.17 18.35A7.60 7.60 0 0 0 13.54 19.44L14.11 22.18L9.89 22.18L10.46 19.44A7.60 7.60 0 0 0 7.83 18.35L6.29 20.69L3.31 17.71L5.65 16.17A7.60 7.60 0 0 0 4.56 13.54L1.82 14.11L1.82 9.89L4.56 10.46A7.60 7.60 0 0 0 5.65 7.83L3.31 6.29L6.29 3.31L7.83 5.65A7.60 7.60 0 0 0 10.46 4.56L9.89 1.82L14.11 1.82L13.54 4.56A7.60 7.60 0 0 0 16.17 5.65L17.71 3.31L20.69 6.29L18.35 7.83A7.60 7.60 0 0 0 19.44 10.46Z"
        stroke="currentColor"
        stroke-width="1.6"
        stroke-linejoin="round"
        stroke-linecap="round"
      />
      <!-- 中心孔 -->
      <circle cx="12" cy="12" r="3.5" stroke="currentColor" stroke-width="1.6" />
    </svg>
  </button>
</template>

<script setup>
import { computed, ref } from 'vue'

defineOptions({ name: 'SettingButton' })

const props = defineProps({
  /** 可选：由父组件控制"设置面板是否打开"，不传则仅做点击反馈 */
  open: { type: Boolean, default: undefined },
  /** 无障碍名称 */
  label: { type: String, default: '设置' }
})

const emit = defineEmits(['click'])

// 点击计数：用来交替旋转方向 + 触发扩散动画重建
const clickCount = ref(0)

function handleClick (event) {
  clickCount.value += 1
  emit('click', event)
}

const ariaLabel = computed(() =>
  props.open === true ? `关闭${props.label}` : props.label
)

const ariaExpanded = computed(() =>
  props.open === undefined ? undefined : String(props.open)
)

// 奇偶次点击反向旋转，形成"拧一下再拧回来"的往复感；
// 角度不累加，避免多次点击后图标转得停不下来。
const iconStyle = computed(() => ({
  transform: `rotate(${clickCount.value % 2 === 0 ? '-45deg' : '45deg'})`
}))
</script>

<style scoped>
  .gear-btn {
    --icon-size: 26px;
    --icon-color: #e8e8e8;

    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    padding: 0;
    /* 无背景：按钮完全透明，图形只由线条构成 */
    background: none;
    border: none;
    color: var(--icon-color);
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }

  .gear-btn__icon {
    display: block;
    width: var(--icon-size);
    height: var(--icon-size);
    /* 只过渡 transform：旋转与按下缩放共用一条弹性缓动 */
    transition: transform 0.34s cubic-bezier(0.34, 1.56, 0.64, 1);
  }

  /* 点击时向外扩散一圈细描边，作为"已触发"的即时反馈 */
  .gear-btn__pulse {
    position: absolute;
    inset: 3px;
    border: 1px solid currentColor;
    border-radius: 50%;
    opacity: 0;
    pointer-events: none;
    animation: gear-pulse 0.5s ease-out both;
  }

  @keyframes gear-pulse {
    0% {
      transform: scale(0.55);
      opacity: 0.55;
    }
    100% {
      transform: scale(1.3);
      opacity: 0;
    }
  }

  .gear-btn:hover {
    color: #ffffff;
  }

  .gear-btn:active .gear-btn__icon {
    /* 按下收紧，松开回弹 */
    transform: scale(0.88);
    transition-duration: 0.12s;
  }

  /* 无背景元素需要更清晰的键盘焦点提示 */
  .gear-btn:focus-visible {
    outline: 2px solid currentColor;
    outline-offset: 3px;
    border-radius: 8px;
  }

  /* 尊重"减少动态效果"偏好：去掉旋转、缩放与扩散 */
  @media (prefers-reduced-motion: reduce) {
    .gear-btn__icon {
      transition: none;
    }

    .gear-btn__pulse {
      animation: none;
    }

    .gear-btn:active .gear-btn__icon {
      transform: none;
    }
  }
</style>
