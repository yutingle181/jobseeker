<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { positionApi, resumeApi } from '@/api'
import { Check, ArrowLeft, ArrowRight } from 'lucide-vue-next'

const router = useRouter()
const step = ref(1)
const saving = ref(false)
const errorMsg = ref('')

const categories = ['后端工程师', '前端工程师', '测试工程师', '数据开发', '算法工程师', '新媒体运营', '会计', '产品经理']

const form = ref({
  category: '后端工程师',
  title: '',
  description: '',
  companyName: '',
  companyIntro: '',
  city: '',
})

const resumeText = ref('')
const resumeTag = ref('应届生')
const createdPositionId = ref<number | null>(null)

async function next() {
  errorMsg.value = ''
  if (step.value === 1) {
    if (!form.value.title.trim()) {
      errorMsg.value = '请填写岗位名称'
      return
    }
    step.value = 2
    return
  }
  if (step.value === 2) {
    saving.value = true
    try {
      createdPositionId.value = await positionApi.create(form.value)
      if (resumeText.value.trim()) {
        await resumeApi.paste(resumeText.value, form.value.title + '-简历', resumeTag.value, createdPositionId.value)
      }
      step.value = 3
    } catch (e: any) {
      errorMsg.value = e?.message || '保存失败'
    } finally {
      saving.value = false
    }
  }
}

function back() {
  if (step.value > 1) step.value -= 1
}
</script>

<template>
  <div class="mx-auto max-w-3xl px-6 py-8">
    <!-- 步骤条 -->
    <div class="mb-8 flex items-center justify-center gap-3">
      <div v-for="(label, i) in ['填写岗位信息', '选择简历', '准备完成']" :key="label" class="flex items-center gap-2">
        <div
          class="flex h-8 w-8 items-center justify-center rounded-full text-sm font-medium transition-colors"
          :class="step >= i + 1 ? 'bg-primary text-white' : 'bg-slate-200 text-muted'"
        >
          <Check v-if="step > i + 1" class="h-4 w-4" />
          <span v-else>{{ i + 1 }}</span>
        </div>
        <span class="text-sm" :class="step >= i + 1 ? 'text-ink' : 'text-muted'">{{ label }}</span>
        <div v-if="i < 2" class="mx-2 h-px w-10 bg-slate-200" />
      </div>
    </div>

    <div class="card p-6">
      <!-- 第一步：岗位信息 -->
      <div v-if="step === 1" class="space-y-4">
        <div>
          <label class="mb-1 block text-sm text-muted">岗位分类</label>
          <select v-model="form.category" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary">
            <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-sm text-muted">岗位名称</label>
          <input v-model="form.title" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="如：长沙初级 Java 开发工程师" />
        </div>
        <div>
          <label class="mb-1 block text-sm text-muted">岗位描述（{{ form.description.length }}/2000）</label>
          <textarea v-model="form.description" maxlength="2000" rows="5" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="粘贴 JD 正文" />
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="mb-1 block text-sm text-muted">公司名称</label>
            <input v-model="form.companyName" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" />
          </div>
          <div>
            <label class="mb-1 block text-sm text-muted">城市</label>
            <input v-model="form.city" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="如：长沙" />
          </div>
        </div>
        <div>
          <label class="mb-1 block text-sm text-muted">公司简介（{{ form.companyIntro.length }}/2000）</label>
          <textarea v-model="form.companyIntro" maxlength="2000" rows="3" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" />
        </div>
      </div>

      <!-- 第二步：简历 -->
      <div v-else-if="step === 2" class="space-y-4">
        <div>
          <label class="mb-1 block text-sm text-muted">粘贴简历文本（{{ resumeText.length }}/3000）</label>
          <textarea v-model="resumeText" maxlength="3000" rows="10" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary" placeholder="直接粘贴简历内容，可留空稍后上传" />
        </div>
        <div>
          <label class="mb-1 block text-sm text-muted">身份标签</label>
          <div class="flex gap-2">
            <button
              v-for="t in ['应届生', '职场新人', '资深专家']"
              :key="t"
              class="rounded-lg px-3 py-1.5 text-sm transition-colors"
              :class="resumeTag === t ? 'bg-primary text-white' : 'bg-slate-100 text-muted hover:bg-slate-200'"
              @click="resumeTag = t"
            >
              {{ t }}
            </button>
          </div>
        </div>
      </div>

      <!-- 第三步：完成 -->
      <div v-else class="py-8 text-center">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-success/10">
          <Check class="h-6 w-6 text-success" />
        </div>
        <p class="font-medium text-ink">岗位已创建完成</p>
        <p class="mt-1 text-sm text-muted">接下来可以让 AI 助手基于这个岗位陪你模拟面试。</p>
        <div class="mt-6 flex justify-center gap-3">
          <button class="rounded-lg border border-primary/30 px-4 py-2 text-sm text-primary transition-colors hover:bg-primary/5" @click="router.push({ name: 'positions' })">
            返回岗位管理
          </button>
          <button class="rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark" @click="router.push({ name: 'assistant' })">
            开始 AI 助手
          </button>
        </div>
      </div>

      <p v-if="errorMsg" class="mt-4 text-sm text-danger">{{ errorMsg }}</p>
    </div>

    <!-- 底部操作栏 -->
    <div v-if="step < 3" class="mt-6 flex justify-center gap-3">
      <button v-if="step > 1" class="flex items-center gap-1 rounded-lg border border-slate-200 px-5 py-2 text-sm text-muted transition-colors hover:bg-white" @click="back">
        <ArrowLeft class="h-4 w-4" />上一步
      </button>
      <button :disabled="saving" class="flex items-center gap-1 rounded-lg bg-primary px-5 py-2 text-sm text-white transition-colors hover:bg-primaryDark disabled:opacity-60" @click="next">
        {{ saving ? '保存中…' : step === 2 ? '完成' : '下一步' }}<ArrowRight class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>
