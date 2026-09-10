package com.jobseeker.common;

/**
 * 当前登录用户上下文（ThreadLocal），由 AuthInterceptor 写入，Service 层读取做数据隔离。
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        CURRENT.set(userId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    /** 必须登录，否则抛 401。 */
    public static Long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
