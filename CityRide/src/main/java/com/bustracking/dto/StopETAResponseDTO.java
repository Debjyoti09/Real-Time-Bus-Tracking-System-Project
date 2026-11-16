package com.bustracking.dto;

import java.time.LocalDateTime;

public class StopETAResponseDTO {
    private Long stopId;
    private String stopName;
    private Integer stopOrder;
    private String status;

    // Null for passed/current stops
    private Double distanceKm;
    private Integer etaMinutes;
    private LocalDateTime expectedArrivalTime;

    public Long getStopId() {
        return stopId;
    }

    public void setStopId(Long stopId) {
        this.stopId = stopId;
    }

    public String getStopName() {
        return stopName;
    }

    public void setStopName(String stopName) {
        this.stopName = stopName;
    }

    public Integer getStopOrder() {
        return stopOrder;
    }

    public void setStopOrder(Integer stopOrder) {
        this.stopOrder = stopOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(Integer etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    public LocalDateTime getExpectedArrivalTime() {
        return expectedArrivalTime;
    }

    public void setExpectedArrivalTime(
            LocalDateTime expectedArrivalTime) {
        this.expectedArrivalTime = expectedArrivalTime;
    }
}
