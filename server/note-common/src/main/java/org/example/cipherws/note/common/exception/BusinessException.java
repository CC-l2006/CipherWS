package org.example.cipherws.note.common.exception;

/**
 * 业务异常：message 会被原样回给前端展示，所以**要写成用户看得懂的话**。
 *
 * 例：throw new BusinessException("AI 额度已用完，请联系管理员分配")
 * 而不是 "quota exhausted"。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
