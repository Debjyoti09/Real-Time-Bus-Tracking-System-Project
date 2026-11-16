package com.bustracking.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class DriverRouteRequestDTO {

    @NotEmpty(message = "Please select at least one route")
    private List<Long> routeIds;

    public List<Long> getRouteIds() {
        return routeIds;
    }

    public void setRouteIds(List<Long> routeIds) {
        this.routeIds = routeIds;
    }
}
