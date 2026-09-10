<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'

const hasError = ref(false)
const message = ref('')

// 捕获子孙组件渲染/生命周期中的异常，避免整棵子树崩溃白屏
onErrorCaptured((err) => {
  console.error('[组件错误]', err)
  message.value = err instanceof Error ? err.message : String(err)
  hasError.value = true
  // 返回 false：阻止错误继续向上冒泡
  return false
})

function reload() {
  hasError.value = false
  message.value = ''
  location.reload()
}
</script>

<template>
  <div
    v-if="hasError"
    class="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-center"
  >
    <p class="text-lg font-medium text-ink">页面渲染出现异常</p>
    <p class="max-w-md text-sm text-muted">{{ message || '请尝试刷新页面' }}</p>
    <button
      class="rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primary/90"
      @click="reload"
    >
      刷新重试
    </button>
  </div>
  <slot v-else />
</template>
