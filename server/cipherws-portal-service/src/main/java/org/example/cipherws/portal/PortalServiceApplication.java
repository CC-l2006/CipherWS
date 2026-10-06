package org.example.cipherws.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 门户聚合服务（BFF）启动类。
 *
 * <p>{@link EnableFeignClients} 扫描公共模块 {@code org.example.cipherws.common.api}
 * 下的 OpenFeign 契约（如 {@code SayClient}），本服务通过声明式调用访问
 * {@code cipherws-say-service}，请求经 Nacos 服务发现 + 负载均衡落地。</p>
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "org.example.cipherws.common.api")
public class PortalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalServiceApplication.class, args);
    }
}
