<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { resumeApi, positionApi, agentStream, type ResumeItem, type Position, type EntityId } from '@/api'
import { Upload, Download, Trash2, FileText, Wand2 } from 'lucide-vue-next'
import AiDisclaimer from '@/components/AiDisclaimer.vue'

const list = ref<ResumeItem[]>([])
const filter = ref('')
const errorMsg = ref('')
const pasteName = ref('')
const pasteContent = ref('')
const showPaste = ref(false)
const aiFileName = ref('')
const showImport = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

async function load() {
  try {
    list.value = await resumeApi.list(filter.value || undefined)
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  }
}

onMounted(load)

async function setFilter(v: string) {
  filter.value = v
  await load()
}

async function onFile(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  try {
    await resumeApi.upload(file, file.name, '职场新人')
    await load()
  } catch (err: any) {
    errorMsg.value = err?.message || '上传失败'
  }
}

async function submitPaste() {
  if (!pasteContent.value.trim()) return
  try {
    await resumeApi.paste(pasteContent.value, pasteName.value || '粘贴简历', '职场新人')
    showPaste.value = false
    pasteContent.value = ''
    pasteName.value = ''
    await load()
  } catch (e: any) {
    errorMsg.value = e?.message || '保存失败'
  }
}

async function submitImport() {
  if (!aiFileName.value.trim()) return
  try {
    await resumeApi.importAi(aiFileName.value.trim(), '职场新人')
    showImport.value = false
    aiFileName.value = ''
    await load()
  } catch (e: any) {
    errorMsg.value = e?.message || '导入失败'
  }
}

async function remove(id: string) {
  try {
    await resumeApi.remove(id)
    await load()
  } catch (e: any) {
    errorMsg.value = e?.message || '删除失败'
  }
}

// ===== 去优化：基于已有简历 + 目标岗位，调 Agent 流式生成优化版简历 =====
const positions = ref<Position[]>([])
const showOptimize = ref(false)
const optimizeTarget = ref<ResumeItem | null>(null)
const optimizePositionId = ref<EntityId | undefined>(undefined)
const optimizeStreaming = ref(false)
const optimizePreview = ref('')
const optimizeAbort = ref<AbortController | null>(null)

async function openOptimize(r: ResumeItem) {
  optimizeTarget.value = r
  optimizePreview.value = ''
  optimizePositionId.value = r.positionId ?? undefined
  errorMsg.value = ''
  if (!positions.value.length) {
    try {
      positions.value = await positionApi.list()
    } catch (e: any) {
      errorMsg.value = e?.message || '加载岗位失败'
    }
  }
  showOptimize.value = true
}

async function startOptimize() {
  if (!optimizeTarget.value || optimizeStreaming.value) return
  optimizeStreaming.value = true
  optimizePreview.value = ''
  errorMsg.value = ''
  const ctrl = new AbortController()
  optimizeAbort.value = ctrl
  try {
    await agentStream(
      {
        query:
          '请基于我关联的目标岗位，优化我的简历：突出与岗位要求匹配的技能、项目经历与成果，保持真实、不虚构，输出可直接使用的优化版简历全文。',
        mode: 'resume',
        position_id: optimizePositionId.value,
        resume_id: optimizeTarget.value.id,
      },
      { onDelta: (t: string) => { optimizePreview.value += t } },
      ctrl.signal,
    )
    await saveOptimized()
  } catch (e: any) {
    errorMsg.value = e?.message || '生成失败'
  } finally {
    optimizeStreaming.value = false
    optimizeAbort.value = null
  }
}

async function saveOptimized() {
  if (!optimizeTarget.value) return
  if (!optimizePreview.value.trim()) {
    errorMsg.value = '未生成优化内容，无法保存'
    return
  }
  try {
    await resumeApi.optimizeResult(
      optimizePreview.value,
      optimizeTarget.value.name + '（优化版）',
      optimizeTarget.value.tag || '职场新人',
      optimizePositionId.value,
    )
    showOptimize.value = false
    optimizeTarget.value = null
    await load()
  } catch (e: any) {
    errorMsg.value = e?.message || '保存失败'
  }
}

function cancelOptimize() {
  optimizeAbort.value?.abort()
}

const sourceLabel = (s: string) =>
  s === 'upload' ? '上传' : s === 'paste' ? '粘贴' : s === 'ai' ? 'AI 产物' : s
</script>

<template>
  <div class="mx-auto max-w-6xl px-6 py-8">
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-xl font-semibold text-ink">简历管理</h1>
      <div class="flex gap-2">
        <button class="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark" @click="fileInput?.click()">
          <Upload class="h-4 w-4" />上传简历
        </button>
        <input ref="fileInput" type="file" class="hidden" @change="onFile" />
        <button class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-muted transition-colors hover:bg-white" @click="showPaste = !showPaste">
          粘贴文本
        </button>
        <button class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-muted transition-colors hover:bg-white" @click="showImport = !showImport">
          从 AI 产物导入
        </button>
      </div>
    </div>

    <p v-if="errorMsg" class="mb-3 text-sm text-danger">{{ errorMsg }}</p>

    <!-- 粘贴 -->
    <div v-if="showPaste" class="card mb-4 p-4">
      <input v-model="pasteName" class="mb-2 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="简历名称" />
      <textarea v-model="pasteContent" rows="6" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="粘贴简历内容" />
      <div class="mt-3 flex justify-end gap-2">
        <button class="rounded-lg px-4 py-2 text-sm text-muted hover:bg-slate-100" @click="showPaste = false">取消</button>
        <button class="rounded-lg bg-primary px-4 py-2 text-sm text-white hover:bg-primaryDark" @click="submitPaste">保存</button>
      </div>
    </div>

    <!-- AI 导入 -->
    <div v-if="showImport" class="card mb-4 p-4">
      <p class="mb-2 text-sm text-muted">填写 Agent 产物文件名（如 Resume_20260909.md），系统会从 AI 产物目录只读读取。</p>
      <input v-model="aiFileName" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="Resume_20260909120000.md" />
      <div class="mt-3 flex justify-end gap-2">
        <button class="rounded-lg px-4 py-2 text-sm text-muted hover:bg-slate-100" @click="showImport = false">取消</button>
        <button class="rounded-lg bg-primary px-4 py-2 text-sm text-white hover:bg-primaryDark" @click="submitImport">导入</button>
      </div>
    </div>

    <!-- 分类 -->
    <div class="mb-4 flex gap-2">
      <button
        v-for="f in [
          { v: '', l: '全部简历' },
          { v: 'draft', l: '待优化' },
          { v: 'optimized', l: '已优化' },
        ]"
        :key="f.v"
        class="rounded-lg px-3 py-1.5 text-sm transition-colors"
        :class="filter === f.v ? 'bg-primary text-white' : 'bg-white text-muted hover:bg-slate-100'"
        @click="setFilter(f.v)"
      >
        {{ f.l }}
      </button>
    </div>

    <!-- 列表 -->
    <div v-if="list.length" class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <div v-for="r in list" :key="r.id" class="card card-hover p-5">
        <div class="flex items-start justify-between">
          <div class="flex items-start gap-3">
            <FileText class="mt-0.5 h-5 w-5 text-primary" />
            <div>
              <p class="font-medium text-ink">{{ r.name }}</p>
              <p class="mt-1 text-xs text-muted">
                {{ sourceLabel(r.sourceType) }} · {{ r.tag || '未标注' }} ·
                {{ r.status === 'optimized' ? '已优化' : '待优化' }}
              </p>
              <AiDisclaimer v-if="r.sourceType === 'ai' || r.status === 'optimized'" dense />
            </div>
          </div>
          <div class="flex gap-1">
            <button class="rounded-lg p-2 text-muted transition-colors hover:bg-slate-100 hover:text-primary" title="去优化" @click="openOptimize(r)">
              <Wand2 class="h-4 w-4" />
            </button>
            <a :href="resumeApi.downloadUrl(r.id)" class="rounded-lg p-2 text-muted transition-colors hover:bg-slate-100 hover:text-primary" title="下载">
              <Download class="h-4 w-4" />
            </a>
            <button class="rounded-lg p-2 text-muted transition-colors hover:bg-slate-100 hover:text-danger" title="删除" @click="remove(r.id)">
              <Trash2 class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
    <p v-else class="card py-16 text-center text-sm text-muted">还没有简历，先上传或粘贴一份。</p>

    <!-- 去优化弹窗：选目标岗位 → SSE 流式生成 → 落库为「已优化」 -->
    <div
      v-if="showOptimize"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
      @click.self="!optimizeStreaming && (showOptimize = false)"
    >
      <div class="animate-fade-in-up w-full max-w-2xl rounded-2xl bg-white p-6 shadow-xl">
        <h2 class="mb-4 text-lg font-semibold text-ink">去优化：{{ optimizeTarget?.name }}</h2>

        <div class="mb-4">
          <label class="mb-1 block text-sm text-muted">目标岗位</label>
          <select
            v-model="optimizePositionId"
            :disabled="optimizeStreaming"
            class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
          >
            <option :value="undefined">不关联岗位（通用优化）</option>
            <option v-for="p in positions" :key="p.id" :value="p.id">{{ p.title }}</option>
          </select>
          <p class="mt-1 text-xs text-muted">优化将结合目标岗位 JD 与你的简历上下文进行，建议选择对应岗位。</p>
        </div>

        <!-- 流式预览区 -->
        <div class="min-h-[160px] max-h-[50vh] overflow-y-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm leading-relaxed text-ink">
          <span v-if="!optimizePreview && !optimizeStreaming" class="text-muted">点击「开始优化」后，这里会实时显示 AI 生成的优化版简历。</span>
          <template v-else>{{ optimizePreview }}<span v-if="optimizeStreaming" class="stream-cursor"></span></template>
        </div>

        <p v-if="errorMsg" class="mt-3 text-sm text-danger">{{ errorMsg }}</p>

        <div class="mt-5 flex justify-end gap-3">
          <button
            v-if="optimizeStreaming"
            class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-muted transition-colors hover:bg-white"
            @click="cancelOptimize"
          >
            取消
          </button>
          <button
            v-else
            class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-muted transition-colors hover:bg-white"
            @click="showOptimize = false"
          >
            关闭
          </button>
          <button
            :disabled="optimizeStreaming"
            class="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark disabled:opacity-60"
            @click="startOptimize"
          >
            <Wand2 class="h-4 w-4" />{{ optimizeStreaming ? '生成中…' : '开始优化' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
