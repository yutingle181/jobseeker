package com.jobseeker.common;

import lombok.Getter;

/**
 * 业务异常：由 GlobalExceptionHandler 统一转成 Result，不在 Controller 里到处 try-catch。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException notFound(String what) {
        return new BizException(404, what + "不存在");
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }
}
