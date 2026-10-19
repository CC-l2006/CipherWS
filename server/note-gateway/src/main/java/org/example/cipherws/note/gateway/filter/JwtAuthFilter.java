package org.example.cipherws.note.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cipherws.note.common.exception.BusinessException;
import org.example.cipherws.note.common.security.Auth;
import org.example.cipherws.note.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 全局 JWT 校验过滤器。
 *
 * 设计要点：
 * 1. **白名单按路径匹配**：登录、注册、找回密码这些接口不能要求登录，
 *    否则永远进不去（鸡生蛋问题）。
 * 2. **先删除再写入身份头**：客户端可以自己伪造 {@code X-User-Id} 发过来，
 *    如果不清掉就直接信任，等于把鉴权送给了攻击者。所以无论有没有 token，
 *    都先把这三个头从请求里剥掉，再由这里写入可信值。
 * 3. **失败返回 401 + 统一信封**：前端 request.js 只认
 *    {@code {success:false, errMessage}}，返回 Spring 默认错误体会让用户
 *    看到一句无意义的「请求失败」。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 无需登录即可访问的路径（网关视角，带 /api 前缀）。
     *
     * 只放「登录/注册/找回密码」这三个 —— 它们必须在没有 token 时也能进，
     * 否则永远拿不到第一个 token。
     *
     * ⚠️ 不要再往里加 /api/announcements：白名单分支是直接放行、不注入身份头的，
     * 而 note-admin-service 那边要求 userId 非空（需求 4.6.4 是「用户登录后
     * 可查看公告」，本来就该登录后看）。放进来会导致公告接口永远返回「未登录」。
     */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/send-code",
            "/api/auth/reset-password"
    );

    /** 运维探活也不该要 token */
    private static final List<String> ACTUATOR = List.of("/actuator/**");

    /**
     * 公开的静态资源：头像。
     *
     * 必须放行 —— 浏览器渲染 {@code <img src="/uploads/avatar/x.png">} 时
     * 不会带 Authorization 头，走鉴权路由只有一个结果：全站头像 401。
     * 安全性由「文件名不可枚举」保证（用户id-16位随机串.ext）。
     */
    private static final List<String> PUBLIC_STATIC = List.of("/uploads/**");

    @Value("${note.jwt.secret}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 无论走哪条路，都先把客户端可能伪造的身份头删掉
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(Auth.HEADER_USER_ID);
                    h.remove(Auth.HEADER_USER_ROLE);
                    h.remove(Auth.HEADER_USERNAME);
                })
                .build();

        if (isPublic(path)) {
            return chain.filter(exchange.mutate().request(stripped).build());
        }

        String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange, "未登录，请先登录");
        }

        JwtUtil.Payload payload;
        try {
            payload = JwtUtil.verify(token, secret);
        } catch (BusinessException e) {
            return unauthorized(exchange, e.getMessage());
        }

        ServerHttpRequest withIdentity = stripped.mutate()
                .header(Auth.HEADER_USER_ID, String.valueOf(payload.userId()))
                .header(Auth.HEADER_USER_ROLE, payload.role())
                .header(Auth.HEADER_USERNAME, payload.username())
                .build();

        return chain.filter(exchange.mutate().request(withIdentity).build());
    }

    private boolean isPublic(String path) {
        for (String pattern : WHITE_LIST) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        for (String pattern : ACTUATOR) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        for (String pattern : PUBLIC_STATIC) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = MAPPER.writeValueAsBytes(Map.of("success", false, "errMessage", message));
        } catch (Exception e) {
            body = "{\"success\":false,\"errMessage\":\"未授权\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
