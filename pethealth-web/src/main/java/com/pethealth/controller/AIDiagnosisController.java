package com.pethealth.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethealth.dto.ApiResponse;
import com.pethealth.service.dubbo.AIDiagnosisDubboService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Value("${app.llm.api-key:}")
    private String apiKey;

    @Value("${app.llm.model:deepseek-chat}")
    private String model;

    /**
     * POST /api/ai-diagnosis — AI 诊断
     * <p>
     * Dubbo 可用时调用 health-record-service 远程诊断；
     * 不可用时降级到本地规则引擎 + 本地 LLM 调用。
     */
    @PostMapping
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> diagnose(@RequestBody Map<String, Object> req) {
        String species = (String) req.getOrDefault("species", "other");
        String breed = (String) req.getOrDefault("breed", "");
        int ageMonths = req.get("ageMonths") != null ? ((Number) req.get("ageMonths")).intValue() : 0;
        String duration = (String) req.getOrDefault("duration", "");
        String notes = (String) req.getOrDefault("notes", "");
        String petId = req.get("petId") != null ? String.valueOf(req.get("petId")) : null;

        Object symptomsRaw = req.getOrDefault("symptoms", "");
        String symptomsStr = toSymptomsString(symptomsRaw);
        List<String> symptoms = normalizeSymptoms(symptomsRaw);

        log.info("AI诊断请求: species={}, breed={}, age={}月, symptoms={}, duration={}",
                species, breed, ageMonths, symptoms, duration);

        Map<String, Object> result = new HashMap<>();

        // 优先走 Dubbo 远程诊断
        if (dubboEnabled) {
            try {
                Map<String, Object> remote = aiDiagnosisDubboService.diagnose(
                        petId, species, breed, ageMonths, symptomsStr, duration);
                if (remote != null && !remote.isEmpty()) {
                    result.putAll(remote);
                    result.put("source", "dubbo-remote");
                    return ApiResponse.success(result);
                }
            } catch (Exception e) {
                log.warn("Dubbo diagnose 调用失败，降级本地: {}", e.getMessage());
            }
        }

        // 降级：本地处理 —— 配置了 API Key 走 LLM，否则走规则引擎
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("sk-your-key-here")) {
            try {
                Map<String, Object> llmResult = callLLM(species, breed, ageMonths, symptoms, duration, notes);
                result.putAll(llmResult);
                result.put("source", "llm");
            } catch (Exception e) {
                log.error("LLM 调用失败，回退到内置规则", e);
                Map<String, Object> fallback = ruleBasedDiagnose(species, breed, ageMonths, symptoms, duration);
                result.putAll(fallback);
                result.put("source", "rule-fallback");
                result.put("llmError", e.getMessage());
            }
        } else {
            Map<String, Object> ruleBased = ruleBasedDiagnose(species, breed, ageMonths, symptoms, duration);
            result.putAll(ruleBased);
            result.put("source", "rule-engine");
        }

        return ApiResponse.success(result);
    }

    /**
     * GET /api/ai-diagnosis/report?ownerId=xxx&petId=xxx&period=WEEKLY
     * 调用 Dubbo 生成健康报告
     */
    @GetMapping("/report")
    public ApiResponse<String> report(@RequestParam String ownerId,
                                      @RequestParam String petId,
                                      @RequestParam(defaultValue = "WEEKLY") String period) {
        if (dubboEnabled) {
            try {
                String report = aiDiagnosisDubboService.generateHealthReport(ownerId, petId, period);
                return ApiResponse.success(report);
            } catch (Exception e) {
                log.warn("Dubbo generateHealthReport 调用失败: {}", e.getMessage());
                return ApiResponse.error(503, "健康报告生成服务暂不可用: " + e.getMessage());
            }
        }
        return ApiResponse.error(503, "Dubbo 未启用，健康报告生成服务不可用");
    }

    /**
     * 把前端传来的 symptoms 统一成字符串（用于 Dubbo 传输）
     */
    private String toSymptomsString(Object raw) {
        if (raw == null) return "";
        if (raw instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(raw);
    }

    /**
     * 把前端传来的 symptoms 统一成 List<String>
     * 支持字符串（逗号/顿号/空格分隔）或数组两种格式
     */
    @SuppressWarnings("unchecked")
    private List<String> normalizeSymptoms(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List) {
            return ((List<?>) raw).stream().map(String::valueOf).toList();
        }
        if (raw instanceof String s) {
            String[] parts = s.split("[,，、;；\\s]+");
            return List.of(parts).stream().filter(p -> !p.isBlank()).toList();
        }
        return List.of(String.valueOf(raw));
    }

    /**
     * 内置规则引擎 —— Phase 1 提供基础诊断，Phase 2 替换为真实 LLM
     */
    private Map<String, Object> ruleBasedDiagnose(String species, String breed, int ageMonths,
                                                   List<String> symptoms, String duration) {
        boolean hasVomit = symptoms.stream().anyMatch(s -> s.contains("吐") || s.contains("呕吐"));
        boolean hasDiarrhea = symptoms.stream().anyMatch(s -> s.contains("拉稀") || s.contains("腹泻"));
        boolean hasLethargy = symptoms.stream().anyMatch(s -> s.contains("没精神") || s.contains("不爱动") || s.contains("萎靡") || s.contains("精神"));
        boolean hasAnorexia = symptoms.stream().anyMatch(s -> s.contains("不吃") || s.contains("食欲") || s.contains("厌食") || s.contains("吃不下"));
        boolean hasCough = symptoms.stream().anyMatch(s -> s.contains("咳"));
        boolean hasEye = symptoms.stream().anyMatch(s -> s.contains("眼"));
        boolean hasSkin = symptoms.stream().anyMatch(s -> s.contains("脱毛") || s.contains("痒") || s.contains("皮肤") || s.contains("掉毛"));
        boolean hasSneeze = symptoms.stream().anyMatch(s -> s.contains("喷") || s.contains("鼻"));

        List<Map<String, Object>> causes = new java.util.ArrayList<>();
        List<String> suggestions = new java.util.ArrayList<>();
        List<String> redFlags = new java.util.ArrayList<>();

        if (hasVomit && hasDiarrhea && hasAnorexia) {
            causes.add(cause("急性胃肠炎", 0.45, "胃黏膜受刺激或肠道感染，常见于误食、换粮、细菌感染"));
            causes.add(cause("细小病毒（幼犬）", 0.30, "高度致死性传染病，幼犬未完成疫苗时风险极高"));
            causes.add(cause("误食异物", 0.15, "猫狗吞食玩具/毛发/塑料袋等导致肠胃阻塞"));
            suggestions.add("⚠️ 建议 24 小时内就医，做血常规 + 粪便检查");
            suggestions.add("禁食 12 小时，只给水，不要自行喂药");
            suggestions.add("观察是否有血便、脱水症状（牙龈发白/凹陷）");
            redFlags.add("呕吐物带血或咖啡色液体");
            redFlags.add("精神极度萎靡、叫不应");
            redFlags.add("幼犬抽搐或体温异常（<37°C 或 >40°C）");
        } else if (hasLethargy && hasAnorexia) {
            causes.add(cause("系统性感染", 0.35, "病毒或细菌感染导致全身症状"));
            causes.add(cause("肝脏/肾脏问题", 0.25, "器官功能异常常伴随精神食欲双下降"));
            causes.add(cause("疼痛或不适", 0.20, "宠物不会说话，疼痛只表现为精神差"));
            suggestions.add("先测体温（正常 38-39°C），看是否发烧");
            suggestions.add("多给水，保持环境温暖安静");
            suggestions.add("若持续超过 48 小时请就医，做基础体检");
            redFlags.add("触摸腹部有疼痛反应");
            redFlags.add("呼吸急促或困难");
            redFlags.add("幼犬需排查犬瘟/细小，成犬需排查胰腺/肾脏");
        } else if (hasCough && (hasSneeze || hasEye)) {
            causes.add(cause("上呼吸道感染", 0.40, "病毒或细菌引起的鼻咽炎，类似人类感冒"));
            causes.add(cause("犬窝咳/猫鼻支", 0.35, "具有传染性，群居环境易发"));
            suggestions.add("保持室内湿度 50-60%，避免烟雾刺激");
            suggestions.add("不要自行给人用感冒药，含对乙酰氨基酚对宠物有毒");
            redFlags.add("咳嗽加重导致呼吸困难");
            redFlags.add("出现流脓性鼻涕或黄绿色眼屎");
        } else if (hasSkin) {
            causes.add(cause("皮肤过敏", 0.35, "食物、环境、药物都可能引起过敏反应"));
            causes.add(cause("真菌感染（猫藓）", 0.30, "圆形脱毛斑、有皮屑，会传染给人"));
            causes.add(cause("体外寄生虫", 0.25, "跳蚤/螨虫/蜱虫叮咬引起瘙痒脱毛"));
            suggestions.add("仔细检查毛发根部是否有跳蚤/蜱虫/虫卵");
            suggestions.add("保持皮肤干燥，潮湿环境易滋生真菌");
            suggestions.add("可尝试低敏饮食 2 周排除食物过敏");
            redFlags.add("皮肤破损、出现恶臭分泌物");
            redFlags.add("大面积脱毛或出现红肿热痛");
        } else if (hasVomit) {
            causes.add(cause("急性胃炎", 0.40, "进食过快/过饱、食物不当引起胃黏膜刺激"));
            causes.add(cause("毛球症（猫）", 0.30, "舔毛过多形成毛球堵塞胃肠道"));
            causes.add(cause("进食过快", 0.15, "吞入空气过多导致反胃"));
            suggestions.add("禁食 8 小时后少量多餐，不要一次喂太多");
            if ("cat".equalsIgnoreCase(species)) {
                suggestions.add("给化毛膏或猫草，帮助排出毛球");
            }
            suggestions.add("观察呕吐频率和呕吐物内容");
            redFlags.add("连续呕吐超过 3 次");
            redFlags.add("呕吐物带血或有异物（塑料/骨头）");
        } else if (hasDiarrhea) {
            causes.add(cause("饮食不当", 0.45, "换粮太快、吃了不该吃的东西、牛奶等"));
            causes.add(cause("肠道菌群紊乱", 0.25, "压力、抗生素使用后、长期喂单一食物"));
            causes.add(cause("体内寄生虫", 0.20, "蛔虫/绦虫等寄生虫破坏肠道"));
            suggestions.add("清淡饮食 2-3 天（白水煮鸡胸 + 南瓜）");
            suggestions.add("补充宠物专用益生菌，不要用人用的");
            suggestions.add("保证饮水充足，防止脱水");
            redFlags.add("便中带血或黑便");
            redFlags.add("持续腹泻超过 48 小时");
        } else if (!symptoms.isEmpty()) {
            causes.add(cause("症状较分散", 0.40, "单一/非典型症状，可能是早期或轻度问题"));
            causes.add(cause("情绪/环境变化", 0.20, "搬家、新宠物、主人不在等压力因素"));
            suggestions.add("记录症状出现的时间、频率、与饮食/活动的关联");
            suggestions.add("保持环境稳定，多观察 1-2 天");
            suggestions.add("若症状持续或加重，请就医做基础体检");
            redFlags.add("出现新的严重症状（呕吐/血便/呼吸困难）");
        } else {
            causes.add(cause("请详细描述症状", 0.0, ""));
            suggestions.add("常见需要关注的方面：精神状态、食欲、排便情况、是否有呕吐/咳嗽/皮肤异常");
            redFlags.add("如果宠物出现抽搐、意识不清、呼吸困难等紧急情况，请立即送医！");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("possibleCauses", causes);
        result.put("suggestions", suggestions);
        result.put("redFlags", redFlags);
        result.put("disclaimer", "⚠️ 以上为基于常见症状的规则推断，不能替代专业兽医诊断。持续不缓解或加重请及时就医。");
        result.put("advice", Map.of("species", species, "breed", breed, "ageMonths", ageMonths));
        return result;
    }

    private Map<String, Object> cause(String name, double probability, String description) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name);
        c.put("probability", probability);
        c.put("description", description);
        return c;
    }

    /**
     * 本地 LLM 调用（DeepSeek API，兼容 OpenAI 格式）
     * <p>
     * 请求体由 fastjson2 序列化：content 字段为明文文本（自动做 JSON 转义），
     * 响应体按 choices[0].message.content 解析，避免此前 URL 编码/字符串截断导致的乱码与截断。
     */
    private Map<String, Object> callLLM(String species, String breed, int ageMonths,
                                        List<String> symptoms, String duration, String notes) throws Exception {
        String prompt = buildPrompt(species, breed, ageMonths, symptoms, duration, notes);
        String url = "https://api.deepseek.com/v1/chat/completions";

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.3);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        payload.put("messages", messages);
        String body = JSON.toJSONString(payload);

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("LLM API 调用失败: HTTP " + code);
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = JSON.parseObject(sb.toString());
            String content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            if (content == null || content.isBlank()) {
                throw new RuntimeException("LLM 返回内容为空");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("possibleCauses", content);
            result.put("suggestions", List.of("建议根据 AI 诊断结果采取相应措施，持续不缓解请就医。"));
            result.put("redFlags", List.of("若出现紧急情况（抽搐/呼吸困难/血便）请立即送医！"));
            result.put("disclaimer", "AI 诊断仅供参考，不能替代专业兽医。");
            return result;
        }
    }

    private String buildPrompt(String species, String breed, int ageMonths,
                                List<String> symptoms, String duration, String notes) {
        return """
                你是一位专业的宠物医生助手。请根据以下宠物信息给出诊断建议：

                宠物种类: %s
                品种: %s
                年龄: %d 个月
                症状: %s
                持续时间: %s
                补充说明: %s

                请用中文回答，包含：
                1. 可能原因（2-3 种常见情况）
                2. 居家护理建议
                3. 危险信号（需要立即就医的情况）
                """.formatted(species, breed, ageMonths, symptoms, duration, notes);
    }
}
