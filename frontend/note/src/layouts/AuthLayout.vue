<template>
  <div class="auth">
    <div class="auth__col">
      <div class="auth__brand">
        <span class="auth__mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round">
            <path d="M6 3h9a2 2 0 0 1 2 2v16l-6.5-3.6L4 21V5a2 2 0 0 1 2-2z" />
          </svg>
        </span>
        <span class="auth__name">CipherWS 笔记</span>
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
  gap: var(--sp-2);
  margin-bottom: var(--sp-5);
  color: var(--accent);
}

.auth__name {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--ink);
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
