import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 轻量全局提示。
 * 需求 6.3.1 要求「额度不足、登录失效、保存失败给出明确提示」，
 * 所以所有失败路径都走这里，而不是各页面自己 alert。
 */
export const useToastStore = defineStore('toast', () => {
  const items = ref([])
  let seq = 0

  function push(message, type = 'info', ttl = 3200) {
    const id = ++seq
    items.value.push({ id, message, type })
    if (ttl > 0) {
      setTimeout(() => dismiss(id), ttl)
    }
    return id
  }

  function dismiss(id) {
    items.value = items.value.filter((t) => t.id !== id)
  }

  const success = (m) => push(m, 'success')
  const error = (m) => push(m, 'error', 4200)
  const info = (m) => push(m, 'info')
  const warn = (m) => push(m, 'warn', 3800)

  return { items, push, dismiss, success, error, info, warn }
})
