package org.example.cipherws.note.common.exception;

import org.example.cipherws.note.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理：把异常翻译成前端认识的 { success:false, errMessage } 结构。
 *
 * 为什么必须有这个：前端 request.js 只有在 success 不为 true 时才读 errMessage。
 * 没有它的话，抛异常会走 Spring 默认的 500 错误页（JSON 结构完全不同），
 * 用户看到的就只是一句「请求失败」，不知道是额度用完了还是账号被禁用了。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result onBusiness(BusinessException e) {
        // 业务异常是「预期内的失败」，不打堆栈，只记一行
        log.info("业务拒绝：{}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result onDataAccess(DataAccessException e) {
        // 数据库异常不能把 SQL 细节回给前端，只记日志
        log.error("数据库异常", e);
        return Result.fail("数据操作失败，请稍后重试");
    }

    /**
     * 上传超限。Spring 在进入 controller 之前就抛这个异常，
     * 不单独处理的话用户只会看到笼统的「服务异常」，不知道自己传太大了。
     * 实际大小上限由 spring.servlet.multipart.max-file-size 决定。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result onUploadTooLarge(MaxUploadSizeExceededException e) {
        log.info("上传文件超过限制：{}", e.getMessage());
        return Result.fail("上传文件过大，请换一张更小的图片");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result onUnexpected(Exception e) {
        log.error("未预期异常", e);
        return Result.fail("服务异常，请稍后重试");
    }
}
