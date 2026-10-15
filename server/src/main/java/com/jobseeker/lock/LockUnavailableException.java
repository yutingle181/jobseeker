package com.jobseeker.lock;

/**
 * 锁基础设施自身不可用（Redis 连不上 / 等待中被中断）。
 *
 * <p>与「没抢到锁」区分开：抢不到是正常竞争（{@link DistributedLock#executeWithLock} 返回 null），
 * 而这个异常表示锁服务坏了，应当降级而不是当成竞争失败处理。
 */
public class LockUnavailableException extends RuntimeException {

    public LockUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public LockUnavailableException(String message) {
        super(message);
    }
}
