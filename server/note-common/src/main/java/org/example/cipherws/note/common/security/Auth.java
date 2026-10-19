package org.example.cipherws.note.common.security;

import org.example.cipherws.note.common.exception.BusinessException;

/**
 * 网关校验完 JWT 后，会把身份放进这几个请求头转发给下游服务。
 *
 * 为什么让网关做鉴权而不是每个服务自己解析 token：
 * 1. 一处校验，四个服务不用各写一遍；
 * 2. 服务内部就不需要 JWT 密钥，泄露面更小；
 * 3. 下游服务只信任网关头（生产环境要靠网络隔离，保证服务端口不对外）。
 */
public final class Auth {

    /** 用户 ID，由网关写入 */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 角色，user / admin */
    public static final String HEADER_USER_ROLE = "X-User-Role";
    /** 账号名，仅用于日志 */
    public static final String HEADER_USERNAME = "X-Username";

    public static final String ROLE_ADMIN = "admin";

    private Auth() {
    }

    /**
     * 管理员专属接口的守卫。需求 4.1.4：普通用户无后台入口，
     * 前端已经拦了一层，这里再拦一层 —— 前端拦截只是体验，后端拦截才是安全。
     */
    public static void requireAdmin(String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BusinessException("无管理员权限");
        }
    }
}
