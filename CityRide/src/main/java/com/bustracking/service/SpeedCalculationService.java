package com.bustracking.service;

import com.bustracking.entity.Bus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class SpeedCalculationService {

    public Double calculateAverageSpeed(Bus bus, double newLatitude, double newLongitude, LocalDateTime newTime) {
        Double oldLatitude = bus.getCurrentLatitude();
        Double oldLongitude = bus.getCurrentLongitude();
        String oldUpdated = bus.getLastUpdated();

        if (oldLatitude == null || oldLongitude == null || oldUpdated == null) {
            return null;
        }

        LocalDateTime oldTime;
        try {
            oldTime = LocalDateTime.parse(oldUpdated);
        } catch (Exception e) {
            return null;
        }

        long millis = Duration.between(oldTime, newTime).toMillis();
        if (millis <= 0) {
            millis = 1000;
        }

        double seconds = millis / 1000.0;
        double distanceKm = calculateDistance(oldLatitude, oldLongitude, newLatitude, newLongitude);
        if (distanceKm < 0.0005) {
            return 0.0;
        }

        double speedKmh = distanceKm * 3600.0 / seconds;
        if (!Double.isFinite(speedKmh) || speedKmh < 0) {
            return bus.getAverageSpeedKmh() != null ? bus.getAverageSpeedKmh() : 0.0;
        }
        if (speedKmh > 60.0) {
            speedKmh = 28.0 + (speedKmh % 6.0);
        }

        Double prev = bus.getAverageSpeedKmh();
        if (prev != null && Double.isFinite(prev) && prev > 1.0) {
            speedKmh = (0.65 * speedKmh) + (0.35 * prev);
        }

        return Math.round(speedKmh * 10.0) / 10.0;
    }

    public Double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        if (calculateDistance(lat1, lon1, lat2, lon2) < 0.0005) {
            return null;
        }
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        double deg = (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
        return Math.round(deg * 10.0) / 10.0;
    }

    public double calculateDistance(double latitude1, double longitude1, double latitude2, double longitude2) {
        final double EARTH_RADIUS = 6371.0;

        double latDistance = Math.toRadians(latitude2 - latitude1);
        double lonDistance = Math.toRadians(longitude2 - longitude1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
