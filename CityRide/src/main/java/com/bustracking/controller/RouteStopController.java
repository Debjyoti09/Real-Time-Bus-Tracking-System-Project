package com.bustracking.controller;

import com.bustracking.dto.RouteRequestDTO;
import com.bustracking.dto.RouteStopPassengerResponseDTO;
import com.bustracking.dto.RouteStopRequestDTO;
import com.bustracking.dto.RouteStopResponseDTO;
import com.bustracking.entity.RouteStop;
import com.bustracking.service.RouteStopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route-stops")
public class RouteStopController {

    private final RouteStopService routeStopService;

    public RouteStopController(RouteStopService routeStopService) {
        this.routeStopService = routeStopService;
    }

    @PostMapping
    public RouteStopResponseDTO createRouteStop(@Valid @RequestBody RouteStopRequestDTO request) {
        return routeStopService.createRouteStop(request);
    }

    @GetMapping
    public List<RouteStopResponseDTO> getAllRouteStops() {
        return routeStopService.getAllRouteStops();
    }

    @GetMapping("/search")
    public RouteStopPassengerResponseDTO searchRoute(@RequestParam String from, @RequestParam String to){
        return routeStopService.searchRoute(from,to);
    }

    @GetMapping("/{id}")
    public RouteStopResponseDTO getRouteStopById(@PathVariable Long id) {
        return routeStopService.getRouteStopById(id);
    }

    @PutMapping("/{id}")
    public RouteStopResponseDTO updateRouteStop(@PathVariable Long id, @Valid @RequestBody RouteStopRequestDTO request){
        return routeStopService.updateRouteStop(id,request);
    }

    @DeleteMapping("/{id}")
    public String deleteRouteStop(@PathVariable Long id) {
        routeStopService.deleteRouteStop(id);
        return "Route-stop deleted successfully";
    }
}
