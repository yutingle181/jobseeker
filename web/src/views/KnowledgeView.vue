<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { knowledgeApi } from '@/api'
import { Upload, Trash2, Database } from 'lucide-vue-next'

const docs = ref<{ id: number; agentKbName: string; fileName: string }[]>([])
const kbName = ref('default')
const errorMsg = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

async function load() {
  try {
    docs.value = await knowledgeApi.list()
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  }
}

onMounted(load)

async function onFile(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  try {
    await knowledgeApi.record(kbName.value, file)
    await load()
  } catch (err: any) {
    errorMsg.value = err?.message || '上传失败'
  }
}

async function remove(id: number) {
  try {
    await knowledgeApi.remove(id)
    await load()
  } catch (e: any) {
    errorMsg.value = e?.message || '删除失败'
  }
}
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-xl font-semibold text-ink">知识库文档</h1>
      <button class="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark" @click="fileInput?.click()">
        <Upload class="h-4 w-4" />上传文档
      </button>
      <input ref="fileInput" type="file" class="hidden" @change="onFile" />
    </div>

    <p class="mb-4 text-sm text-muted">
      上传的文档会记录在这里；真正的建库与带引用问答由 AI 助手完成（建库接口经网关转发给 Agent）。
    </p>
    <p v-if="errorMsg" class="mb-3 text-sm text-danger">{{ errorMsg }}</p>

    <div class="card p-5">
      <div v-if="docs.length" class="space-y-2">
        <div v-for="d in docs" :key="d.id" class="flex items-center justify-between rounded-xl bg-bgSoft px-4 py-3">
          <div class="flex items-center gap-3">
            <Database class="h-4 w-4 text-primary" />
            <div>
              <p class="text-sm font-medium text-ink">{{ d.fileName }}</p>
              <p class="text-xs text-muted">知识库：{{ d.agentKbName }}</p>
            </div>
          </div>
          <button class="rounded-lg p-2 text-muted transition-colors hover:bg-white hover:text-danger" @click="remove(d.id)">
            <Trash2 class="h-4 w-4" />
          </button>
        </div>
      </div>
      <p v-else class="py-12 text-center text-sm text-muted">还没有文档，上传一份 JD 或公司资料开始吧。</p>
    </div>
  </div>
</template>
