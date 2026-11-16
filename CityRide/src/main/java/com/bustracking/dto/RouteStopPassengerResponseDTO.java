package com.bustracking.dto;

import java.util.List;

public class RouteStopPassengerResponseDTO {
    private String from;

    private  String to;

    private List<String> stoppages;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public List<String> getStoppages() {
        return stoppages;
    }

    public void setStoppages(List<String> stoppages) {
        this.stoppages = stoppages;
    }
}
