package org.example.cipherws.say;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * say 业务服务启动类。
 *
 * <p>由原单体后端 {@code CipherWsServerApplication} 拆分而来，启动后会自动向
 * Nacos 注册（服务名 {@code cipherws-say-service}）。</p>
 */
@SpringBootApplication
public class SayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SayServiceApplication.class, args);
    }
}
