package com.fooddelivery.backend.unit;

import com.fooddelivery.backend.dto.response.CachedPage;
import com.fooddelivery.backend.util.GeoUtils;
import com.fooddelivery.backend.util.PageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Small pure helpers: distance maths, paging limits, the Redis page wrapper. */
class GeoAndPagingUtilsTest {

    // ── GeoUtils ─────────────────────────────────────────────

    @Test
    void distanceToSamePointIsZero() {
        assertThat(GeoUtils.distanceKm(19.07, 72.87, 19.07, 72.87)).isEqualTo(0.0);
    }

    @Test
    void mumbaiToPuneIsAbout120Km() {
        double km = GeoUtils.distanceKm(19.0760, 72.8777, 18.5204, 73.8567);
        assertThat(km).isCloseTo(120.0, within(2.0)); // straight-line distance
    }

    @Test
    void distanceIsSymmetric() {
        double ab = GeoUtils.distanceKm(12.97, 77.59, 13.08, 80.27);
        double ba = GeoUtils.distanceKm(13.08, 80.27, 12.97, 77.59);
        assertThat(ab).isCloseTo(ba, within(1e-9));
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(GeoUtils.distanceKm(10, 75, 11, 75)).isCloseTo(111.2, within(0.5));
    }

    @Test
    void travelMinutesUsesRoadFactorAndRoundsUp() {
        // 10 km × 1.3 road factor ÷ 20 km/h = 0.65 h = 39 min
        assertThat(GeoUtils.travelMinutes(10, 20, 1.3)).isEqualTo(39);
        // 0.1 km → 0.39 min → rounds UP to 1 (never "0 minutes away")
        assertThat(GeoUtils.travelMinutes(0.1, 20, 1.3)).isEqualTo(1);
    }

    @Test
    void round2() {
        assertThat(GeoUtils.round2(2.3456)).isEqualTo(2.35);
        assertThat(GeoUtils.round2(2.0)).isEqualTo(2.0);
    }

    // ── PageUtils ────────────────────────────────────────────

    @Test
    void hugePageSizeIsCapped() {
        Pageable p = PageUtils.of(0, 1_000_000);
        assertThat(p.getPageSize()).isEqualTo(PageUtils.MAX_PAGE_SIZE);
    }

    @Test
    void negativeOrZeroValuesAreFixedInsteadOfCrashing() {
        Pageable p = PageUtils.of(-5, 0);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(1);
    }

    // ── CachedPage (Redis-friendly Page) ─────────────────────

    @Test
    void cachedPageRoundTripKeepsContentAndCounts() {
        Page<String> original = new PageImpl<>(List.of("a", "b"), PageRequest.of(2, 2), 9);
        Page<String> rebuilt = CachedPage.from(original).toPage();

        assertThat(rebuilt.getContent()).containsExactly("a", "b");
        assertThat(rebuilt.getNumber()).isEqualTo(2);
        assertThat(rebuilt.getSize()).isEqualTo(2);
        assertThat(rebuilt.getTotalElements()).isEqualTo(9);
        assertThat(rebuilt.getTotalPages()).isEqualTo(5);
    }
}
