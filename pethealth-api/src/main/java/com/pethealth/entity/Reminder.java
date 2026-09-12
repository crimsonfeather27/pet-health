package com.pethealth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reminders")
public class Reminder implements Serializable {

    @Id
    private String id;

    @Indexed
    private String ownerId;

    @Indexed
    private String petId;

    private String petName;

    private String email;

    private String type;

    private String title;

    private String description;

    @Indexed
    private LocalDateTime remindAt;

    private Integer advanceDays;

    @Indexed
    private String status;

    private List<String> notifyMethod;

    private Map<String, Object> sourceRef;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
