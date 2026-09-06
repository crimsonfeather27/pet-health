package com.pethealth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NutritionCalculator 规则单元测试
 * <p>
 * 参考值人工核算（NRC 2006 公式 RER = 70 × kg^0.75）：
 * - 10kg：10^0.75 ≈ 5.6234 → RER ≈ 393.6
 * - 4kg ：4^0.75  ≈ 2.8284 → RER ≈ 198.0
 */
class NutritionCalculatorTest {

    @Test
    void 成年犬未绝育10kg_系数1_6() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("dog", 24, false, 10);
        assertTrue(r.supported());
        assertEquals(393.6, r.rer(), 0.05);
        assertEquals(1.6, r.merFactor(), 0.0001);
        assertEquals(629.8, r.mer(), 0.05);
        assertTrue(r.stageLabel().contains("成年犬"));
    }

    @Test
    void 成年犬已绝育_系数1_4() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("DOG", 36, true, 10);
        assertEquals(1.4, r.merFactor(), 0.0001);
    }

    @Test
    void 成年猫绝育4kg_系数1_2() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("cat", 30, true, 4);
        assertTrue(r.supported());
        assertEquals(198.0, r.rer(), 0.05);
        assertEquals(1.2, r.merFactor(), 0.0001);
        assertEquals(237.6, r.mer(), 0.05);
        assertTrue(r.stageLabel().contains("成年猫"));
    }

    @Test
    void 成年猫未设置绝育_按未绝育1_4() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("cat", 30, null, 4);
        assertEquals(1.4, r.merFactor(), 0.0001);
    }

    @Test
    void 幼犬2月龄_系数3_0() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("dog", 2, null, 3);
        assertEquals(3.0, r.merFactor(), 0.0001);
        assertTrue(r.stageLabel().contains("幼犬"));
    }

    @Test
    void 幼猫8月龄_系数1_4() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("cat", 8, null, 2.5);
        assertEquals(1.4, r.merFactor(), 0.0001);
        assertTrue(r.stageLabel().contains("幼猫"));
    }

    @Test
    void 不支持物种_返回不支持() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("rabbit", 24, false, 2);
        assertFalse(r.supported());
    }

    @Test
    void 体重非法_不计算() {
        NutritionCalculator.Result r =
                NutritionCalculator.calculate("dog", 24, false, 0);
        assertFalse(r.supported());
    }

    @Test
    void bcs超重_目标热量下调() {
        assertEquals(80.0, NutritionCalculator.adjustKcalByBcs(100, 7), 0.001);
    }

    @Test
    void bcs偏瘦_目标热量上调() {
        assertEquals(115.0, NutritionCalculator.adjustKcalByBcs(100, 4), 0.001);
    }

    @Test
    void bcs正常_不调整() {
        assertNull(NutritionCalculator.adjustKcalByBcs(100, 5));
        assertNull(NutritionCalculator.adjustKcalByBcs(100, 0));
        assertNull(NutritionCalculator.adjustKcalByBcs(100, 10));
    }

    @Test
    void 喂食克数换算() {
        // 300 kcal/天 ÷ (380 kcal/100g) → 78.9 g/天
        assertEquals(78.9, NutritionCalculator.feedingGrams(300, 380), 0.05);
    }
}
