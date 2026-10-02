<template>
  <!-- 移除绝对定位，由父组件决定位置 -->
  <div
    class="icon-container"
    :class="{ 'is-link': hasTarget }"
    :style="{ '--size': size + 'px' }"
  >
    <!--
      外链必须自己渲染 <a>，不能交给 RouterLink：
      vue-router 的 to 只用于内部路由，传 "http://x.com" 时它内部会做
      path.replace(/^\//, '') 再拼成 "/http://x.com"，浏览器解析后就变成
      http://当前域名/http://x.com —— 完全错误的地址。
    -->
    <a
      v-if="isExternal"
      class="app-icon"
      :class="`is-${size}`"
      :style="{ backgroundColor: bgColor }"
      :href="externalHref"
      :target="target"
      rel="noopener noreferrer"
      :title="title"
    >
      <img v-if="imgUrl" :src="imgUrl" class="icon-img" alt="" />
      <span v-else class="placeholder-text" :style="{ fontSize: iconFontSize }">
        <slot>Logo</slot>
      </span>
    </a>

    <!-- 内部路由仍交给 router-link，用 v-slot 自行渲染，避免依赖全局注册 -->
    <RouterLink v-else-if="hasTarget" :to="to" custom v-slot="{ href, navigate }">
      <a
        class="app-icon"
        :class="`is-${size}`"
        :style="{ backgroundColor: bgColor }"
        :href="href"
        @click="navigate"
      >
        <img v-if="imgUrl" :src="imgUrl" class="icon-img" alt="" />
        <span v-else class="placeholder-text" :style="{ fontSize: iconFontSize }">
          <slot>Logo</slot>
        </span>
      </a>
    </RouterLink>

    <!-- 无 to：纯展示 -->
    <div v-else class="app-icon" :style="{ backgroundColor: bgColor }">
      <img v-if="imgUrl" :src="imgUrl" class="icon-img" alt="" />
      <span v-else class="placeholder-text" :style="{ fontSize: iconFontSize }">
        <slot>Logo</slot>
      </span>
    </div>

    <div class="app-label">{{ title }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps({
  title: { type: String, required: true },
  imgUrl: { type: String, default: '' },
  bgColor: { type: String, default: '#fb7299' },
  size: { type: Number, default: 60 },
  // 跳转目标：
  //   外部链接 → 传完整 URL，如 'http://frp-dog.com:51921'
  //   内部路由 → 传 { name: 'home' } 等
  to: { type: [String, Object], default: '' },
  // 外链打开方式，默认新开标签页；想在本页打开传 '_self'
  target: { type: String, default: '_blank' }
})

const hasTarget = computed(() => {
  if (typeof props.to === 'string') return props.to.trim() !== ''
  return !!props.to
})

// 显式识别外链：带协议的绝对地址，或以 // 开头的协议相对地址
const isExternal = computed(
  () => typeof props.to === 'string' && /^(?:https?:)?\/\//i.test(props.to.trim())
)

// 去掉空白，避免复制粘贴带入空格导致地址失效
const externalHref = computed(() => String(props.to).trim())

// 图标字号随尺寸等比缩放。
// 注意：直接依赖 props.size 而不是 CSS 的 var(--size)，
// 因为父组件可能用 :deep 覆盖 --size，那样 var() 会失效。
const iconFontSize = computed(() => `${Math.round(props.size * 0.2)}px`)
</script>

<style scoped>
.icon-container {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 只有可点击的图标才显示手型 */
.icon-container.is-link {
  cursor: pointer;
}

.app-icon {
  width: var(--size); /* 使用 CSS 变量，实现尺寸动态化 */
  height: var(--size);
  border-radius: 22%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.3);
  transition: transform 0.2s ease;
  overflow: hidden; /* 防止图片溢出圆角 */
  /* 渲染成 <a> 时去掉链接默认样式 */
  text-decoration: none;
  color: inherit;
}

.icon-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-icon:hover {
  transform: scale(1.05);
}

/* 键盘可达性：Tab 聚焦时给出清晰提示 */
.app-icon:focus-visible {
  outline: 2px solid #ffffff;
  outline-offset: 3px;
}

.placeholder-text {
  color: white;
  font-weight: bold;
  /* 字号由 :style 传入（见 iconFontSize），保证父组件覆盖 --size 时依然正确 */
}

.app-label {
  margin-top: calc(var(--size) * 0.12);
  color: #ffffff;
  font-size: calc(var(--size) * 0.18);
  font-weight: 500;
  text-shadow: 0px 1px 3px rgba(0, 0, 0, 0.6); /* 默认加上文字阴影以适应复杂背景 */
}
</style>
