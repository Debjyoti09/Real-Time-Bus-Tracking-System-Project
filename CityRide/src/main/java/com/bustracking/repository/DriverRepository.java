package com.bustracking.repository;

import com.bustracking.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    boolean existsByPhone(String phone);

    boolean existsByLicenseNumber(String licenseNumber);

    Optional<Driver> findByPhone(String phone);

    Optional<Driver> findByLicenseNumber(String licenseNumber);

    List<Driver> findByUsernameIgnoreCase(String username);

    List<Driver> findByPreferredRoutes_Id(Long routeId);
}
