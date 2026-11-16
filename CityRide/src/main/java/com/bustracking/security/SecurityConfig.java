package com.bustracking.security;

import com.bustracking.entity.Admin;
import com.bustracking.entity.Driver;
import com.bustracking.repository.AdminRepository;
import com.bustracking.repository.DriverRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Optional;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"message\":\"Invalid username or password\"}");
        };
    }

    @Bean
    AuthenticationProvider cityRideAuthenticationProvider(
            DriverRepository driverRepository,
            AdminRepository adminRepository,
            @Value("${app.security.admin.username:admin}") String defaultAdminUsername,
            @Value("${app.security.admin.password:Admin@12345}") String defaultAdminPassword) {

        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String inputUsername = authentication.getName() != null ? authentication.getName().trim() : "";
                String inputPassword = authentication.getCredentials() != null ? authentication.getCredentials().toString().trim() : "";

                if (inputUsername.isEmpty() || inputPassword.isEmpty()) {
                    throw new BadCredentialsException("Invalid username or password");
                }

                // 1. Default System Admin
                if (defaultAdminUsername.equals(inputUsername) && defaultAdminPassword.equals(inputPassword)) {
                    return new UsernamePasswordAuthenticationToken(
                            defaultAdminUsername,
                            inputPassword,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );
                }

                // 2. Database Admins: Username + 10-Digit Mobile Number
                Optional<Admin> dbAdmin = adminRepository.findByUsername(inputUsername);
                if (dbAdmin.isPresent() && dbAdmin.get().getPhone() != null
                        && dbAdmin.get().getPhone().equals(inputPassword)) {
                    return new UsernamePasswordAuthenticationToken(
                            dbAdmin.get().getUsername(),
                            inputPassword,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );
                }

                // 3. Drivers: Username + 10-Digit Mobile Number
                Optional<Driver> driverByPhone = driverRepository.findByPhone(inputPassword);
                if (driverByPhone.isPresent()) {
                    Driver d = driverByPhone.get();
                    boolean usernameMatches = (d.getUsername() != null && d.getUsername().equalsIgnoreCase(inputUsername));
                    if (usernameMatches) {
                        return new UsernamePasswordAuthenticationToken(
                                d.getPhone(),
                                inputPassword,
                                List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))
                        );
                    }
                }

                throw new BadCredentialsException("Invalid username or password");
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider cityRideAuthenticationProvider) throws Exception {
        AuthenticationEntryPoint entryPoint = jsonAuthenticationEntryPoint();
        http
            .authenticationProvider(cityRideAuthenticationProvider)
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/vendor/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/ws/location").permitAll()
                .requestMatchers("/api/passenger/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/stops", "/api/stops/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/buses/*/details").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/route-stops/search").permitAll()
                .requestMatchers("/api/driver/**").hasRole("DRIVER")
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"status\":403,\"message\":\"You do not have permission to perform this action\"}");
                })
            )
            .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
