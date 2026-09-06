package com.pethealth.service;

/**
 * 营养计算器（纯规则，无 IO，便于单元测试）
 * <p>
 * 依据 NRC 2006 / WSAVA 宠物营养联盟通用估算公式：
 * - RER（静息能量需求）= 70 × 体重(kg)^0.75 kcal/天
 * - MER（每日能量需求）= RER × 生命阶段系数（物种 × 年龄 × 绝育状态）
 * 仅适用于猫（cat）与犬（dog），其他物种返回不支持。
 */
public final class NutritionCalculator {

    private NutritionCalculator() {
    }

    /** 生命阶段系数：犬 */
    private static final double DOG_FACTOR_PUPPY_UNDER_4M = 3.0;   // 幼犬（<4 月龄）
    private static final double DOG_FACTOR_PUPPY_4_12M     = 2.0;   // 幼犬（4–12 月龄）
    private static final double DOG_FACTOR_ADULT_INTACT    = 1.6;   // 成年犬（未绝育）
    private static final double DOG_FACTOR_ADULT_NEUTERED  = 1.4;   // 成年犬（已绝育）

    /** 生命阶段系数：猫 */
    private static final double CAT_FACTOR_KITTEN_UNDER_6M = 2.5;   // 幼猫（<6 月龄）
    private static final double CAT_FACTOR_KITTEN_6_12M    = 1.4;   // 幼猫（6–12 月龄）
    private static final double CAT_FACTOR_ADULT_INTACT    = 1.4;   // 成年猫（未绝育）
    private static final double CAT_FACTOR_ADULT_NEUTERED  = 1.2;   // 成年猫（已绝育）

    /** 体重管理：BCS ≥7（超重）目标热量按 MER 下调比例 */
    private static final double REDUCE_RATIO_OVERWEIGHT = 0.8;
    /** 体重管理：BCS ≤4（偏瘦）目标热量按 MER 上调比例 */
    private static final double INCREASE_RATIO_UNDERWEIGHT = 1.15;

    /** 计算结果 */
    public record Result(boolean supported, String stageLabel, double merFactor,
                         double rer, double mer) {
    }

    /**
     * 计算能量需求。
     *
     * @param species    物种（cat/狗/猫 或 dog/狗 均可识别，大小写不敏感）
     * @param ageMonths  月龄（<0 按 0 处理）
     * @param neutered   是否绝育（null 视为未绝育，需在页面提示补全）
     * @param weightKg   当前体重 kg（必须 > 0，否则视为不支持计算）
     */
    public static Result calculate(String species, int ageMonths, Boolean neutered, double weightKg) {
        Species kind = resolveSpecies(species);
        if (kind == Species.UNSUPPORTED || weightKg <= 0) {
            return new Result(false, null, 0, 0, 0);
        }
        if (ageMonths < 0) {
            ageMonths = 0;
        }
        boolean spayed = Boolean.TRUE.equals(neutered);

        double factor;
        String stage;
        if (kind == Species.DOG) {
            if (ageMonths < 4) {
                factor = DOG_FACTOR_PUPPY_UNDER_4M;
                stage = "幼犬（哺乳期至 4 月龄，成长期）";
            } else if (ageMonths < 12) {
                factor = DOG_FACTOR_PUPPY_4_12M;
                stage = "幼犬（4–12 月龄，成长期）";
            } else {
                factor = spayed ? DOG_FACTOR_ADULT_NEUTERED : DOG_FACTOR_ADULT_INTACT;
                stage = spayed ? "成年犬（已绝育）" : "成年犬（未绝育）";
            }
        } else {
            if (ageMonths < 6) {
                factor = CAT_FACTOR_KITTEN_UNDER_6M;
                stage = "幼猫（6 月龄前，快速成长期）";
            } else if (ageMonths < 12) {
                factor = CAT_FACTOR_KITTEN_6_12M;
                stage = "幼猫（6–12 月龄，成长期）";
            } else {
                factor = spayed ? CAT_FACTOR_ADULT_NEUTERED : CAT_FACTOR_ADULT_INTACT;
                stage = spayed ? "成年猫（已绝育）" : "成年猫（未绝育）";
            }
        }

        double rer = 70 * Math.pow(weightKg, 0.75);
        double mer = rer * factor;
        return new Result(true, stage, factor, round1(rer), round1(mer));
    }

    /**
     * 基于 BCS（1–9 体况评分）给出目标热量建议。
     *
     * @param mer 当前每日能量需求（MER）
     * @param bcs 1–9；超出区间返回 null
     * @return 目标每日热量（调整后），不在调整区间返回 null 表示维持现状
     */
    public static Double adjustKcalByBcs(double mer, int bcs) {
        if (bcs < 1 || bcs > 9) {
            return null;
        }
        if (bcs >= 7) {
            return round1(mer * REDUCE_RATIO_OVERWEIGHT);
        }
        if (bcs <= 4) {
            return round1(mer * INCREASE_RATIO_UNDERWEIGHT);
        }
        return null;
    }

    /** 根据热量需求与主粮热量密度（kcal/100g）换算每日喂食克数 */
    public static double feedingGrams(double mer, double kcalPer100g) {
        if (kcalPer100g <= 0) {
            return 0;
        }
        return round1(mer / (kcalPer100g / 100.0));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private enum Species {
        CAT, DOG, UNSUPPORTED
    }

    private static Species resolveSpecies(String species) {
        if (species == null) {
            return Species.UNSUPPORTED;
        }
        String s = species.trim().toLowerCase();
        if (s.contains("cat") || s.contains("猫")) {
            return Species.CAT;
        }
        if (s.contains("dog") || s.contains("狗")) {
            return Species.DOG;
        }
        return Species.UNSUPPORTED;
    }
}
