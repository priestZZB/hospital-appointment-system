package com.hospital.ai.controller;

import com.hospital.ai.mapper.B2Mapper;
import com.hospital.common.annotation.AuditLog;
import com.hospital.common.annotation.RequiresPermission;
import com.hospital.common.constant.PermissionConstant;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import com.hospital.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 多轮问诊（迭代16 B2-1，base=/api/ai/consult）。
 * 规则追问引擎：按主诉关键词匹配追问模板，最多 3 轮，轮次满或建议性回答即给初步建议。
 */
@RestController
@RequestMapping("/api/ai/consult")
@RequiredArgsConstructor
public class ConsultController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 追问模板：主诉关键词 → 依次追问列表 */
    private static final Map<String, List<String>> FOLLOW_UP = new LinkedHashMap<>();
    /** 初步建议：主诉关键词 → 建议 */
    private static final Map<Set<String>, String> SUGGESTIONS = new LinkedHashMap<>();

    static {
        FOLLOW_UP.put("发热", List.of("体温最高多少度？", "是否伴有咳嗽或咽痛？", "症状持续几天了？"));
        FOLLOW_UP.put("咳嗽", List.of("咳嗽有痰吗？痰的颜色？", "是否伴有胸闷气短？", "夜间咳嗽是否加重？"));
        FOLLOW_UP.put("胸痛", List.of("疼痛是压榨性还是针刺样？", "疼痛持续几分钟还是几小时？", "是否放射到左肩或后背？"));
        FOLLOW_UP.put("腹痛", List.of("疼痛在哪个部位？上腹还是下腹？", "是否伴有恶心呕吐？", "进食后疼痛是否加重？"));
        FOLLOW_UP.put("头痛", List.of("头痛是搏动性还是紧箍感？", "是否伴有视物模糊或呕吐？", "近期睡眠和压力如何？"));
        // 初步建议（命中任意关键词）
        SUGGESTIONS.put(Set.of("发热", "咳嗽"), "考虑呼吸道感染可能，建议查血常规+C反应蛋白，多饮水休息；如持续高热请及时发热门诊就诊。");
        SUGGESTIONS.put(Set.of("胸痛"), "胸痛需警惕心血管事件，建议立即完善心电图+心肌酶检查，必要时急诊就诊。");
        SUGGESTIONS.put(Set.of("腹痛"), "考虑消化系统疾病可能，建议完善腹部超声/淀粉酶检查，暂禁食辛辣刺激。");
        SUGGESTIONS.put(Set.of("头痛"), "考虑血管性或紧张性头痛，建议测血压，保证睡眠；如伴呕吐视物模糊请及时神经内科就诊。");
    }

    private final B2Mapper b2Mapper;

    /** 开始问诊会话 */
    @PostMapping("/start")
    @AuditLog(value = "AI多轮问诊开始", operationType = "INSERT")
    @RequiresPermission(PermissionConstant.AI_CONSULT)
    public Result<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        Long patientId = toLong(body.get("patientId"));
        String symptom = str(body.get("symptom"));
        if (patientId == null || symptom == null || symptom.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "patientId 与 symptom 不能为空");
        }
        String sessionNo = "AC" + LocalDateTime.now().format(TS) + String.format("%03d", RANDOM.nextInt(1000));
        String followUp = followUpFor(symptom, 0);
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "AI", "content", "您好，我注意到您的主诉是「" + symptom + "」。"));
        messages.add(Map.of("role", "AI", "content", followUp == null ? "请补充其他症状描述。" : followUp));
        b2Mapper.insertSession(sessionNo, patientId, symptom, toJson(messages), 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionNo", sessionNo);
        data.put("round", 1);
        data.put("question", followUp == null ? "请补充其他症状描述。" : followUp);
        return Result.ok(data);
    }

    /** 回答追问（返回下一问或初步建议） */
    @PostMapping("/{sessionNo}/reply")
    @RequiresPermission(PermissionConstant.AI_CONSULT)
    public Result<Map<String, Object>> reply(@PathVariable("sessionNo") String sessionNo,
                                             @RequestBody Map<String, Object> body) {
        Map<String, Object> session = b2Mapper.selectSession(sessionNo);
        if (session == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "会话不存在");
        }
        if ("CLOSED".equals(String.valueOf(session.get("status")))) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "会话已结束");
        }
        String answer = str(body.get("content"));
        if (answer == null || answer.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "content 不能为空");
        }
        int round = session.get("rounds") == null ? 1 : (int) Double.parseDouble(String.valueOf(session.get("rounds")));
        int next = round + 1;
        String symptom = String.valueOf(session.get("symptom"));
        String messages = String.valueOf(session.get("messages"));
        List<Object> history = fromJsonArray(messages);
        history.add(Map.of("role", "PATIENT", "content", answer));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionNo", sessionNo);
        data.put("round", next);
        if (next >= 3) {
            String suggestion = suggestionFor(symptom, answer);
            history.add(Map.of("role", "AI", "content", suggestion));
            b2Mapper.updateSession(sessionNo, toJson(history), next, suggestion, "CLOSED", round);
            data.put("suggestion", suggestion);
            data.put("finished", true);
        } else {
            String question = followUpFor(symptom, round);
            if (question == null) {
                // 主诉未命中追问模板时兜底通用追问，避免 Map.of(null) NPE 卡死会话
                question = "还有其他伴随症状吗？（如持续时间、加重或缓解因素）";
            }
            history.add(Map.of("role", "AI", "content", question));
            b2Mapper.updateSession(sessionNo, toJson(history), next, null, "OPEN", round);
            data.put("question", question);
            data.put("finished", false);
        }
        return Result.ok(data);
    }

    /** 会话详情 */
    @GetMapping("/{sessionNo}")
    @RequiresPermission(PermissionConstant.AI_CONSULT)
    public Result<Map<String, Object>> detail(@PathVariable("sessionNo") String sessionNo) {
        Map<String, Object> session = b2Mapper.selectSession(sessionNo);
        if (session == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "会话不存在");
        }
        return Result.ok(session);
    }

    /** 患者会话分页 */
    @GetMapping("/list")
    @RequiresPermission(PermissionConstant.AI_CONSULT)
    public Result<Map<String, Object>> list(@RequestParam("patientId") Long patientId,
                                            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", b2Mapper.selectSessionPage(patientId, (pageNo - 1) * pageSize, pageSize));
        page.put("total", b2Mapper.countSessionPage(patientId));
        page.put("pageNo", pageNo);
        page.put("pageSize", pageSize);
        return Result.ok(page);
    }

    private String followUpFor(String symptom, int round) {
        for (Map.Entry<String, List<String>> e : FOLLOW_UP.entrySet()) {
            if (symptom.contains(e.getKey())) {
                return round < e.getValue().size() ? e.getValue().get(round) : null;
            }
        }
        return round == 0 ? "请问症状持续多久了？是否伴有其他不适？" : null;
    }

    private String suggestionFor(String symptom, String answer) {
        for (Map.Entry<Set<String>, String> e : SUGGESTIONS.entrySet()) {
            for (String kw : e.getKey()) {
                if (symptom.contains(kw) || answer.contains(kw)) {
                    return e.getValue();
                }
            }
        }
        return "根据您的描述建议先观察休息，若症状加重或持续请及时线下就诊，由医生进一步评估。";
    }

    private Long toLong(Object v) {
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private List<Object> fromJsonArray(String json) {
        List<Object> list = new ArrayList<>();
        if (json != null && json.startsWith("[")) {
            try {
                list.addAll(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .readValue(json, List.class));
            } catch (Exception ignored) {
                // 历史消息解析失败按空会话继续
            }
        }
        return list;
    }

    private String toJson(List<Object> list) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
