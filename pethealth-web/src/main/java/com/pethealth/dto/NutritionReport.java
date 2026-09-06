package com.pethealth.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 营养助手聚合报告 —— 宠物信息 + 最新体重 + RER/MER 计算 + 体重趋势
 */
public record NutritionReport(
        PetInfo pet,
        WeightInfo weight,
        Calculation calculation,
        List<TrendPoint> trend,
        String notice
) {

    public record PetInfo(String id, String name, String species, String breed,
                          String gender, Boolean neutered, LocalDate birthday, Integer ageMonths) {
    }

    public record WeightInfo(double value, String unit, LocalDateTime recordedAt) {
    }

    public record Calculation(double rer, double merFactor, double mer,
                              String stageLabel, Integer bcs, Double bcsTargetKcal) {
    }

    public record TrendPoint(String date, double value) {
    }
}
