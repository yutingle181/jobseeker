<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import {
  deliveryApi,
  positionApi,
  resumeApi,
  type Delivery,
  type DeliveryStats,
  type Position,
  type ResumeItem,
} from '@/api'
import { Plus, Edit2, Trash2, Briefcase, Trophy, Activity, XCircle, X } from 'lucide-vue-next'

const STATUSES = [
  '待投递', '已投递', '简历筛选', '笔试', '测评',
  '一面', '二面', '三面', 'HR面', 'Offer', '拒信', '放弃',
]
const CHANNELS = ['官网投递', '牛客', 'BOSS', '内推', '校招官网', '猎聘', '其他']

const list = ref<Delivery[]>([])
const stats = ref<DeliveryStats | null>(null)
const positions = ref<Position[]>([])
const resumes = ref<ResumeItem[]>([])
const errorMsg = ref('')
const loading = ref(false)

const statusFilter = ref('')
const cityFilter = ref('')
const keyword = ref('')

const posMap = computed<Record<string, string>>(() =>
  Object.fromEntries(positions.value.map((p) => [p.id, p.title])),
)
const resMap = computed<Record<string, string>>(() =>
  Object.fromEntries(resumes.value.map((r) => [r.id, r.name])),
)
const cityOptions = computed(() =>
  Array.from(new Set(list.value.map((d) => d.city).filter(Boolean))) as string[],
)

const filtered = computed(() =>
  list.value.filter((d) => {
    if (statusFilter.value && d.status !== statusFilter.value) return false
    if (cityFilter.value && d.city !== cityFilter.value) return false
    const k = keyword.value.trim().toLowerCase()
    if (k && !`${d.companyName || ''} ${d.jobTitle}`.toLowerCase().includes(k)) return false
    return true
  }),
)

async function loadAll() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [l, s, p, r] = await Promise.all([
      deliveryApi.list(),
      deliveryApi.stats(),
      positionApi.list(),
      resumeApi.list(),
    ])
    list.value = l
    stats.value = s
    positions.value = p
    resumes.value = r
  } catch (e: any) {
    errorMsg.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(loadAll)

function statusTagClass(s: string): string {
  if (s === 'Offer') return 'bg-green-100 text-green-700'
  if (s === '已投递') return 'bg-yellow-100 text-yellow-700'
  if (['笔试', '测评', '一面', '二面', '三面', 'HR面'].includes(s))
    return 'bg-blue-100 text-blue-700'
  if (['拒信', '放弃'].includes(s)) return 'bg-red-100 text-red-700'
  return 'bg-slate-100 text-slate-600'
}
// 状态色：Offer 绿 / 已投递 黄 / 面试流程各轮 蓝 / 拒信·放弃 红 / 其余 灰
function statusColor(s: string): string {
  if (s === 'Offer') return '#16A34A'
  if (s === '已投递') return '#CA8A04'
  if (['笔试', '测评', '一面', '二面', '三面', 'HR面'].includes(s)) return '#2563EB'
  if (['拒信', '放弃'].includes(s)) return '#DC2626'
  return '#94A3B8'
}

// ---- 新增 / 编辑对话框 ----
const showDialog = ref(false)
const editingId = ref<string | null>(null)
const form = ref<Partial<Delivery>>({ status: '待投递' })
const formError = ref('')

function openCreate() {
  editingId.value = null
  form.value = { status: '待投递' }
  formError.value = ''
  showDialog.value = true
}
function openEdit(d: Delivery) {
  editingId.value = d.id
  form.value = { ...d }
  formError.value = ''
  showDialog.value = true
}
async function save() {
  formError.value = ''
  if (!form.value.jobTitle || !form.value.jobTitle.trim()) {
    formError.value = '岗位不能为空'
    return
  }
  try {
    if (editingId.value) await deliveryApi.update(editingId.value, form.value)
    else await deliveryApi.create(form.value)
    showDialog.value = false
    await loadAll()
  } catch (e: any) {
    formError.value = e?.message || '保存失败'
  }
}
async function remove(d: Delivery) {
  if (!confirm(`确认删除「${d.companyName || ''} ${d.jobTitle}」的投递记录？`)) return
  try {
    await deliveryApi.remove(d.id)
    await loadAll()
  } catch (e: any) {
    errorMsg.value = e?.message || '删除失败'
  }
}
</script>

<template>
  <div class="mx-auto max-w-6xl px-6 py-8">
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-xl font-semibold text-ink">投递进度</h1>
      <button
        class="flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark"
        @click="openCreate"
      >
        <Plus class="h-4 w-4" />新增投递
      </button>
    </div>

    <p v-if="errorMsg" class="mb-3 text-sm text-danger">{{ errorMsg }}</p>

    <!-- 统计看板 -->
    <div class="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
      <div class="card flex items-center gap-3 p-4">
        <Briefcase class="h-8 w-8 text-primary" />
        <div>
          <p class="text-xs text-muted">总投递</p>
          <p class="text-2xl font-semibold text-ink">{{ stats?.total ?? 0 }}</p>
        </div>
      </div>
      <div class="card flex items-center gap-3 p-4">
        <Trophy class="h-8 w-8 text-green-600" />
        <div>
          <p class="text-xs text-muted">Offer</p>
          <p class="text-2xl font-semibold text-ink">{{ stats?.offerCount ?? 0 }}</p>
        </div>
      </div>
      <div class="card flex items-center gap-3 p-4">
        <Activity class="h-8 w-8 text-blue-600" />
        <div>
          <p class="text-xs text-muted">面试中</p>
          <p class="text-2xl font-semibold text-ink">{{ stats?.interviewingCount ?? 0 }}</p>
        </div>
      </div>
      <div class="card flex items-center gap-3 p-4">
        <XCircle class="h-8 w-8 text-red-600" />
        <div>
          <p class="text-xs text-muted">拒信/放弃</p>
          <p class="text-2xl font-semibold text-ink">{{ stats?.rejectedCount ?? 0 }}</p>
        </div>
      </div>
    </div>

    <!-- 状态分布：阶段徽标流（非零上色高亮，为 0 弱化为描边） -->
    <div class="card mb-6 p-5">
      <div class="mb-4 flex items-baseline justify-between">
        <p class="text-sm font-medium text-ink">状态分布</p>
        <p class="text-xs text-muted">共 {{ stats?.total ?? 0 }} 条</p>
      </div>
      <div v-if="stats" class="flex flex-wrap gap-2">
        <span
          v-for="(count, st) in stats.byStatus"
          :key="st"
          class="inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs transition-colors"
          :class="
            count > 0
              ? 'border-transparent font-medium text-white'
              : 'border-slate-200 bg-slate-50 text-slate-400'
          "
          :style="count > 0 ? { background: statusColor(st) } : undefined"
        >
          {{ st }}
          <span class="font-semibold tabular-nums">{{ count }}</span>
        </span>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <input
        v-model="keyword"
        class="w-48 rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
        placeholder="搜索公司 / 岗位"
      />
      <select
        v-model="statusFilter"
        class="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
      >
        <option value="">全部状态</option>
        <option v-for="s in STATUSES" :key="s" :value="s">{{ s }}</option>
      </select>
      <select
        v-model="cityFilter"
        class="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
      >
        <option value="">全部城市</option>
        <option v-for="c in cityOptions" :key="c" :value="c">{{ c }}</option>
      </select>
    </div>

    <!-- 列表 -->
    <div class="card overflow-hidden">
      <p v-if="loading" class="p-8 text-center text-sm text-muted">加载中…</p>
      <p v-else-if="!filtered.length" class="p-8 text-center text-sm text-muted">
        {{ list.length ? '没有符合筛选条件的记录' : '还没有投递记录，点击右上角「新增投递」开始记录。' }}
      </p>
      <table v-else class="w-full text-left text-sm">
        <thead class="bg-bgSoft text-xs text-muted">
          <tr>
            <th class="px-4 py-3">公司</th>
            <th class="px-4 py-3">岗位</th>
            <th class="px-4 py-3">城市</th>
            <th class="px-4 py-3">渠道</th>
            <th class="px-4 py-3">投递日期</th>
            <th class="px-4 py-3">状态</th>
            <th class="px-4 py-3">面试轮次</th>
            <th class="px-4 py-3">薪资</th>
            <th class="px-4 py-3 text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in filtered" :key="d.id" class="border-t border-slate-100 hover:bg-slate-50">
            <td class="px-4 py-3 text-ink">{{ d.companyName || '—' }}</td>
            <td class="px-4 py-3 text-ink">{{ d.jobTitle }}</td>
            <td class="px-4 py-3 text-muted">{{ d.city || '—' }}</td>
            <td class="px-4 py-3 text-muted">{{ d.channel || '—' }}</td>
            <td class="px-4 py-3 text-muted">{{ d.deliverDate || '—' }}</td>
            <td class="px-4 py-3">
              <span
                class="rounded-full px-2 py-0.5 text-xs font-medium"
                :class="statusTagClass(d.status)"
              >{{ d.status || '待投递' }}</span>
            </td>
            <td class="px-4 py-3 text-muted">{{ d.interviewRound || '—' }}</td>
            <td class="px-4 py-3 text-muted">{{ d.salary || '—' }}</td>
            <td class="px-4 py-3">
              <div class="flex justify-end gap-2">
                <button
                  class="rounded-lg p-1.5 text-muted transition-colors hover:bg-slate-100 hover:text-primary"
                  title="编辑"
                  @click="openEdit(d)"
                >
                  <Edit2 class="h-4 w-4" />
                </button>
                <button
                  class="rounded-lg p-1.5 text-muted transition-colors hover:bg-slate-100 hover:text-danger"
                  title="删除"
                  @click="remove(d)"
                >
                  <Trash2 class="h-4 w-4" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新增 / 编辑对话框 -->
    <div
      v-if="showDialog"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="showDialog = false"
    >
      <div class="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-ink">{{ editingId ? '编辑投递' : '新增投递' }}</h2>
          <button class="rounded-lg p-1.5 text-muted hover:bg-slate-100" @click="showDialog = false">
            <X class="h-5 w-5" />
          </button>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="col-span-2">
            <label class="mb-1 block text-xs text-muted">岗位 *</label>
            <input
              v-model="form.jobTitle"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
              placeholder="如 后端开发(校招)"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">公司</label>
            <input
              v-model="form.companyName"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">城市</label>
            <input
              v-model="form.city"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">投递渠道</label>
            <select
              v-model="form.channel"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            >
              <option value="">请选择</option>
              <option v-for="c in CHANNELS" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">投递日期</label>
            <input
              v-model="form.deliverDate"
              type="date"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">状态</label>
            <select
              v-model="form.status"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            >
              <option v-for="s in STATUSES" :key="s" :value="s">{{ s }}</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">面试轮次</label>
            <input
              v-model="form.interviewRound"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
              placeholder="如 二面"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">最近面试时间</label>
            <input
              v-model="form.lastInterviewTime"
              type="date"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">笔试 / 测评</label>
            <input
              v-model="form.examInfo"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
              placeholder="如 9/20 行测"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">结果去向</label>
            <input
              v-model="form.result"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">薪资 (Offer)</label>
            <input
              v-model="form.salary"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
              placeholder="如 12k×13"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">关联岗位</label>
            <select
              v-model="form.positionId"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            >
              <option :value="null">不关联</option>
              <option v-for="p in positions" :key="p.id" :value="p.id">{{ p.title }}</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-xs text-muted">关联简历</label>
            <select
              v-model="form.resumeId"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            >
              <option :value="null">不关联</option>
              <option v-for="r in resumes" :key="r.id" :value="r.id">{{ r.name }}</option>
            </select>
          </div>
          <div class="col-span-2">
            <label class="mb-1 block text-xs text-muted">备注</label>
            <textarea
              v-model="form.remark"
              rows="2"
              class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-primary"
            />
          </div>
        </div>

        <p v-if="formError" class="mt-3 text-sm text-danger">{{ formError }}</p>

        <div class="mt-5 flex justify-end gap-3">
          <button
            class="rounded-lg border border-slate-200 px-4 py-2 text-sm text-muted transition-colors hover:bg-slate-50"
            @click="showDialog = false"
          >
            取消
          </button>
          <button
            class="rounded-lg bg-primary px-4 py-2 text-sm text-white transition-colors hover:bg-primaryDark"
            @click="save"
          >
            保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
