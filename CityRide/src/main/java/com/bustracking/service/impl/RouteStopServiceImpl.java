package com.bustracking.service.impl;
import com.bustracking.entity.Route;
import com.bustracking.entity.RouteStop;
import com.bustracking.dto.RouteStopResponseDTO;
import com.bustracking.dto.RouteStopRequestDTO;
import com.bustracking.entity.Stop;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.RouteRepository;
import com.bustracking.repository.StopRepository;
import com.bustracking.service.RouteStopService;
import com.bustracking.repository.RouteStopRepository;
import org.springframework.stereotype.Service;
import com.bustracking.dto.RouteStopPassengerResponseDTO;
import java.util.ArrayList;
import java.util.List;

@Service
public class RouteStopServiceImpl implements RouteStopService {

    private final RouteStopRepository routeStopRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;

    public RouteStopServiceImpl(RouteStopRepository routeStopRepository, RouteRepository routeRepository, StopRepository stopRepository) {
        this.routeStopRepository = routeStopRepository;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
    }

    @Override
    public RouteStopResponseDTO createRouteStop(RouteStopRequestDTO request) {

        Route route = routeRepository.findById(request.getRouteId()).orElse(null);

        Stop stop = stopRepository.findById(request.getStopId()).orElse(null);

        if(route == null){
            throw new ResourceNotFoundException("Route not found with id: "+request.getRouteId());
        }

        if(stop == null){
            throw new ResourceNotFoundException("Stop not found with id: "+request.getStopId());
        }

        RouteStop routeStop = new RouteStop();

        routeStop.setRoute(route);
        routeStop.setStop(stop);
        routeStop.setStopOrder(request.getStopOrder());
        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        return convertToResponse(savedRouteStop);
    }

    @Override
    public List<RouteStopResponseDTO> getAllRouteStops() {

        List<RouteStop> routeStops = routeStopRepository.findAll();

        List<RouteStopResponseDTO> responseList = new ArrayList<>();

        for (RouteStop routeStop : routeStops) {
            responseList.add(convertToResponse(routeStop));
        }

        return responseList;
    }

    @Override
    public RouteStopResponseDTO getRouteStopById(Long id) {

        RouteStop routeStop = routeStopRepository.findById(id).orElse(null);

        if(routeStop == null){
            throw new ResourceNotFoundException("RouteStop not found with id: "+id);
        }

        return convertToResponse(routeStop);
    }

    @Override
    public RouteStopResponseDTO updateRouteStop(Long id, RouteStopRequestDTO request){

        RouteStop routeStop = routeStopRepository.findById(id).orElse(null);

        if(routeStop == null){
            throw new ResourceNotFoundException("RouteStop not found with id: "+id);
        }

        Route route = routeRepository.findById(request.getRouteId()).orElse(null);

        if(route == null){
            throw new ResourceNotFoundException("Route not found with id: "+request.getRouteId());
        }

        Stop stop = stopRepository.findById(request.getStopId()).orElse(null);

        if(stop == null){
            throw new ResourceNotFoundException("Stop not found with id: "+request.getStopId());
        }

        routeStop.setRoute(route);
        routeStop.setStop(stop);
        routeStop.setStopOrder(request.getStopOrder());

        RouteStop updateRouteStop = routeStopRepository.save(routeStop);

        return convertToResponse(updateRouteStop);

    }

    @Override
    public void deleteRouteStop(Long id) {

        RouteStop routeStop = routeStopRepository.findById(id).orElse(null);

        if(routeStop == null){
            throw new ResourceNotFoundException("RouteStop not found with id: "+id);
        }

        routeStopRepository.delete(routeStop);
    }

    @Override
    public RouteStopPassengerResponseDTO searchRoute(String from, String to){

        /// Find from stop
        List<RouteStop> fromStops = routeStopRepository.findByStop_Name(from);

        /// Find to stop
        List<RouteStop> toStops = routeStopRepository.findByStop_Name(to);

        if(fromStops.isEmpty()){
            throw new ResourceNotFoundException("From stop not found: "+from);
        }

        if(toStops.isEmpty()){
            throw new ResourceNotFoundException("To stop not found: "+to);
        }

        /// Find common route
        for(RouteStop fromStop : fromStops){

            for(RouteStop toStop : toStops){

                if(fromStop.getRoute().getId().equals(toStop.getRoute().getId())){

                    /// check direction :: Only allow when we find in one direction
//                    if(fromStop.getStopOrder() >= toStop.getStopOrder()){
//
//                        continue;
//
//                    }

                    ///  Get all stops of this route

                    List<RouteStop> routeStops = routeStopRepository.findByRoute_IdOrderByStopOrderAsc(fromStop.getRoute().getId());

                    List<String> stoppages = new ArrayList<>();

                    ///  forward direction
                    if(fromStop.getStopOrder() < toStop.getStopOrder()) {

                        for (RouteStop rs : routeStops) {

                            if (rs.getStopOrder() >= fromStop.getStopOrder() && rs.getStopOrder() <= toStop.getStopOrder()) {

                                stoppages.add(rs.getStop().getName());

                            }

                        }
                    }

                    /// Backward Direction
                    else if (fromStop.getStopOrder() > toStop.getStopOrder()) {

                        for(int i = routeStops.size() - 1; i >= 0; i--){

                            RouteStop rs = routeStops.get(i);

                            if(rs.getStopOrder() <= fromStop.getStopOrder() && rs.getStopOrder() >= toStop.getStopOrder()){

                                stoppages.add(rs.getStop().getName());

                            }

                        }
                    }

                    else{
                        throw new ResourceNotFoundException("Source and Destination cannot be the same");
                    }

                    /// create Response

                    RouteStopPassengerResponseDTO response = new RouteStopPassengerResponseDTO();

                    response.setFrom(from);
                    response.setTo(to);
                    response.setStoppages(stoppages);

                    return response;

                }

            }

        }

        throw new ResourceNotFoundException("No Route found from " + from + " to " + to);

    }


    private RouteStopResponseDTO convertToResponse(RouteStop routeStop) {

        RouteStopResponseDTO response = new RouteStopResponseDTO();

        response.setId(routeStop.getId());
        response.setRouteId(routeStop.getRoute().getId());
        response.setStopId(routeStop.getStop().getId());
        response.setStopOrder(routeStop.getStopOrder());

        return response;
    }
}
