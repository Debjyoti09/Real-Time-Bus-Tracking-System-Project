package com.bustracking.repository;
import com.bustracking.entity.RouteStop;
import com.bustracking.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    List<RouteStop> findByStop_Name(String from);

    List<RouteStop> findByRoute_IdOrderByStopOrderAsc(Long routeId);
}
