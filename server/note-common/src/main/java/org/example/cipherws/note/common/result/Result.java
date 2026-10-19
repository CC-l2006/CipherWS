package org.example.cipherws.note.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一响应结构。
 *
 * 字段名与前端 frontend/note/src/api/request.js 的约定严格一致：
 * <pre>
 * { success: true,  data: ..., total: ... }
 * { success: false, errMessage: "..." }
 * </pre>
 *
 * 注意：cipherws-common 里那份 {@code Result} 用的是 {@code errorMsg}，
 * 而前端（以及 docs 里的接口约定）读的是 {@code errMessage}。笔记系统这边
 * 以**前端读得懂**为准，所以这里用 errMessage —— 不要改成 errorMsg，
 * 否则前端取不到错误信息，只会显示「请求失败」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    private Boolean success;
    private String errMessage;
    private Object data;
    private Long total;

    public static Result ok() {
        return new Result(true, null, null, null);
    }

    public static Result ok(Object data) {
        return new Result(true, null, data, null);
    }

    /** 分页返回：data 是当前页列表，total 是总条数，前端的分页器靠它算页数 */
    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total == null ? (long) data.size() : total);
    }

    public static Result fail(String errMessage) {
        return new Result(false, errMessage, null, null);
    }
}
