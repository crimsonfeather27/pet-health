package com.pethealth.service;

import com.pethealth.dto.NutritionReport;
import com.pethealth.entity.HealthRecord;
import com.pethealth.entity.PetProfile;
import com.pethealth.repository.HealthRecordRepository;
import com.pethealth.repository.PetProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 营养助手聚合服务：
 * 读取宠物档案 + 健康记录（体重），经 {@link NutritionCalculator} 计算 RER/MER 并返回报告。
 */
@Service
@RequiredArgsConstructor
public class NutritionService {

    /** 体重记录类型（健康记录中的中文类型名） */
    private static final String WEIGHT_TYPE = "体重";
    /** 趋势图最多取最近记录条数 */
    private static final int TREND_LIMIT = 30;

    private final PetProfileRepository petProfileRepository;
    private final HealthRecordRepository healthRecordRepository;

    /**
     * 生成某宠物的营养报告；宠物不存在返回 null（由 Controller 转 404）。
     *
     * @param bcs 体况评分 1–9，可不传（null）
     */
    public NutritionReport generate(String petId, Integer bcs) {
        PetProfile pet = petProfileRepository.findById(petId).orElse(null);
        if (pet == null) {
            return null;
        }

        // 体重记录：最新 + 趋势（升序，去重到 30 条）
        List<HealthRecord> weightRecords = new ArrayList<>();
        List<HealthRecord> all = healthRecordRepository.findByPetIdOrderByRecordedAtDesc(petId);
        for (HealthRecord r : all) {
            if (WEIGHT_TYPE.equals(r.getRecordType()) && parseWeight(r) != null) {
                weightRecords.add(r);
            }
        }

        NutritionReport.WeightInfo latest = null;
        if (!weightRecords.isEmpty()) {
            HealthRecord top = weightRecords.get(0);
            latest = new NutritionReport.WeightInfo(
                    parseWeight(top), unitOf(top), top.getRecordedAt());
        }

        List<NutritionReport.TrendPoint> trend = new ArrayList<>();
        for (int i = weightRecords.size() - 1; i >= 0 && trend.size() < TREND_LIMIT; i--) {
            HealthRecord r = weightRecords.get(i);
            trend.add(new NutritionReport.TrendPoint(
                    r.getRecordedAt() != null ? r.getRecordedAt().toLocalDate().toString() : "",
                    parseWeight(r)));
        }

        // 宠物信息
        Integer ageMonths = ageMonthsOf(pet);
        NutritionReport.PetInfo info = new NutritionReport.PetInfo(
                pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(),
                pet.getGender(), pet.getNeutered(), pet.getBirthday(), ageMonths);

        // 计算（无体重 / 非猫犬不计算）
        NutritionReport.Calculation calc = null;
        StringBuilder notice = new StringBuilder();
        if (latest == null) {
            notice.append("尚未记录体重，无法计算能量需求。请先在健康记录中添加“体重”记录。");
        } else {
            NutritionCalculator.Result result = NutritionCalculator.calculate(
                    pet.getSpecies(),
                    ageMonths == null ? 0 : ageMonths,
                    pet.getNeutered(),
                    latest.value());
            if (!result.supported()) {
                notice.append("当前仅支持猫/犬的精确能量估算，其他物种请以兽医建议为准。");
            } else {
                Double target = (bcs == null || bcs < 1 || bcs > 9)
                        ? null
                        : NutritionCalculator.adjustKcalByBcs(result.mer(), bcs);
                calc = new NutritionReport.Calculation(
                        result.rer(), result.merFactor(), result.mer(),
                        result.stageLabel(), bcs, target);
                if (pet.getNeutered() == null && ageMonths != null && ageMonths >= 12) {
                    notice.append("未设置绝育状态，已按“未绝育”系数计算；可在宠物档案中补全后重新计算。");
                }
            }
        }

        return new NutritionReport(info, latest, calc, trend, notice.toString());
    }

    /** 解析体重数值（value.value 数字），无法解析返回 null */
    private static Double parseWeight(HealthRecord r) {
        Map<String, Object> value = r.getValue();
        if (value == null) {
            return null;
        }
        Object v = value.get("value");
        if (v instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return v == null ? null : Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String unitOf(HealthRecord r) {
        Map<String, Object> value = r.getValue();
        if (value == null || value.get("unit") == null) {
            return "kg";
        }
        return String.valueOf(value.get("unit"));
    }

    private static Integer ageMonthsOf(PetProfile pet) {
        if (pet.getBirthday() == null) {
            return null;
        }
        int months = Period.between(pet.getBirthday(), LocalDate.now()).getYears() * 12
                + Period.between(pet.getBirthday(), LocalDate.now()).getMonths();
        return Math.max(0, months);
    }
}
