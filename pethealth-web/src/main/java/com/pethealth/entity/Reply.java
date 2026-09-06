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
@Document(collection = "replies")
public class Reply {

    @Id
    private String id;

    @Indexed
    private String postId;

    private String content;

    private String authorId;

    private String authorName;

    private Integer likeCount;

    private Boolean isAccepted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
