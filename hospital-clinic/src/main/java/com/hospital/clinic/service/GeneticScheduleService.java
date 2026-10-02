package com.hospital.clinic.service;

import com.hospital.clinic.mapper.ScheduleSuggestionMapper;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 遗传算法排班服务（迭代15 B1-1）。
 * <p>
 * 染色体 = 每位医生 7 位（周一~周日）0/1 位串；种群 60，进化 40 代，锦标赛选择 + 单点交叉 + 位变异。
 * 适应度 = 出诊覆盖奖励 + 负荷公平性 - 约束惩罚（周末连班/超 5 天/已有人工排班冲突）。
 * 结果为 DRAFT 建议，管理员确认后标记 APPLIED。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeneticScheduleService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int POPULATION = 60;
    private static final int GENERATIONS = 40;
    private static final double CROSSOVER_RATE = 0.8;
    private static final double MUTATION_RATE = 0.03;

    private final ScheduleSuggestionMapper mapper;

    /** 生成某科室（可选）下一周排班建议 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(Long departmentId, String weekStart) {
        List<Map<String, Object>> doctors = mapper.selectDoctors(departmentId);
        if (doctors == null || doctors.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "未找到可排班的医生");
        }
        // 已有人工排班视为硬约束（该日强制出诊），GA 只优化其余空位
        Set<String> locked = new HashSet<>();
        for (Map<String, Object> row : mapper.selectExistingWeek(weekStart)) {
            locked.add(row.get("doctorId") + "@" + row.get("d"));
        }

        int n = doctors.size();
        List<int[][]> population = new ArrayList<>(POPULATION);
        for (int p = 0; p < POPULATION; p++) {
            int[][] ind = new int[n][7];
            for (int i = 0; i < n; i++) {
                for (int d = 0; d < 7; d++) {
                    ind[i][d] = RANDOM.nextDouble() < 0.6 ? 1 : 0;
                    if (locked.contains(doctors.get(i).get("id") + "@"
                            + LocalDate.parse(weekStart).plusDays(d).format(DateTimeFormatter.ISO_LOCAL_DATE))) {
                        ind[i][d] = 1;
                    }
                }
            }
            population.add(ind);
        }

        int[][] best = population.get(0);
        double bestFit = -1;
        for (int g = 0; g < GENERATIONS; g++) {
            double[][] fits = new double[POPULATION][2];
            for (int p = 0; p < POPULATION; p++) {
                double f = fitness(population.get(p), doctors, locked, weekStart);
                fits[p][0] = f;
                fits[p][1] = p;
                if (f > bestFit) {
                    bestFit = f;
                    best = population.get(p);
                }
            }
            java.util.Arrays.sort(fits, (a, b) -> Double.compare(b[0], a[0]));
            List<int[][]> next = new ArrayList<>(POPULATION);
            int elite = 4;
            for (int e = 0; e < elite; e++) {
                next.add(population.get((int) fits[e][1]));
            }
            while (next.size() < POPULATION) {
                int[][] pa = tournament(population, fits);
                int[][] pb = tournament(population, fits);
                int[][] c1 = new int[n][7];
                int[][] c2 = new int[n][7];
                if (RANDOM.nextDouble() < CROSSOVER_RATE) {
                    int cut = RANDOM.nextInt(6) + 1;
                    for (int i = 0; i < n; i++) {
                        for (int d = 0; d < 7; d++) {
                            c1[i][d] = d < cut ? pa[i][d] : pb[i][d];
                            c2[i][d] = d < cut ? pb[i][d] : pa[i][d];
                        }
                    }
                } else {
                    c1 = pa;
                    c2 = pb;
                }
                mutate(c1, locked, doctors, weekStart);
                mutate(c2, locked, doctors, weekStart);
                next.add(c1);
                if (next.size() < POPULATION) {
                    next.add(c2);
                }
            }
            population = next;
        }

        String batchNo = "GA" + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + String.format("%03d", RANDOM.nextInt(1000));
        for (int i = 0; i < n; i++) {
            StringBuilder days = new StringBuilder();
            int cnt = 0;
            for (int d = 0; d < 7; d++) {
                days.append(best[i][d]);
                cnt += best[i][d];
            }
            mapper.insert(batchNo, weekStart, departmentId, ((Number) doctors.get(i).get("id")).longValue(),
                    String.valueOf(doctors.get(i).get("name")), days.toString(), Math.round(bestFit * 10000) / 10000.0);
            log.info("[遗传排班] {} 医生{} 周计划 {}（{}天）", batchNo, doctors.get(i).get("name"), days, cnt);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchNo", batchNo);
        data.put("weekStart", weekStart);
        data.put("doctorCount", n);
        data.put("bestFitness", Math.round(bestFit * 10000) / 10000.0);
        data.put("generations", GENERATIONS);
        data.put("population", POPULATION);
        return data;
    }

    /** 适应度：覆盖 + 公平 - 惩罚 */
    private double fitness(int[][] ind, List<Map<String, Object>> doctors, Set<String> locked, String weekStart) {
        int cover = 0;
        int penalty = 0;
        int[] perDoctor = new int[ind.length];
        int[] perDay = new int[7];
        for (int i = 0; i < ind.length; i++) {
            int days = 0;
            for (int d = 0; d < 7; d++) {
                if (ind[i][d] == 1) {
                    cover += 10;
                    days++;
                    perDoctor[i]++;
                    perDay[d]++;
                    String key = doctors.get(i).get("id") + "@"
                            + LocalDate.parse(weekStart).plusDays(d).format(DateTimeFormatter.ISO_LOCAL_DATE);
                    if (!locked.contains(key) && ind[i][d] == 1 && days > 5) {
                        penalty += 30; // 超 5 天惩罚
                    }
                }
            }
        }
        // 公平性：医生间出诊天数方差小奖励
        double mean = 0;
        for (int v : perDoctor) {
            mean += v;
        }
        mean /= Math.max(1, ind.length);
        double var = 0;
        for (int v : perDoctor) {
            var += (v - mean) * (v - mean);
        }
        double fair = -Math.sqrt(var / Math.max(1, ind.length)) * 8;
        // 每日出诊人数接近 3 人奖励
        int dailyBonus = 0;
        for (int d = 0; d < 7; d++) {
            dailyBonus += -Math.abs(perDay[d] - 3) * 4;
        }
        return cover + fair + dailyBonus - penalty;
    }

    private int[][] tournament(List<int[][]> population, double[][] fits) {
        int a = RANDOM.nextInt(population.size());
        int b = RANDOM.nextInt(population.size());
        return fits[a][0] >= fits[b][0] ? population.get(a) : population.get(b);
    }

    private void mutate(int[][] ind, Set<String> locked, List<Map<String, Object>> doctors, String weekStart) {
        for (int i = 0; i < ind.length; i++) {
            for (int d = 0; d < 7; d++) {
                String key = doctors.get(i).get("id") + "@"
                        + LocalDate.parse(weekStart).plusDays(d).format(DateTimeFormatter.ISO_LOCAL_DATE);
                if (locked.contains(key)) {
                    ind[i][d] = 1;
                    continue;
                }
                if (RANDOM.nextDouble() < MUTATION_RATE) {
                    ind[i][d] = ind[i][d] == 1 ? 0 : 1;
                }
            }
        }
    }

    /** 批次应用（DRAFT → APPLIED） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(String batchNo) {
        long applied = mapper.markApplied(batchNo);
        if (applied == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "批次不存在或已应用");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("batchNo", batchNo);
        data.put("applied", applied);
        return data;
    }
}
