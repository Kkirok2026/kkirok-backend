package com.database2026.backend.auth;

import com.database2026.backend.common.DomainException;
import java.time.LocalDateTime;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmtpSchoolEmailVerificationSender implements SchoolEmailVerificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpSchoolEmailVerificationSender.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final Properties smtpProperties;

    public SmtpSchoolEmailVerificationSender(
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") String smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") String startTlsEnable,
            @Value("${spring.mail.properties.mail.smtp.starttls.required:false}") String startTlsRequired,
            @Value("${spring.mail.properties.mail.smtp.connectiontimeout:5000}") String connectionTimeout,
            @Value("${spring.mail.properties.mail.smtp.timeout:5000}") String timeout,
            @Value("${spring.mail.properties.mail.smtp.writetimeout:5000}") String writeTimeout
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from == null || from.isBlank() ? username : from;
        this.smtpProperties = new Properties();
        this.smtpProperties.put("mail.smtp.auth", smtpAuth);
        this.smtpProperties.put("mail.smtp.starttls.enable", startTlsEnable);
        this.smtpProperties.put("mail.smtp.starttls.required", startTlsRequired);
        this.smtpProperties.put("mail.smtp.connectiontimeout", connectionTimeout);
        this.smtpProperties.put("mail.smtp.timeout", timeout);
        this.smtpProperties.put("mail.smtp.writetimeout", writeTimeout);
    }

    @Override
    public void send(String email, String code, LocalDateTime expiresAt) {
        assertConfigured();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setJavaMailProperties(smtpProperties);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[끼록] 학교 이메일 인증코드");
        message.setText("""
                끼록 학교 이메일 인증코드입니다.

                인증코드: %s
                만료시간: %s

                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(code, expiresAt));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Failed to send school email verification code. host={}, port={}, username={}, from={}, to={}, reason={}",
                    host, port, username, from, email, exception.getMessage(), exception);
            throw new DomainException(HttpStatus.BAD_GATEWAY, "SCHOOL_EMAIL_SEND_FAILED", "학교 이메일 인증코드 발송에 실패했습니다.");
        }
    }

    private void assertConfigured() {
        if (host == null || host.isBlank() || username == null || username.isBlank() || password == null || password.isBlank()) {
            throw DomainException.badRequest(
                    "MAIL_CONFIGURATION_REQUIRED",
                    "MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD 환경변수가 필요합니다."
            );
        }
    }
}
