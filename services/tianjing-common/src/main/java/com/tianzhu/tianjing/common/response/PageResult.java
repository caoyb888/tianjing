package com.tianzhu.tianjing.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果包装体
 * 规范：API 接口规范 V3.1 §3.2
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private long total;
    private int page;
    private int size;
    private List<T> items;

    public static <T> PageResult<T> of(long total, int page, int size, List<T> items) {
        return new PageResult<>(total, page, size, items);
    }
}
