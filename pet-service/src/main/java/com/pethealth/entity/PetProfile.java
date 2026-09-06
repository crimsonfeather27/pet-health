package com.pethealth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pet_profiles")
public class PetProfile implements Serializable {

    @Id
    private String id;

    @Indexed
    private String ownerId;

    private String ownerName;

    private String name;

    private String species;

    private String breed;

    private String gender;

    /** 是否绝育：true=已绝育 / false=未绝育 / null=未设置 */
    private Boolean neutered;

    private LocalDate birthday;

    private LocalDate adoptedAt;

    private String avatar;

    private String description;

    private List<Vaccine> vaccines;

    private List<Deworming> dewormings;

    private List<Checkup> checkups;

    private List<MedicalVisit> medicalVisits;

    private Map<String, Object> additionalInfo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vaccine implements Serializable {
        private String id;
        private String name;
        private LocalDate vaccinatedAt;
        @Indexed
        private LocalDate nextDueAt;
        private String vetClinic;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Deworming implements Serializable {
        private String id;
        private String type;
        private String medicine;
        private LocalDate dewormedAt;
        @Indexed
        private LocalDate nextDueAt;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Checkup implements Serializable {
        private String id;
        private LocalDate checkedAt;
        private String vetId;
        private String vetName;
        private String clinic;
        private String result;
        private List<String> abnormalItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicalVisit implements Serializable {
        private String id;
        private LocalDate visitedAt;
        private String reason;
        private String diagnosis;
        private String treatment;
        private String vetId;
    }
}
