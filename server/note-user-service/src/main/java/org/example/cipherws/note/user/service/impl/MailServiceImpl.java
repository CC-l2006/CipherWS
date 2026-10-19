package org.example.cipherws.note.user.service.impl;

import org.example.cipherws.note.user.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送实现。
 *
 * 用 ObjectProvider 拿 JavaMailSender 而不是直接注入：Spring Boot 只有在
 * {@code spring.mail.host} 配置了的时候才会创建这个 bean，直接注入会让
 * 「没配邮箱」的环境启动失败 —— 而没配邮箱恰恰是最常见的本地开发状态。
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${note.mail.enabled:false}")
    private boolean enabled;

    @Value("${note.mail.from:}")
    private String from;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public boolean isEnabled() {
        // 三个条件缺一不可：开关打开、有 host（决定 bean 是否存在）、有发件账号
        return enabled
                && mailSenderProvider.getIfAvailable() != null
                && !mailUsername.isBlank();
    }

    @Override
    public void sendCode(String to, String code, String purpose) {
        String subject = "reset".equals(purpose)
                ? "【CipherWS 笔记】重置密码验证码"
                : "【CipherWS 笔记】绑定邮箱验证码";
        String text = """
                %s

                验证码：%s
                有效期 5 分钟，请勿转发给他人。

                如果不是你本人操作，忽略本邮件即可，你的账号不会有任何变化。
                """.formatted(
                "reset".equals(purpose) ? "你正在重置密码。" : "你正在为账号绑定这个邮箱。",
                code);

        if (!isEnabled()) {
            // 本地开发/演示：不发信，把验证码写日志。
            // 用 WARN 级别，免得有人以为邮件真的发出去了。
            log.warn("[note-user] 未配置 SMTP，验证码只写日志。to={} purpose={} code={}", to, purpose, code);
            return;
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from.isBlank() ? mailUsername : from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        mailSenderProvider.getObject().send(msg);
        log.info("[note-user] 验证码邮件已发送 to={} purpose={}", to, purpose);
    }
}
