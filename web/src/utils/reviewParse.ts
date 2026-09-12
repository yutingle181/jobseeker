/**
 * 面试复盘报告解析。
 *
 * Agent 的 interview_review 模式产出的是固定小标题的 Markdown：
 *
 * ```
 * ## 面试复盘：72/100
 * 整体思路清晰，技术细节需要夯实。
 * ### 分项得分
 * - 技术深度：70/100
 * ### 追问链还原
 * 1. 自我介绍
 * ### 薄弱点
 * - 对向量检索原理回答含糊
 * ### 改进动作
 * - [ ] 复盘 HNSW 原理
 * ```
 *
 * 复盘详情接口只回存纯文本 `detail`，因此在前端把它还原成结构化区块，
 * 让「评分 / 追问链 / 薄弱点 / 改进动作」四段式卡片可以直接渲染。
 * 解析不到任何内容时返回全空，调用方回退到后端抽取的 suggestions / weaknesses。
 */

export interface ReviewDimension {
  label: string
  score: number
}

export interface ParsedReview {
  /** 总分（0-100），解析不到为 null */
  overall: number | null
  dimensions: ReviewDimension[]
  /** 追问链还原 */
  questionChain: string[]
  /** 薄弱点 */
  weaknesses: string[]
  /** 改进动作（去掉勾选框后的问题清单） */
  actions: string[]
}

/**
 * 小标题关键字 -> 目标区块。
 *
 * 结构化输出不可用时模型会退回自由发挥，小标题措辞会漂移
 * （「追问链还原」写成「面试问题链回顾」等），因此这里做**同义收敛**，
 * 保证两种产出都能落到同一套卡片上。
 */
const SECTION_KEYS: { match: string[]; field: 'questionChain' | 'weaknesses' | 'actions' }[] = [
  { match: ['追问链', '追问路径', '提问链', '问题链', '问题回顾', '提问回顾'], field: 'questionChain' },
  { match: ['薄弱点', '薄弱环节', '弱点', '不足'], field: 'weaknesses' },
  { match: ['改进动作', '改进建议', '行动项', '下一步', '建议'], field: 'actions' },
]

const DIMENSION_SECTION = ['分项得分', '分项评分', '能力维度']
const HEADING = /^#{1,6}\s*/

function tidy(line: string) {
  return line
    .replace(HEADING, '')
    .replace(/^[-*•\d.、)\s]+/, '')
    .replace(/^\[[ xX]\]\s*/, '')
    // 模型常在小标题式条目里用粗体强调（如「**技术深度不足**：…」），
    // 卡片本身已有样式层级，这里去掉残留的 Markdown 强调符，避免出现裸 **
    .replace(/\*\*/g, '')
    .replace(/__/g, '')
    .replace(/`/g, '')
    .trim()
}

function emptyReview(): ParsedReview {
  return { overall: null, dimensions: [], questionChain: [], weaknesses: [], actions: [] }
}

export function parseReviewReport(markdown?: string): ParsedReview {
  const result = emptyReview()
  if (!markdown) return result

  let section: 'dimensions' | 'questionChain' | 'weaknesses' | 'actions' | null = null

  for (const raw of markdown.split('\n')) {
    const line = raw.trim()
    if (!line) continue

    if (line.startsWith('#')) {
      const title = tidy(line)
      // 总分：## 面试复盘：72/100
      const overall = title.match(/(\d{1,3})\s*(?:\/\s*100|分)/)
      if (overall && result.overall === null && !DIMENSION_SECTION.some((k) => title.includes(k))) {
        const value = Number(overall[1])
        if (value >= 0 && value <= 100) result.overall = value
      }
      if (DIMENSION_SECTION.some((k) => title.includes(k))) {
        section = 'dimensions'
        continue
      }
      const hit = SECTION_KEYS.find((s) => s.match.some((k) => title.includes(k)))
      section = hit ? hit.field : null
      continue
    }

    const text = tidy(line)
    if (!text) continue

    if (section === 'dimensions') {
      const m = text.match(/^(.+?)[：:]\s*(\d{1,3})/)
      if (m) {
        const score = Math.max(0, Math.min(100, Number(m[2])))
        result.dimensions.push({ label: m[1].trim(), score })
      }
      continue
    }
    if (section) result[section].push(text)
  }

  return result
}

export function hasParsedContent(review: ParsedReview) {
  return (
    review.overall !== null ||
    review.dimensions.length > 0 ||
    review.questionChain.length > 0 ||
    review.weaknesses.length > 0 ||
    review.actions.length > 0
  )
}
