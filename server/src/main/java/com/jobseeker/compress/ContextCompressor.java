package com.jobseeker.compress;

import com.jobseeker.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * user_context 按需裁剪：按用户问题 query 与 JD/简历文本的词面相关性，
 * 仅保留相关句/段，无关内容被裁剪，从而降低塞进 prompt 的 token 与 LLM prefill 开销。
 *
 * 设计取舍：
 * - **词面打分而非语义向量**：网关是透传层，接入 embedding 需网络/新依赖，违背薄网关语义；
 *   词面 token 重叠（中文二元组 + 英文数字词）在「JD 职责/要求/薪资」「简历技能/经历」这类
 *   结构文本上足够区分相关性，且确定性、零成本、可单测。
 * - **保留命中 chunk 原文顺序、不重排**：JD/简历段落顺序承载语义（如「任职要求」在「岗位职责」后），
 *   重排会破坏 Agent 的消费方式；按原序拼接只在尾部截断，安全可解释。
 * - **回退兜底**：query 为空 / 开关关闭 / 所有 chunk 得分均为 0（query 过泛）时，退化为固定截断，
 *   与改造前字节级一致，绝不丢信息。
 * - **硬上限仍是配置的 contextJdLimit / contextResumeLimit**：压缩只是「在更短预算内去掉无关句」，
 *   不会超过原上限，响应体体积只减不增。
 */
@Component
public class ContextCompressor {

    /** JD 硬上限（字符） */
    private final int jdLimit;
    /** 简历正文硬上限（字符） */
    private final int resumeLimit;
    /** 开关关闭时直接退化为固定截断 */
    private final boolean enabled;

    /** 句/行切分：在中文/ASCII 句末标点或换行处断开，并保留该分隔符（固定长度后顾断言） */
    private static final Pattern SPLIT = Pattern.compile("(?<=[\u3002\uff1b\uff01\uff1f;!?\n\r])");
    /** 英文/数字词（转小写） */
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    public ContextCompressor(AgentProperties props) {
        this.enabled = props.isContextCompressEnabled();
        this.jdLimit = props.getContextJdLimit();
        this.resumeLimit = props.getContextResumeLimit();
    }

    /** 压缩 JD：enabled 且 query 非空时按相关性裁剪，否则固定截断。 */
    public String compressJd(String jd, String query) {
        return compress(jd, query, jdLimit);
    }

    /** 压缩简历正文：同上。 */
    public String compressResume(String resume, String query) {
        return compress(resume, query, resumeLimit);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 内部裁剪：按句/行切 chunk，保留命中 query token 的 chunk（原文顺序），
     * 拼接后若超 limit 则在尾段截断；无任何 chunk 命中则回退固定截断。
     */
    String compress(String text, String query, int limit) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (!enabled || isBlank(query) || t.isEmpty()) {
            return fixedTruncate(t, limit);
        }
        Set<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return fixedTruncate(t, limit);
        }

        String[] raw = SPLIT.split(t);
        List<String> chunks = new ArrayList<>();
        for (String c : raw) {
            if (!c.isBlank()) {
                chunks.add(c.trim());
            }
        }
        // 单 chunk 不值得切分，直接固定截断
        if (chunks.size() <= 1) {
            return fixedTruncate(t, limit);
        }

        // 仅保留命中 query token 的 chunk（保原序）
        List<String> kept = new ArrayList<>();
        for (String chunk : chunks) {
            if (score(chunk, tokens) > 0) {
                kept.add(chunk);
            }
        }
        // 没有任何 chunk 命中（query 过泛/无关）→ 回退固定截断，不丢信息
        if (kept.isEmpty()) {
            return fixedTruncate(t, limit);
        }

        // 按原序拼接，预算内尽量多放相关 chunk；超 limit 则截断尾段
        StringBuilder sb = new StringBuilder();
        for (String chunk : kept) {
            if (sb.length() == 0) {
                sb.append(chunk);
            } else if (sb.length() + 1 + chunk.length() <= limit) {
                sb.append('\n').append(chunk);
            } else {
                int room = limit - sb.length() - 1;
                if (room > 0) {
                    sb.append('\n').append(chunk, 0, Math.min(chunk.length(), room));
                }
                sb.append("…（已截断）");
                break;
            }
        }
        return sb.toString();
    }

    /** 等同改造前的 AgentContextService.truncate：超长截到 limit + 省略号。 */
    static String fixedTruncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.length() <= limit) {
            return t;
        }
        return t.substring(0, limit) + "…（已截断）";
    }

    /** query 词面 token 化：英文/数字小写词 + 中文连续汉字二元组。 */
    private static Set<String> tokenize(String query) {
        Set<String> tokens = new HashSet<>();
        if (query == null) {
            return tokens;
        }
        String q = query.toLowerCase();
        java.util.regex.Matcher m = WORD.matcher(q);
        while (m.find()) {
            tokens.add(m.group());
        }
        StringBuilder han = new StringBuilder();
        for (int i = 0; i < q.length(); i++) {
            int cp = q.codePointAt(i);
            if (Character.isIdeographic(cp)) {
                han.append((char) cp);
            } else {
                addBigrams(tokens, han);
                han.setLength(0);
            }
        }
        addBigrams(tokens, han);
        return tokens;
    }

    private static void addBigrams(Set<String> tokens, StringBuilder han) {
        for (int i = 0; i + 1 < han.length(); i++) {
            tokens.add(han.substring(i, i + 2));
        }
    }

    /** 命中 token 数（去重计数）。 */
    private static int score(String chunk, Set<String> tokens) {
        String c = chunk.toLowerCase();
        int s = 0;
        for (String token : tokens) {
            if (c.contains(token)) {
                s++;
            }
        }
        return s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
