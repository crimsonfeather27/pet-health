package com.pethealth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    /** 接收者用户 ID */
    @Indexed
    private String ownerId;

    /** 通知类型：REPLY / ACCEPT / REMINDER / SYSTEM */
    private String type;

    private String title;

    private String content;

    /** 关联业务 ID（如 postId / replyId / reminderId） */
    private String relatedId;

    /** 是否已读 */
    private Boolean isRead;

    private LocalDateTime createdAt;
}
