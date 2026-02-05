package com.example.kyc_system.service;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.repository.UserRepository;
import com.example.kyc_system.util.PasswordUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

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

        // BaseEntity fields like createdAt handled by @PrePersist if exists or DB
        // default,
        // but here we just rely on standard JPA save. BaseEntity might need handling if
        // not auto-managed.
        // Assuming BaseEntity uses @CreationTimestamp or similar, or we set it manually
        // if needed.
        // Let's assume standard behavior for now.

        User savedUser = userRepository.save(user);
        return mapToDTO(savedUser);
    }

    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setName(userDTO.getName());
        user.setEmail(userDTO.getEmail());
        user.setMobileNumber(userDTO.getMobileNumber());
        if (userDTO.getDob() != null) {
            user.setDob(userDTO.getDob());
        }
        if (userDTO.getIsActive() != null) {
            user.setIsActive(userDTO.getIsActive());
        }

        User updatedUser = userRepository.save(user);
        return mapToDTO(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public String forgotPassword(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        // Generate a random password (simple implementation for demo)
        String newPassword = UUID.randomUUID().toString().substring(0, 8);
        String hashedPassword = PasswordUtil.hashPassword(newPassword);

        user.setPasswordHash(hashedPassword);
        userRepository.save(user);

        return newPassword;
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
