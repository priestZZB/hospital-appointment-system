package com.hospital.clinic.job;

import com.hospital.clinic.service.ScheduleAutoGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 自动排班定时任务（迭代9 A8）
 * <p>
 * 每天凌晨 1 点（{@code cron = "0 0 1 * * ?"}）为未来第 7 天（新开放挂号的一天）
 * 按医生默认出诊模板生成排班并确认生成号源，执行结果写 schedule_generate_log。
 * 幂等：同日同医生已有排班则跳过；单医生失败不影响其他医生。
 * 管理端可通过 {@code POST /api/admin/schedule/generate?date=} 手动补跑指定日期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleGenerateJob {

    private final ScheduleAutoGenerateService scheduleAutoGenerateService;

    /** 每日凌晨 1 点执行：为未来第 7 天生成排班 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDailySchedules() {
        LocalDate targetDate = LocalDate.now().plusDays(7);
        log.info("[自动排班] 定时任务开始: 目标日期={}", targetDate);
        try {
            scheduleAutoGenerateService.generateForDate(targetDate);
        } catch (Exception e) {
            // 定时任务内部兜底：任何异常只记日志，不影响调度器后续触发
            log.error("[自动排班] 定时任务执行失败: 目标日期={}", targetDate, e);
        }
    }
}
