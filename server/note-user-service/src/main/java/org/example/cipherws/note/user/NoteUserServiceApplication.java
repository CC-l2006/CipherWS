package org.example.cipherws.note.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 笔记系统用户服务：8084。
 *
 * scanBasePackages 扩到 org.example.cipherws.note —— note-common 里的
 * GlobalExceptionHandler 在 note.common 包下，默认扫描（本模块包）扫不到，
 * 扫不到就意味着抛异常时前端拿不到 errMessage。
 *
 * @MapperScan 显式声明 Mapper 接口所在包：靠 @Mapper 注解自动发现也能工作，
 * 但一旦有人把接口挪到别的包就会静默失效，显式写死更稳。
 */
@SpringBootApplication(scanBasePackages = "org.example.cipherws.note")
@EnableDiscoveryClient
@MapperScan("org.example.cipherws.note.user.mapper")
public class NoteUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteUserServiceApplication.class, args);
    }
}
