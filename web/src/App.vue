<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { LogOut, Sparkles } from 'lucide-vue-next'
import ErrorBoundary from '@/components/ErrorBoundary.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const navs = [
  { name: 'home', label: '主页' },
  { name: 'assistant', label: 'AI 助手' },
  { name: 'positions', label: '岗位管理' },
  { name: 'resumes', label: '简历管理' },
  { name: 'reviews', label: '面试复盘' },
  { name: 'jobs', label: '找职位' },
  { name: 'knowledge', label: '知识库' },
  { name: 'deliveries', label: '投递进度' },
]

const isLogin = computed(() => route.name === 'login')

function go(name: string) {
  router.push({ name })
}

function logout() {
  user.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="min-h-screen bg-bgSoft">
    <!-- 顶部固定导航，高 60px -->
    <header
      v-if="!isLogin"
      class="fixed inset-x-0 top-0 z-50 flex h-[60px] items-center justify-between border-b border-slate-200 bg-white/90 px-6 backdrop-blur"
    >
      <div class="flex items-center gap-2">
        <Sparkles class="h-5 w-5 text-primary" />
        <span class="text-base font-semibold text-ink">AI 求职助手</span>
      </div>
      <nav class="flex items-center gap-1">
        <button
          v-for="n in navs"
          :key="n.name"
          class="rounded-lg px-3 py-1.5 text-sm transition-colors hover:bg-primary/10"
          :class="route.name === n.name ? 'bg-primary/10 font-medium text-primary' : 'text-muted'"
          @click="go(n.name)"
        >
          {{ n.label }}
        </button>
      </nav>
      <div class="flex items-center gap-3">
        <span class="text-sm text-muted">{{ user.nickname || '未登录' }}</span>
        <button
          class="flex items-center gap-1 rounded-lg px-2 py-1.5 text-sm text-muted transition-colors hover:bg-slate-100 hover:text-danger"
          @click="logout"
        >
          <LogOut class="h-4 w-4" />退出
        </button>
      </div>
    </header>

    <main :class="isLogin ? '' : 'pt-[60px]'">
      <ErrorBoundary>
        <router-view />
      </ErrorBoundary>
    </main>
  </div>
</template>
