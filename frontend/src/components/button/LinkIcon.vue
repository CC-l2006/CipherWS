<template>
  <!-- 移除绝对定位，由父组件决定位置 -->
  <div class="icon-container" :style="{ '--size': size + 'px' }">
    <div class="app-icon" :style="{ backgroundColor: bgColor }">
      <!-- 支持图片或文字插槽 -->
      <img v-if="imgUrl" :src="imgUrl" class="icon-img" alt="icon" />
      <span v-else class="placeholder-text">
        <slot>Logo</slot> 
      </span>
    </div>
    <div class="app-label">{{ title }}</div>
  </div>
</template>

<script setup>
// 定义 Props，让数据从外部流入
defineProps({
  title: { type: String, required: true },
  imgUrl: { type: String, default: '' },
  bgColor: { type: String, default: '#fb7299' },
  size: { type: Number, default: 60 } // 默认大小为 66px
})
</script>

<style scoped>
.icon-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
}

.app-icon {
  width: var(--size);       /* 使用 CSS 变量，实现尺寸动态化 */
  height: var(--size);
  border-radius: 22%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.3);
  transition: transform 0.2s ease;
  overflow: hidden; /* 防止图片溢出圆角 */
}

.icon-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-icon:hover {
  transform: scale(1.05);
}

.placeholder-text {
  color: white;
  font-weight: bold;
  font-size: calc(var(--size) * 0.2); /* 字体大小随容器大小等比缩放 */
}

.app-label {
  margin-top: calc(var(--size) * 0.12); 
  color: #ffffff;
  font-size: calc(var(--size) * 0.18);
  font-weight: 500;
  text-shadow: 0px 1px 3px rgba(0, 0, 0, 0.6); /* 默认加上文字阴影以适应复杂背景 */
}
</style>