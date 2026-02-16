package com.example.kyc_system.service;

import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.dto.UserUpdateDTO;
import java.util.List;

public interface UserService {
    User getActiveUser(Long userId);

    List<UserDTO> getAllUsers();

    UserDTO getUserById(Long id);

    UserDTO createUser(UserDTO userDTO);

    UserDTO updateUser(Long id, UserUpdateDTO userDTO);

    void deleteUser(Long id);

    String login(LoginDTO loginDTO);
}
