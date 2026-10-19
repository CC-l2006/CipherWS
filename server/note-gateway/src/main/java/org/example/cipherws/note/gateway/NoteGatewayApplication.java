package org.example.cipherws.note.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 笔记系统网关：对外统一入口（8083）。
 *
 * 职责只有两件：
 * 1. 按路径把 /api/** 分流到四个 note 服务；
 * 2. 在一处校验 JWT，把身份塞进请求头转发给下游 —— 下游服务因此不需要
 *    再各自解析 token，也不需要拿到签名密钥。
 *
 * 注意：这里不能引入 spring-boot-starter-web。Spring Cloud Gateway 基于
 * WebFlux，一旦 classpath 上出现 spring-webmvc，网关会以 MVC 模式启动并直接报错。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NoteGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteGatewayApplication.class, args);
    }
}
