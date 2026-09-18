package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.dto.NutritionReport;
import com.pethealth.service.NutritionService;
import com.pethealth.service.OwnershipGuard;
import jakarta.servlet.http.HttpServletRequest;
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
    private final OwnershipGuard ownershipGuard;

    @GetMapping("/{petId}")
    public ApiResponse<NutritionReport> report(@PathVariable String petId,
                                               @RequestParam(required = false) Integer bcs,
                                               HttpServletRequest request) {
        // 宠物档案与体重数据属私有信息，先做属主校验
        ownershipGuard.requireOwnedPet(request, petId);
        NutritionReport report = nutritionService.generate(petId, bcs);
        if (report == null) {
            return ApiResponse.error(404, "宠物档案不存在");
        }
        return ApiResponse.success(report);
    }
}
