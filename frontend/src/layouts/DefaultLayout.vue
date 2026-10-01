<template>
  <div class="layout-default">
    <AppHeader />
    <NavMenu />
    <main class="layout-body">
      <!-- 页面左右滑动切换：方向由路由的 meta.depth 决定 -->
      <router-view v-slot="{ Component }">
        <transition :name="transitionName">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <AppFooter />

    <!--
      悬浮控件放在 .layout-body 之外，因此不参与页面滑动：
      滑动的 transform 会把页面内 position: fixed 的子元素变成相对页面定位、
      从而被一起拖走；放在布局层就彻底避免了这个问题，只保留淡入淡出。
    -->
    <transition name="fade" appear>
      <div id="button-wrapper">
        <setting-button />
      </div>
    </transition>

    <transition name="fade" appear>
      <div id="music-wrapper">
        <music-player />
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import NavMenu from '@/components/NavMenu.vue'
import settingButton from '@/components/button/SettingButton.vue'
import musicPlayer from '@/components/music-player/music-player.vue'

const route = useRoute()

// 默认向左（点「链接」的方向）
const transitionName = ref('slide-left')

/**
 * 根据目标路由的深度决定滑动方向：
 *   去更深的页面（首页 -> 链接）向左滑动
 *   回到更浅的页面（链接 -> 首页）向右滑动
 * watch 是 pre-flush 的，会在组件重新渲染（即过渡开始）之前更新好名字。
 */
watch(
  () => route.meta.depth,
  (depth, prevDepth) => {
    if (typeof depth !== 'number' || typeof prevDepth !== 'number') return
    transitionName.value = depth > prevDepth ? 'slide-left' : 'slide-right'
  }
)
</script>

<style scoped>
  .layout-default {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
  }

  .layout-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    /* 作为滑动页面的定位参照；裁掉滑动过程中超出容器的横向部分 */
    position: relative;
    overflow: hidden;
  }

  /* 设置按钮固定在右上角 */
  #button-wrapper {
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 999;
  }
</style>
