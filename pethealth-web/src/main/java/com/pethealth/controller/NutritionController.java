package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.dto.NutritionReport;
import com.pethealth.service.NutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 营养助手接口
 * <p>
 * GET /api/nutrition/{petId}?bcs=5 — 聚合宠物档案与体重记录，
 * 返回 RER / MER / 生命阶段系数 / 体重趋势等（基于 NRC/WSAVA 通用估算公式）。
 */
@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping("/{petId}")
    public ApiResponse<NutritionReport> report(@PathVariable String petId,
                                               @RequestParam(required = false) Integer bcs) {
        NutritionReport report = nutritionService.generate(petId, bcs);
        if (report == null) {
            return ApiResponse.error(404, "宠物档案不存在");
        }
        return ApiResponse.success(report);
    }
}
