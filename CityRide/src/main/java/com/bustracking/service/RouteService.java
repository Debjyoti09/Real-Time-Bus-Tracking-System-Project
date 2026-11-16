package com.bustracking.service;

import com.bustracking.dto.*;

import java.util.List;

public interface RouteService {

    RouteResponseDTO createRoute(RouteRequestDTO request);

    List<RouteResponseDTO> getAllRoutes();

    RouteResponseDTO getRouteById(Long id);

    RouteResponseDTO updateRoute(Long id, RouteRequestDTO request);

    void deleteRoute(Long id);


    /// ------- AFTER DTO THIS IS NOT NEEDED---------
///    Route createRoute(Route route);
//
///    List<Route> getAllRoutes();
//
///    Route getRouteById(Long id);

}
