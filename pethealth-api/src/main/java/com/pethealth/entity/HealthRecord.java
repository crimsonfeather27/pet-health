package com.pethealth.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Document(collection = "health_records")
public class HealthRecord implements Serializable {

    @Id
    private String id;

    @Indexed
    private String petId;

    private String ownerId;

    private String recordType;

    private Map<String, Object> value;

    private LocalDateTime recordedAt;

    private String notes;

    private LocalDateTime createdAt;
}
