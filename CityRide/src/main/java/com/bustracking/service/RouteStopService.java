package com.bustracking.service;
import com.bustracking.dto.RouteStopPassengerResponseDTO;
import com.bustracking.dto.RouteStopRequestDTO;
import com.bustracking.dto.RouteStopResponseDTO;

import java.util.List;

public interface RouteStopService {

    RouteStopResponseDTO createRouteStop(RouteStopRequestDTO routeStop);

    List<RouteStopResponseDTO> getAllRouteStops();

    RouteStopResponseDTO getRouteStopById(Long id);

    RouteStopResponseDTO updateRouteStop(Long id, RouteStopRequestDTO request);

    void deleteRouteStop(Long id);

    RouteStopPassengerResponseDTO searchRoute(String from, String to);
}
