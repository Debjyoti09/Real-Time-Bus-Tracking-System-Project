package com.bustracking.controller;

import com.bustracking.dto.AdminRequestDTO;
import com.bustracking.dto.AdminResponseDTO;
import com.bustracking.dto.BusResponseDTO;
import com.bustracking.entity.Admin;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.AdminRepository;
import com.bustracking.service.BusService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BusService busService;
    private final AdminRepository adminRepository;
    private final String defaultAdminUsername;

    public AdminController(BusService busService,
                           AdminRepository adminRepository,
                           @Value("${app.security.admin.username:admin}") String defaultAdminUsername) {
        this.busService = busService;
        this.adminRepository = adminRepository;
        this.defaultAdminUsername = defaultAdminUsername;
    }

    @GetMapping("/buses")
    public List<BusResponseDTO> getAllBuses() {
        return busService.getAllBuses();
    }

    @GetMapping("/accounts")
    @Transactional(readOnly = true)
    public List<AdminResponseDTO> getAllAdmins() {
        List<AdminResponseDTO> list = new ArrayList<>();
        AdminResponseDTO primary = new AdminResponseDTO();
        primary.setId(0L);
        primary.setName("Primary System Admin");
        primary.setUsername(defaultAdminUsername);
        primary.setPhone("System Default");
        list.add(primary);

        for (Admin admin : adminRepository.findAll()) {
            list.add(toDto(admin));
        }
        return list;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AdminResponseDTO createAdmin(@Valid @RequestBody AdminRequestDTO request) {
        String uname = request.getUsername().trim();
        String phone = request.getPhone().trim();
        if (defaultAdminUsername.equalsIgnoreCase(uname) || adminRepository.existsByUsername(uname)) {
            throw new IllegalArgumentException("Admin username already exists");
        }
        if (adminRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Admin mobile number already exists");
        }

        Admin admin = new Admin();
        admin.setName(request.getName().trim());
        admin.setUsername(uname);
        admin.setPhone(phone);

        Admin saved = adminRepository.save(admin);
        return toDto(saved);
    }

    @DeleteMapping("/accounts/{id}")
    @Transactional
    public void deleteAdmin(@PathVariable Long id, Authentication auth) {
        if (id == null || id == 0L) {
            throw new IllegalStateException("Primary system admin cannot be deleted");
        }
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + id));
        if (auth != null && admin.getUsername().equalsIgnoreCase(auth.getName())) {
            throw new IllegalStateException("You cannot delete your own currently logged-in admin account");
        }
        adminRepository.delete(admin);
    }

    private AdminResponseDTO toDto(Admin admin) {
        AdminResponseDTO dto = new AdminResponseDTO();
        dto.setId(admin.getId());
        dto.setName(admin.getName());
        dto.setUsername(admin.getUsername());
        dto.setPhone(admin.getPhone());
        return dto;
    }
}
