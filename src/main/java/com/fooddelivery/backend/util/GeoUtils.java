package com.fooddelivery.backend.util;

/**
 * GeoUtils — distance and travel-time maths (Phase 12).
 *
 * Same Haversine formula as the SQL in DeliveryPartnerStatusRepository,
 * but in Java, for ETA and geofence checks on single points.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {}

    /** Great-circle distance in km between two lat/lng points. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Minutes to ride `km` (straight line).
     * roadFactor: roads aren't straight — real distance is ~1.3× the crow-flies one.
     */
    public static long travelMinutes(double km, double avgSpeedKmh, double roadFactor) {
        return (long) Math.ceil(km * roadFactor / avgSpeedKmh * 60.0);
    }

    /** Round to 2 decimals for API responses (2.3456 → 2.35). */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
