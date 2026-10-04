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
      <!--
        触屏没有 hover，只靠 CSS :hover 展开会导致手机上音乐控件完全点不到。
        因此这里用 JS 维护展开状态：点击悬浮球展开。
        （@click.self 确保点内部控件不会误触）
        桌面端原有的 hover 展开仍然保留，两种方式并存互不冲突。
      -->
      <div
        id="music-wrapper"
        :class="{ 'is-open': musicOpen }"
        @click.self="musicOpen = !musicOpen"
      >
        <music-player :expanded="musicOpen" />
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

// 音乐播放器展开状态（触屏用，桌面端仍可用 hover）
const musicOpen = ref(false)

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

// 切页时收起音乐播放器，避免展开态跨页残留
watch(
  () => route.fullPath,
  () => { musicOpen.value = false }
)
</script>

<style scoped>
  .layout-default {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
    /* 移动浏览器地址栏伸缩时 100vh 会跳变，dvh 跟随可视高度（不支持的浏览器忽略此行） */
    min-height: 100dvh;
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

  /* ===== 手机竖屏：避让刘海/圆角，并给内容留出安全区 ===== */
  @media (max-width: 600px) {
    #button-wrapper {
      /* env() 在无安全区的设备上取回退值，所以桌面/普通机型不会偏移 */
      top: calc(12px + env(safe-area-inset-top, 0px));
      right: calc(12px + env(safe-area-inset-right, 0px));
    }
  }
</style>
