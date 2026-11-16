package com.bustracking.controller;

import com.bustracking.dto.RouteRequestDTO;
import com.bustracking.dto.RouteResponseDTO;
import com.bustracking.entity.Route;
import com.bustracking.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService){
        this.routeService = routeService;
    }

    @PostMapping
    public RouteResponseDTO createRoute(
            @Valid @RequestBody RouteRequestDTO request) {

        return routeService.createRoute(request);
    }

    @GetMapping
    public List<RouteResponseDTO> getAllRoutes() {

        return routeService.getAllRoutes();
    }

    @GetMapping("/{id}")
    public RouteResponseDTO getRouteById(
            @PathVariable Long id) {

        return routeService.getRouteById(id);
    }

    @PutMapping("/{id}")
    public RouteResponseDTO updateRoute(@PathVariable Long id, @Valid @RequestBody RouteRequestDTO request) {
        return routeService.updateRoute(id, request);
    }

    @DeleteMapping("/{id}")
    public String deleteRoute(@PathVariable Long id) {

        routeService.deleteRoute(id);

        return "Route deleted successfully";
    }
}
