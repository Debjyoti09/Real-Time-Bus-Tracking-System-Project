package com.bustracking.service.impl;
import com.bustracking.dto.RouteRequestDTO;
import com.bustracking.dto.RouteResponseDTO;
import com.bustracking.entity.Route;
//import com.bustracking.ResourceNotFoundException;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.RouteRepository;
import com.bustracking.service.RouteService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteServiceImpl implements RouteService{

    private final RouteRepository routeRepository;

    RouteServiceImpl(RouteRepository routeRepository){
        this.routeRepository = routeRepository;
    }

    @Override
    public RouteResponseDTO createRoute(RouteRequestDTO request) {

        Route route = new Route();

        route.setName(request.getName());
        route.setDescription(request.getDescription());

        Route savedRoute = routeRepository.save(route);

        return convertToResponse(savedRoute);
    }

    @Override
    public List<RouteResponseDTO> getAllRoutes() {

        List<Route> routes = routeRepository.findAll();

        List<RouteResponseDTO> responseList = new ArrayList<>();

        for (Route route : routes) {
            responseList.add(convertToResponse(route));
        }

        return responseList;
    }

    @Override
    public RouteResponseDTO getRouteById(Long id) {

        /// ----------this also be an option to throw exception--------(54 -> 64)
//        Route route = routeRepository.findById(id).orElse(null);

//        if(route == null){
//
//            RouteResponseDTO response = new RouteResponseDTO();
//
//            response.setMessage("Route not found with id: " + id);
//
//            return response;
//
//        }

        /// ----------this also be an option to throw exception--------
        //Route route = routeRepository.findById(id) .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + id));

        /// ----------this also be an option to throw exception--------(70 - > 73)
        Route route = routeRepository.findById(id).orElse(null);
        if (route == null) {
            throw new ResourceNotFoundException("Route not found with id: " + id);
        }
        return convertToResponse(route);
    }

    @Override
    public RouteResponseDTO updateRoute(Long id, RouteRequestDTO request) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + id));
        route.setName(request.getName());
        route.setDescription(request.getDescription());
        return convertToResponse(routeRepository.save(route));
    }

    @Override
    public void deleteRoute(Long id) {

        Route route = routeRepository.findById(id).orElse(null);

        if (route == null) {
            System.out.println("Route not found with id: " + id);
            return;
        }

        routeRepository.delete(route);
    }

    private RouteResponseDTO convertToResponse(Route route) {

        RouteResponseDTO response = new RouteResponseDTO();

        response.setId(route.getId());
        response.setName(route.getName());
        response.setDescription(route.getDescription());

        return response;
    }
}
