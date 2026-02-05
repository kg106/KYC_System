package com.example.kyc_system.service;

import com.example.kyc_system.entity.User;

public interface UserService {
    User getActiveUser(Long userId);

    java.util.List<com.example.kyc_system.dto.UserDTO> getAllUsers();

    com.example.kyc_system.dto.UserDTO getUserById(Long id);

    com.example.kyc_system.dto.UserDTO createUser(com.example.kyc_system.dto.UserDTO userDTO);

    com.example.kyc_system.dto.UserDTO updateUser(Long id, com.example.kyc_system.dto.UserDTO userDTO);

    void deleteUser(Long id);

    String forgotPassword(Long id);
}
