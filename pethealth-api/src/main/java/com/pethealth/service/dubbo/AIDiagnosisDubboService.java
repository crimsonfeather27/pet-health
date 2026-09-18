package com.pethealth.service.dubbo;

import java.util.Map;

/**
 * AI 诊断 Dubbo 服务接口（共享契约）
 * <p>
 * 由 health-record-service 提供实现，pethealth-web 通过 Dubbo 调用。设计文档 7.3 节。
 */
public interface AIDiagnosisDubboService {

    /**
     * AI 初步诊断
     *
     * @param petId     宠物 ID（可为空）
     * @param species   物种 cat / dog / other
     * @param breed     品种
     * @param ageMonths 月龄
     * @param symptoms  症状（逗号或顿号分隔的字符串）
     * @param duration  持续时间
     * @param apiKey    请求级 LLM API Key（由前端用户输入、web 层透传，可为空）；
     *                  为空时回退到 Provider 环境变量配置，再为空则走内置规则引擎。
     *                  Provider 不落库、不缓存、不记录该值。
     * @return 诊断结果 Map
     */
    Map<String, Object> diagnose(String petId, String species, String breed,
                                  int ageMonths, String symptoms, String duration,
                                  String apiKey);

    /**
     * 生成健康报告
     *
     * @param ownerId 所有者 ID
     * @param petId   宠物 ID
     * @param period  WEEKLY / MONTHLY
     * @return 报告文本（Markdown 格式）
     */
    String generateHealthReport(String ownerId, String petId, String period);
}
