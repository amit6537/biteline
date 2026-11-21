package com.fooddelivery.backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * PageUtils — builds a safe Pageable from user-supplied page/size.
 *
 * WHY? Without a cap, a client can send ?size=1000000 and make the server
 * load the whole table into memory in one request. Negative values would
 * also crash PageRequest.of() with a 500.
 */
public final class PageUtils {

    public static final int MAX_PAGE_SIZE = 50;

    private PageUtils() {}

    public static Pageable of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
