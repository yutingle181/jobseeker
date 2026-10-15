package com.jobseeker.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 缓存 key 的生成逻辑（两个缓存实现共享）。
 *
 * <p>为什么抽出来：Redis 版与内存版如果各写一份 key 逻辑，将来任何一处微调
 * （比如再多去掉一个易变字段）都会让两个后端的 key 不一致 —— 表现是
 * 「切换后端后命中率莫名变化」，而这种问题极难定位。共享一份，语义才可保证。
 */
public final class ChatCacheSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatCacheSupport() {
    }

    /**
     * 由请求报文生成稳定 key：解析 JSON → 去除易变字段（session_id / session）→ 规范化后 SHA-256。
     * 解析失败则回退为字符串 hashCode，保证主流程不被缓存逻辑阻断。
     */
    public static String buildKey(String payload) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(payload);
            node.remove("session_id");
            node.remove("session");
            return sha256(node.toString());
        } catch (Exception e) {
            return "fb-" + Integer.toHexString(payload.hashCode());
        }
    }

    /** SHA-256 十六进制；算法不可用时退化为 hashCode（保可用性，不保稳定性）。 */
    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "fb-" + Integer.toHexString(text.hashCode());
        }
    }
}
