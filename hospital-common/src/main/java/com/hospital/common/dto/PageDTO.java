package com.hospital.common.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询请求基类
 */
@Data
public class PageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码（从 1 开始） */
    private Integer pageNo = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 每页条数上限，防止恶意查询 */
    private static final Integer MAX_PAGE_SIZE = 100;

    /**
     * 计算 MySQL LIMIT offset
     */
    public Integer getOffset() {
        int no = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int size = getEffectivePageSize();
        return (no - 1) * size;
    }

    /**
     * 获取有效的每页条数（限制上限）
     */
    private Integer getEffectivePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
