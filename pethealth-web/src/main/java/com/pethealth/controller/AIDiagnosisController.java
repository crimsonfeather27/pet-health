package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.interceptor.AuthContext;
import com.pethealth.service.RateLimitService;
import com.pethealth.service.dubbo.AIDiagnosisDubboService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 诊断入口 —— 仅作为 Dubbo Consumer 的薄适配层。
 * <p>
 * 架构决策（#16）：LLM 调用与规则引擎统一收敛到 health-record-service 的
 * AIDiagnosisDubboServiceImpl，web 层不再持有任何 LLM / 规则副本。
 * 这样做的好处：
 * 1. 单一事实来源，避免两侧 prompt / 规则漂移导致结果不一致；
 * 2. LLM 密钥只在 provider 侧配置，减少泄露面；
 * 3. 限流、计费、降级策略在一处实现。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-diagnosis")
@RequiredArgsConstructor
public class AIDiagnosisController {

    /**
     * Dubbo Consumer —— 直连 health-record-service（端口 20885）
     */
    @DubboReference(
            url = "dubbo://localhost:20885/com.pethealth.service.dubbo.AIDiagnosisDubboService",
            check = false,
            timeout = 15000
    )
    private AIDiagnosisDubboService aiDiagnosisDubboService;

    @Value("${app.dubbo.enabled:true}")
    private boolean dubboEnabled;

    private final RateLimitService rateLimitService;

    /** AI 诊断限流：每用户每分钟 10 次（防止滥用 / 控制 LLM 成本） */
    private static final int AI_DIAGNOSIS_LIMIT = 10;
    private static final long AI_DIAGNOSIS_WINDOW_SECONDS = 60;

    /**
     * POST /api/ai-diagnosis — AI 诊断
     * <p>
     * 统一走 Dubbo 远程诊断（provider 内部已实现 LLM → 规则引擎的降级）。
     * web 层不再本地调用 LLM，避免双重调用与密钥分散。
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> diagnose(@RequestBody Map<String, Object> req,
                                                     HttpServletRequest request) {
        // 写接口已由 AuthInterceptor 强制登录，这里取 userId 做限流维度
        String userId = AuthContext.userId(request);
        String rateKey = "ai-diagnosis:" + (userId != null ? userId : "anonymous");
        if (!rateLimitService.tryAcquire(rateKey, AI_DIAGNOSIS_LIMIT, AI_DIAGNOSIS_WINDOW_SECONDS)) {
            log.warn("AI 诊断触发限流: key={}", rateKey);
            return ApiResponse.error(429, "AI 诊断调用过于频繁，请稍后再试（每分钟限 " + AI_DIAGNOSIS_LIMIT + " 次）");
        }

        String species = (String) req.getOrDefault("species", "other");
        String breed = (String) req.getOrDefault("breed", "");
        int ageMonths = req.get("ageMonths") != null ? ((Number) req.get("ageMonths")).intValue() : 0;
        String duration = (String) req.getOrDefault("duration", "");
        String petId = req.get("petId") != null ? String.valueOf(req.get("petId")) : null;

        String symptomsStr = toSymptomsString(req.getOrDefault("symptoms", ""));

        log.info("AI诊断请求: species={}, breed={}, age={}月, symptoms={}, duration={}",
                species, breed, ageMonths, symptomsStr, duration);

        if (!dubboEnabled) {
            return ApiResponse.error(503, "AI 诊断服务未启用");
        }

        try {
            Map<String, Object> remote = aiDiagnosisDubboService.diagnose(
                    petId, species, breed, ageMonths, symptomsStr, duration);
            if (remote == null || remote.isEmpty()) {
                return ApiResponse.error(500, "AI 诊断返回为空");
            }
            remote.put("source", "dubbo-remote");
            return ApiResponse.success(remote);
        } catch (Exception e) {
            log.error("Dubbo AI 诊断调用失败", e);
            return ApiResponse.error(503, "AI 诊断服务暂不可用，请稍后再试");
        }
    }

    /**
     * GET /api/ai-diagnosis/report?ownerId=xxx&petId=xxx&period=WEEKLY
     * 调用 Dubbo 生成健康报告
     */
    @GetMapping("/report")
    public ApiResponse<String> report(@RequestParam String ownerId,
                                      @RequestParam String petId,
                                      @RequestParam(defaultValue = "WEEKLY") String period) {
        if (!dubboEnabled) {
            return ApiResponse.error(503, "Dubbo 未启用，健康报告生成服务不可用");
        }
        try {
            String report = aiDiagnosisDubboService.generateHealthReport(ownerId, petId, period);
            return ApiResponse.success(report);
        } catch (Exception e) {
            log.warn("Dubbo generateHealthReport 调用失败: {}", e.getMessage());
            return ApiResponse.error(503, "健康报告生成服务暂不可用，请稍后再试");
        }
    }

    /**
     * 把前端传来的 symptoms 统一成字符串（用于 Dubbo 传输）
     */
    @SuppressWarnings("unchecked")
    private String toSymptomsString(Object raw) {
        if (raw == null) return "";
        if (raw instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(raw);
    }
}
