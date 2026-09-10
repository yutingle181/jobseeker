<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { Sparkles } from 'lucide-vue-next'

const router = useRouter()
const user = useUserStore()

const isRegister = ref(false)
const username = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function submit() {
  errorMsg.value = ''
  loading.value = true
  try {
    const data = isRegister.value
      ? await authApi.register({ username: username.value, password: password.value, nickname: nickname.value })
      : await authApi.login({ username: username.value, password: password.value })
    user.setLogin(data.token, data.nickname)
    router.push({ name: 'home' })
  } catch (e: any) {
    errorMsg.value = e?.message || '操作失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gradient-to-br from-primary/10 via-bgSoft to-primary/5 px-4">
    <div class="card w-full max-w-md p-8">
      <div class="mb-6 flex items-center gap-2">
        <Sparkles class="h-6 w-6 text-primary" />
        <h1 class="text-xl font-semibold text-ink">AI 求职助手</h1>
      </div>

      <div class="mb-5 flex gap-2">
        <button
          class="flex-1 rounded-lg py-2 text-sm transition-colors"
          :class="!isRegister ? 'bg-primary text-white' : 'bg-slate-100 text-muted'"
          @click="isRegister = false"
        >
          登录
        </button>
        <button
          class="flex-1 rounded-lg py-2 text-sm transition-colors"
          :class="isRegister ? 'bg-primary text-white' : 'bg-slate-100 text-muted'"
          @click="isRegister = true"
        >
          注册
        </button>
      </div>

      <form class="space-y-4" @submit.prevent="submit">
        <div>
          <label class="mb-1 block text-sm text-muted">用户名</label>
          <input
            v-model="username"
            class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none transition-colors focus:border-primary"
            placeholder="请输入用户名"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm text-muted">密码</label>
          <input
            v-model="password"
            type="password"
            class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none transition-colors focus:border-primary"
            placeholder="请输入密码"
          />
        </div>
        <div v-if="isRegister">
          <label class="mb-1 block text-sm text-muted">昵称（可选）</label>
          <input
            v-model="nickname"
            class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none transition-colors focus:border-primary"
            placeholder="展示名称"
          />
        </div>

        <p v-if="errorMsg" class="text-sm text-danger">{{ errorMsg }}</p>

        <button
          type="submit"
          :disabled="loading"
          class="w-full rounded-lg bg-primary py-2.5 text-sm font-medium text-white transition-colors hover:bg-primaryDark disabled:opacity-60"
        >
          {{ loading ? '处理中…' : isRegister ? '注册并登录' : '登录' }}
        </button>
      </form>
    </div>
  </div>
</template>
