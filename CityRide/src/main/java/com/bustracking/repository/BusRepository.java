package com.bustracking.repository;

import com.bustracking.entity.Bus;
import com.bustracking.enums.BusStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BusRepository extends JpaRepository<Bus,Long> {

    List<Bus> findByRoute_IdInAndStatus(Collection<Long> routeIds, BusStatus status);

    List<Bus> findByStatusIn(Collection<BusStatus> statuses);

}
