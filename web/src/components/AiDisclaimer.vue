<script setup lang="ts">
import { Sparkles } from 'lucide-vue-next'

/**
 * AI 生成内容的统一免责声明。
 *
 * 凡展示模型产出（对话、复盘结论、AI 简历产物等）的位置都应带上，
 * 避免用户把生成内容当作事实或真实职位直接采信。
 *
 * text 可覆盖为更强的场景化提示（如找职位页需强调"非实时在招职位"）。
 */
withDefaults(
  defineProps<{
    /** 声明文案 */
    text?: string
    /** 紧凑模式：用于卡片内等空间受限处 */
    dense?: boolean
  }>(),
  { text: '内容由 AI 生成，仅供参考', dense: false },
)
</script>

<template>
  <p
    class="flex items-start gap-1.5 text-xs text-muted"
    :class="dense ? 'mt-1.5' : 'mt-2 justify-center'"
  >
    <Sparkles class="mt-px h-3.5 w-3.5 shrink-0 text-primary/70" />
    <span>{{ text }}</span>
  </p>
</template>
