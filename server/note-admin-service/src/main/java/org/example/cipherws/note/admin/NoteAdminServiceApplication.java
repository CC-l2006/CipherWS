package org.example.cipherws.note.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 笔记系统后台服务启动类。
 *
 * <p>端口 8087，提供用户管理、笔记巡检、AI 配置、公告管理与操作日志查询，
 * 启动后自动注册到 Nacos（服务名 {@code note-admin-service}）。</p>
 *
 * <p>为什么要写 scanBasePackages：{@code GlobalExceptionHandler} 在 note-common 的
 * {@code org.example.cipherws.note.common} 包下，与启动类不在同一个包里。默认只扫
 * {@code org.example.cipherws.note.admin}，异常就会走 Spring 的默认错误页，
 * 前端 request.js 拿不到 {@code { success:false, errMessage }} 这个结构。</p>
 */
@SpringBootApplication(scanBasePackages = "org.example.cipherws.note")
public class NoteAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteAdminServiceApplication.class, args);
    }
}
