package com.example.kyc_system.service;

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
import com.example.kyc_system.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
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

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final KycRequestRepository kycRequestRepository;
    private final KycDocumentService kycDocumentService;

    @Override
    public User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToDTO(user);
    }

    @Override
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return mapToDTO(user);
    }

    @Override
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new RuntimeException("User email already exists");
        }

        String hashedPassword = PasswordUtil.hashPassword(userDTO.getPassword());

        User user = User.builder()
                .name(userDTO.getName())
                .email(userDTO.getEmail())
                .mobileNumber(userDTO.getMobileNumber())
                .passwordHash(hashedPassword)
                .isActive(userDTO.getIsActive() != null ? userDTO.getIsActive() : true)
                .dob(userDTO.getDob())
                .build();

        User savedUser = userRepository.save(user);

        // Assign default ROLE_USER
        roleRepository.findByName("ROLE_USER").ifPresent(role -> {
            UserRole userRole = UserRole.builder()
                    .user(savedUser)
                    .role(role)
                    .build();
            userRoleRepository.save(userRole);
        });

        return mapToDTO(savedUser);
    }

    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserUpdateDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (userDTO.getName() != null) {
            user.setName(userDTO.getName());
        }
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail());
        }
        if (userDTO.getMobileNumber() != null) {
            user.setMobileNumber(userDTO.getMobileNumber());
        }
        if (userDTO.getDob() != null) {
            user.setDob(userDTO.getDob());
        }
        if (userDTO.getIsActive() != null) {
            user.setIsActive(userDTO.getIsActive());
        }

        // Note: Password update is explicitly excluded from this method as requested.

        User updatedUser = userRepository.save(user);
        return mapToDTO(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }

        // Delete associated KYC documents from file system
        kycRequestRepository.findByUserId(id).forEach(request -> {
            request.getKycDocuments().forEach(kycDocumentService::deleteDocument);
        });

        userRepository.deleteById(id);
    }

    @Override
    public String login(LoginDTO loginDTO) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginDTO.getEmail(), loginDTO.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        return jwtTokenProvider.generateToken(authentication);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> searchUsers(UserSearchDTO searchDTO, Pageable pageable) {
        return userRepository.findAll(UserSpecification.buildSpecification(searchDTO), pageable)
                .map(this::mapToDTO);
    }

    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .isActive(user.getIsActive())
                .dob(user.getDob())
                // Password is not returned
                .build();
    }
}
