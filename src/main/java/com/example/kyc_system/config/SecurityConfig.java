package com.example.kyc_system.config;

import com.example.kyc_system.filter.TenantResolutionFilter;
import com.example.kyc_system.security.CustomAccessDeniedHandler;
import com.example.kyc_system.security.CustomAuthenticationEntryPoint;
import com.example.kyc_system.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 * - Stateless session (JWT-based, no server-side session)
 * - CSRF disabled (not needed for stateless REST APIs)
 * - Role hierarchy: SUPER_ADMIN > TENANT_ADMIN > ADMIN
 * - Custom exception handlers for 401 (unauthenticated) and 403 (forbidden)
 * - Filter chain order: JWT auth filter → Tenant resolution filter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables @PreAuthorize, @Secured annotations on methods
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final CustomAuthenticationEntryPoint authenticationEntryPoint;
        private final CustomAccessDeniedHandler accessDeniedHandler;
        private final TenantResolutionFilter tenantResolutionFilter;

        /** BCrypt password encoder bean used for hashing passwords. */
        @Bean
        public static PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        /** Exposes Spring's default AuthenticationManager as a bean. */
        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
                return configuration.getAuthenticationManager();
        }

        /**
         * Defines role hierarchy so higher roles automatically inherit permissions:
         * SUPER_ADMIN inherits TENANT_ADMIN, which inherits ADMIN.
         */
        @Bean
        public RoleHierarchy roleHierarchy() {
                return RoleHierarchyImpl.fromHierarchy(
                                "ROLE_SUPER_ADMIN > ROLE_TENANT_ADMIN\n" +
                                                "ROLE_TENANT_ADMIN > ROLE_ADMIN");
        }

        /**
         * Applies the RoleHierarchy to method security (@PreAuthorize).
         */
        @Bean
        public org.springframework.security.access.expression.method.MethodSecurityExpressionHandler methodSecurityExpressionHandler(
                        RoleHierarchy roleHierarchy) {
                org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler methodHandler = new org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler();
                methodHandler.setRoleHierarchy(roleHierarchy);
                return methodHandler;
        }

        /**
         * Configures the HTTP Security filter chain:
         * - Public: /api/auth/**, Swagger docs, and all GET /api/** endpoints
         * - Superadmin only: /api/super/**
         * - Everything else: requires authentication
         */
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                http.csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers("/error").permitAll()
                                                .requestMatchers("/api/super/**")
                                                .hasRole("SUPER_ADMIN")
                                                .requestMatchers("/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/actuator",
                                                                "/actuator/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(authenticationEntryPoint)
                                                .accessDeniedHandler(accessDeniedHandler));

                // Filter order matters:
                // 1. JwtAuthenticationFilter runs first — extracts user identity from JWT
                // 2. TenantResolutionFilter runs after — resolves tenant from JWT claims or
                // headers
                http.addFilterBefore(jwtAuthenticationFilter,
                                UsernamePasswordAuthenticationFilter.class);
                http.addFilterAfter(tenantResolutionFilter,
                                JwtAuthenticationFilter.class);

                return http.build();
        }
}
