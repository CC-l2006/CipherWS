package org.example.cipherws.note.common.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cipherws.note.common.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 JWT（HS256）实现，**只用 JDK 自带的 javax.crypto + Jackson**，
 * 不引入 jjwt 之类的库 —— 笔记系统只需要「签发 / 验签 / 取声明」三件事，
 * 为了这点功能多一个传递依赖不划算。
 *
 * 注意：这不是通用 JWT 库。它只认 HS256、不处理 jwk/x5c、不校验 aud/iss。
 * 如果将来要接第三方登录，应该换成标准实现。
 */
public final class JwtUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    /** 固定的 header 段：{"alg":"HS256","typ":"JWT"} 的 base64url */
    private static final String HEADER_B64 =
            B64E.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {
    }

    /** 解析出来的载荷，只放业务真正要用的三个字段 */
    public record Payload(long userId, String username, String role) {
    }

    /**
     * 签发令牌。
     *
     * @param ttlSeconds 有效期（秒）；前端会话超时的体验由此决定
     */
    public static String sign(long userId, String username, String role, String secret, long ttlSeconds) {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("uid", userId);
        claims.put("sub", username);
        claims.put("role", role);
        claims.put("iat", now);
        claims.put("exp", now + ttlSeconds);

        String payloadB64;
        try {
            payloadB64 = B64E.encodeToString(MAPPER.writeValueAsBytes(claims));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 载荷序列化失败", e);
        }

        String signingInput = HEADER_B64 + "." + payloadB64;
        return signingInput + "." + B64E.encodeToString(hmac(signingInput, secret));
    }

    /**
     * 验签并取出载荷。
     * 任何一步不通过都抛 BusinessException —— 前端会把它显示成「登录已失效」。
     */
    public static Payload verify(String token, String secret) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("未登录，请先登录");
        }
        String raw = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();

        String[] parts = raw.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException("登录凭证格式不正确");
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput, secret);
        byte[] actual;
        try {
            actual = B64D.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("登录凭证格式不正确");
        }
        // 定长比较，避免按字节短路带来的时序侧信道
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException("登录凭证无效，请重新登录");
        }

        Map<String, Object> claims;
        try {
            // 用 TypeReference 读成强类型 Map，否则 getOrDefault 的入参类型是 ?，
            // 传 String 默认值会编译不过
            claims = MAPPER.readValue(B64D.decode(parts[1]), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("登录凭证内容无法解析");
        }

        Object exp = claims.get("exp");
        if (!(exp instanceof Number) || ((Number) exp).longValue() < System.currentTimeMillis() / 1000) {
            throw new BusinessException("登录已过期，请重新登录");
        }

        Object uid = claims.get("uid");
        if (!(uid instanceof Number)) {
            throw new BusinessException("登录凭证缺少用户标识");
        }

        return new Payload(
                ((Number) uid).longValue(),
                String.valueOf(claims.getOrDefault("sub", "")),
                String.valueOf(claims.getOrDefault("role", "user")));
    }

    private static byte[] hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 算法或密钥不可用时属于部署配置问题，直接中断
            throw new IllegalStateException("JWT 签名失败，请检查 note.jwt.secret 配置", e);
        }
    }
}
