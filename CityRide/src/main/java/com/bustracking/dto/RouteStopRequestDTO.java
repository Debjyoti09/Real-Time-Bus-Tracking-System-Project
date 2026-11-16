package com.bustracking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RouteStopRequestDTO {

    @NotNull(message = "Route ID cannot be null")
    private Long routeId;

    @NotNull(message = "Stop ID cannot be null")
    private Long stopId;

    @NotNull(message = "Stop order cannot be null")
    @Min(value = 1, message = "Stop order must be at least 1")
    private Integer stopOrder;

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public Long getStopId() {
        return stopId;
    }

    public void setStopId(Long stopId) {
        this.stopId = stopId;
    }

    public Integer getStopOrder() {
        return stopOrder;
    }

    public void setStopOrder(Integer stopOrder) {
        this.stopOrder = stopOrder;
    }
}
