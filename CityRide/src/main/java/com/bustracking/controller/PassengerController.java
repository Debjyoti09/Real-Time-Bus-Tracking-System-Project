package com.bustracking.controller;

import com.bustracking.dto.AvailableBusResponseDTO;
import com.bustracking.dto.BusTrackingResponseDTO;
import com.bustracking.service.PassengerService;
import com.bustracking.repository.RouteStopRepository;
import com.bustracking.dto.StopResponseDTO;
import com.bustracking.entity.RouteStop;
import java.util.ArrayList;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passenger")
public class PassengerController {

    private final PassengerService passengerService;
    private final RouteStopRepository routeStopRepository;

    public PassengerController(PassengerService passengerService, RouteStopRepository routeStopRepository) {
        this.passengerService = passengerService;
        this.routeStopRepository = routeStopRepository;
    }

    @GetMapping("/buses")
    public List<AvailableBusResponseDTO> getAvailableBuses(
            @RequestParam String from,
            @RequestParam String to) {

        return passengerService.searchBuses(from, to);
    }

    @GetMapping("/tracking/{busId}")
    public BusTrackingResponseDTO getBusTracking(
            @PathVariable Long busId,
            @RequestParam(required = false) Long routeId) {
        return passengerService.getBusTracking(busId, routeId);
    }
    @GetMapping("/routes/{routeId}/stops")
    public List<StopResponseDTO> getRouteStops(@PathVariable Long routeId) {
        List<StopResponseDTO> result = new ArrayList<>();
        for (RouteStop routeStop : routeStopRepository.findByRoute_IdOrderByStopOrderAsc(routeId)) {
            StopResponseDTO dto = new StopResponseDTO();
            dto.setId(routeStop.getStop().getId());
            dto.setName(routeStop.getStop().getName());
            dto.setLatitude(routeStop.getStop().getLatitude());
            dto.setLongitude(routeStop.getStop().getLongitude());
            result.add(dto);
        }
        return result;
    }
}
