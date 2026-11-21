package com.fooddelivery.backend.dto.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

/**
 * CachedPage — a Redis-friendly copy of a Spring Page.
 *
 * WHY? Spring's PageImpl can be written as JSON but not read back
 * (it has no constructor Jackson can use). So we store just the 4 numbers
 * and the list that make up a page, and rebuild a real Page on the way out.
 */
public record CachedPage<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> CachedPage<T> from(Page<T> page) {
        return new CachedPage<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public Page<T> toPage() {
        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }
}
