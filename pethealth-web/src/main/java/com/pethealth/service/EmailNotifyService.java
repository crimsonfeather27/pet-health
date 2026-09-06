package com.pethealth.service;

import com.pethealth.entity.Reminder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;

/**
 * 邮件通知服务
 * SMTP 未配置时自动跳过，只打 warn 日志（设计文档 5.5.6 节）
 */
@Slf4j
@Service
public class EmailNotifyService {

    @Value("${mail.smtp-host:}")
    private String smtpHost;

    @Value("${mail.smtp-port:465}")
    private String smtpPort;

    @Value("${mail.smtp-user:}")
    private String smtpUser;

    @Value("${mail.smtp-password:}")
    private String smtpPassword;

    @Value("${mail.from:pethealth@example.com}")
    private String fromAddress;

    public void send(Reminder reminder) {
        if (smtpUser == null || smtpUser.isEmpty()) {
            log.warn("SMTP 未配置，跳过邮件发送（提醒: {} → {}）", reminder.getTitle(), reminder.getEmail());
            return;
        }

        if (reminder.getEmail() == null || reminder.getEmail().isEmpty()) {
            log.warn("收件人邮箱为空，跳过发送（提醒: {}）", reminder.getTitle());
            return;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(reminder.getEmail()));
            message.setSubject("PetHealth 提醒：" + reminder.getTitle());
            message.setContent(buildEmailBody(reminder), "text/plain; charset=UTF-8");
            message.saveChanges();

            Transport.send(message);
            log.info("提醒邮件发送成功 -> {}", reminder.getEmail());
        } catch (MessagingException e) {
            log.error("邮件发送失败: {}", e.getMessage());
        }
    }

    private String buildEmailBody(Reminder reminder) {
        return String.format(
                "亲爱的铲屎官：\n\n" +
                "您的宠物【%s】有一条健康提醒：\n\n" +
                "  %s\n" +
                "  %s\n" +
                "  提醒时间：%s\n\n" +
                "建议尽快处理哦！\n\n" +
                "—— PetHealth 宠物健康管家",
                reminder.getPetName() != null ? reminder.getPetName() : "",
                reminder.getTitle(),
                reminder.getDescription() != null ? reminder.getDescription() : "",
                reminder.getRemindAt()
        );
    }
}
