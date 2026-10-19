<template>
  <div class="auth">
    <div class="auth__col">
      <div class="auth__brand">
        <!-- 直接用品牌图：logo 本身已经含「池禾笔记」字样，再单独排一段文字就重复了 -->
        <img class="auth__logo" src="/logo.png" alt="池禾笔记" width="76" height="74" />
      </div>

      <div class="auth__sheet sheet">
        <h1 class="auth__title">{{ title }}</h1>
        <p v-if="subtitle" class="auth__subtitle">{{ subtitle }}</p>

        <div class="auth__body">
          <slot />
        </div>
      </div>

      <p class="auth__foot">
        <slot name="footer" />
      </p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' }
})
</script>

<style scoped>
.auth {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-8) var(--sp-4);
  background: var(--paper);
}

.auth__col {
  width: 100%;
  max-width: 400px;
}

.auth__brand {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--sp-5);
}

/* 品牌图自带圆角与米色底，所以这里不再另加边框/背景，避免出现双层圆角 */
.auth__logo {
  width: 76px;
  height: auto;
  display: block;
  border-radius: 14px;
}

.auth__sheet {
  padding: var(--sp-6);
}

.auth__title {
  font-size: var(--fs-xl);
}

.auth__subtitle {
  margin-top: 6px;
  font-size: var(--fs-sm);
  color: var(--ink-3);
  line-height: 1.7;
}

.auth__body {
  margin-top: var(--sp-5);
}

.auth__foot {
  margin-top: var(--sp-5);
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--ink-3);
}
</style>
