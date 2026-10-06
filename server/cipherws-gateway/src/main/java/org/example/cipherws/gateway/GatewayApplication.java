package org.example.cipherws.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 网关启动类。
 *
 * <p>职责：作为唯一的对外入口，把 {@code /say/**}、{@code /audio/**}、{@code /portal/**}
 * 等请求经 Nacos 服务发现动态路由到对应的后端微服务。网关保持轻量，只做转发，
 * 不承载业务逻辑与阻塞式调用（服务间调用见 cipherws-portal-service 的 OpenFeign 示例）。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
