package com.hospital.clinic.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 科室排队快照（签到叫号大屏初始数据）
 */
@Data
@Builder
public class QueueSnapshotVO {

    /** 科室 ID */
    private Long departmentId;

    /** 科室名称 */
    private String departmentName;

    /** 当前叫号（最近一次 CALLED/RE_CALLED/IN_CONSULT，可能为空） */
    private QueuePatientVO currentCall;

    /** 等待列表（WAITING） */
    private List<QueuePatientVO> waitingList;
}
