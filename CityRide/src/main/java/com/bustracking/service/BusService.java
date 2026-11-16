package com.bustracking.service;

import com.bustracking.dto.*;

import java.util.List;

public interface BusService {

    BusResponseDTO createBus(BusRequestDTO request);

    List<BusResponseDTO> getAllBuses();

    BusResponseDTO getBusById(Long id);

    BusResponseDTO updateBus(Long id, BusRequestDTO request);

    BusResponseDTO updateLocation(Long id, LocationUpdateRequestDTO request);

    //BusTrackingResponseDTO getBusTracking(Long busId);

    BusDetailsResponseDTO getBusDetails(Long busId, Long routeId);

    void deleteBus(Long id);
}
