package com.example.kyc_system.service.impl;

import com.example.kyc_system.context.TenantContext;
import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.dto.UserUpdateDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.entity.UserRole;
import com.example.kyc_system.repository.RoleRepository;
import com.example.kyc_system.repository.UserRepository;

import com.example.kyc_system.repository.UserRoleRepository;
import com.example.kyc_system.repository.KycRequestRepository;
import com.example.kyc_system.security.JwtTokenProvider;
import com.example.kyc_system.service.KycDocumentService;
import com.example.kyc_system.service.UserService;
import com.example.kyc_system.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.kyc_system.dto.UserSearchDTO;
import com.example.kyc_system.repository.specification.UserSpecification;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of UserService.
 * Handles user business logic with strict multi-tenant isolation.
 * Integrated with Spring Security for authentication and JWT generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final KycRequestRepository kycRequestRepository;
    private final KycDocumentService kycDocumentService;

    /**
     * Retrieves an active user, respecting the current tenant context.
     * Super admins can bypass tenant-id filtering.
     */
    @Override
    public User getActiveUser(Long userId) {
        if (TenantContext.isSuperAdmin()) {
            return userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        }
        String tenantId = TenantContext.getTenant();
        return userRepository.findByIdAndTenantId(userId, tenantId).orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Lists users within the current tenant scope.
     */
    @Override
    public List<UserDTO> getAllUsers() {
        if (TenantContext.isSuperAdmin()) {
            return userRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
        }
        String tenantId = TenantContext.getTenant();
        return userRepository.findByTenantId(tenantId).stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Gets user profile by ID with tenant isolation.
     */
    @Override
    public UserDTO getUserById(Long id) {
        if (TenantContext.isSuperAdmin()) {
            User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
            return mapToDTO(user);
        }
        String tenantId = TenantContext.getTenant();
        User user = userRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToDTO(user);
    }

    /**
     * Gets user profile by email with tenant isolation.
     */
    @Override
    public UserDTO getUserByEmail(String email) {
        if (TenantContext.isSuperAdmin()) {
            User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found with email: " + email));
            return mapToDTO(user);
        }
        String tenantId = TenantContext.getTenant();
        User user = userRepository.findByEmailAndTenantId(email, tenantId).orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return mapToDTO(user);
    }

    /**
     * Direct email lookup across all tenants (internal use).
     */
    @Override
    public UserDTO getUserByEmailDirect(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return mapToDTO(user);
    }

    /**
     * Registers a new user and assigns the default ROLE_USER.
     * Passwords are hashed before storage.
     */
    @Override
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        String tenantId = TenantContext.getTenant();

        if (userRepository.existsByEmailAndTenantId(userDTO.getEmail(), tenantId)) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .name(userDTO.getName())
                .email(userDTO.getEmail())
                .mobileNumber(userDTO.getMobileNumber())
                .passwordHash(PasswordUtil.hashPassword(userDTO.getPassword()))
                .isActive(userDTO.getIsActive() != null ? userDTO.getIsActive() : true)
                .dob(userDTO.getDob())
                .tenantId(tenantId) // ← scope to tenant
                .build();

        User savedUser = userRepository.save(user);

        roleRepository.findByName("ROLE_USER").ifPresent(role -> {
            userRoleRepository.save(UserRole.builder().user(savedUser).role(role).build());
        });

        log.info("User created: id={}, email={}, tenantId={}", savedUser.getId(), savedUser.getEmail(), tenantId);
        return mapToDTO(savedUser);
    }

    /**
     * Partially updates a user profile.
     */
    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserUpdateDTO userDTO) {
        String tenantId = TenantContext.getTenant();
        User user = userRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (userDTO.getName() != null)
            user.setName(userDTO.getName());
        if (userDTO.getEmail() != null)
            user.setEmail(userDTO.getEmail());
        if (userDTO.getMobileNumber() != null)
            user.setMobileNumber(userDTO.getMobileNumber());
        if (userDTO.getDob() != null)
            user.setDob(userDTO.getDob());
        if (userDTO.getIsActive() != null)
            user.setIsActive(userDTO.getIsActive());

        log.info("User updated: id={}", id);
        return mapToDTO(userRepository.save(user));
    }

    /**
     * Deletes a user and triggers cleanup of their physical documents.
     */
    @Override
    @Transactional
    public void deleteUser(Long id) {
        String tenantId = TenantContext.getTenant();
        User user = userRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        kycRequestRepository.findByUserIdAndTenantId(id, tenantId).forEach(request -> request.getKycDocuments().forEach(kycDocumentService::deleteDocument));

        userRepository.deleteById(id);
        log.info("User deleted: id={}, tenantId={}", id, tenantId);
    }

    /**
     * Handles user login with status checks and multi-tenant token generation.
     */
    @Override
    public String login(LoginDTO loginDTO) {
        // This will throw DisabledException automatically (from Fix 1)
        // if the account is inactive — but we add an explicit check
        // BEFORE authenticate() to return a cleaner error message.
        User user = userRepository.findByEmail(loginDTO.getEmail()).orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new DisabledException("Account is deactivated. Please contact support.");
        }

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginDTO.getEmail(), loginDTO.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("Login successful: email={}, tenantId={}", loginDTO.getEmail(), user.getTenantId());
        return jwtTokenProvider.generateToken(authentication, user.getTenantId());
    }

    /**
     * Advanced user search using JPA Specifications.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> searchUsers(UserSearchDTO searchDTO, Pageable pageable) {
        String tenantId = TenantContext.getTenant();
        boolean isSuperAdmin = TenantContext.isSuperAdmin();
        return userRepository.findAll(UserSpecification.buildSpecification(searchDTO, tenantId, isSuperAdmin), pageable).map(this::mapToDTO);
    }

    /** Internal mapper from entity to DTO. */
    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .isActive(user.getIsActive())
                .dob(user.getDob())
                .tenantId(user.getTenantId())
                .build();
    }
}
