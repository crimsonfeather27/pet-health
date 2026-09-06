package com.pethealth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "likes")
@CompoundIndex(name = "user_target_unique", def = "{'userId': 1, 'targetType': 1, 'targetId': 1}", unique = true)
public class Like {

    @Id
    private String id;

    private String userId;

    private String targetType;

    private String targetId;

    private LocalDateTime createdAt;
}
