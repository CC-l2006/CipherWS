<template>
  <div class="icon-grid">
    <LinkIcon title="毕业设计" bg-color="#fb7299" :size="66" />
    <LinkIcon title="关于我" bg-color="#4e6ef2" :size="66" img-url="/tab-icon/icon.jpg" />
  </div>
</template>

<script setup>
import LinkIcon from '@/components/button/LinkIcon.vue'
</script>

<style scoped>
  .icon-grid {
    --icon-size: 66px;
    /* 单个图标占的格子宽度 */
    --icon-cell: 84px;
    /* 列数上限：PC 端一行最多 10 个，多出来的换到下一行 */
    --max-cols: 10;
    /* 视觉微调：在居中基础上整体上移 20px（改这个值即可调整偏移量） */
    position: relative;
    top: -20px;
    flex: 1;
    width: 100%;
    max-width: calc(
      var(--max-cols) * var(--icon-cell) + (var(--max-cols) - 1) * 12px + 32px
    );
    margin-inline: auto;
    display: grid;
    grid-template-columns: repeat(
      auto-fit,
      minmax(min(100%, var(--icon-cell)), 1fr)
    );
    justify-items: center;
    justify-content: center;
    align-content: center;
    /* 横向 12px + 单元格宽度 = 每行 10 个刚好铺满；纵向 32px 控制行间距 */
    gap: 32px 12px;
    padding: 48px 16px;
  }

  /* 覆盖组件内部写死的 :style="--size"，尺寸完全交给本页控制 */
  .icon-grid :deep(.icon-container) {
    --size: var(--icon-size);
  }

  /* ===== 移动端适配：列数封顶 + 图标缩小 ===== */
  @media (max-width: 1200px) {
    .icon-grid {
      --max-cols: 8;
      gap: 28px 10px;
    }
  }

  @media (max-width: 900px) {
    .icon-grid {
      --max-cols: 6;
    }
  }

  /* 断点里一起缩小格子宽度，否则 3~4 列会撑出容器 */
  @media (max-width: 700px) {
    .icon-grid {
      --max-cols: 4;
      --icon-size: 56px;
      --icon-cell: 70px;
      gap: 24px 10px;
      padding: 32px 12px;
    }
  }

  @media (max-width: 420px) {
    .icon-grid {
      --max-cols: 3;
      --icon-size: 52px;
      --icon-cell: 64px;
      gap: 20px 8px;
    }
  }
</style>
