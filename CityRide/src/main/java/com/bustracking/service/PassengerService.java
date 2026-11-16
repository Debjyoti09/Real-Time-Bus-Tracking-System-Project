package com.bustracking.service;

import com.bustracking.dto.AvailableBusResponseDTO;
import com.bustracking.dto.BusTrackingResponseDTO;

import java.util.List;

public interface PassengerService {

    List<AvailableBusResponseDTO> searchBuses(String from, String to);

    BusTrackingResponseDTO getBusTracking(Long busId, Long routeId);
}
