package com.example.kyc_system.service;

import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.entity.User;
import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.dto.UserUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.kyc_system.dto.UserSearchDTO;
import java.util.List;

/**
 * Service interface for User management.
 * Provides methods for CRUD operations, authentication, and searching.
 */
public interface UserService {
    /**
     * Retrieves an active user by ID. Throws exception if user is inactive or not found.
     *
     * @param userId user ID
     * @return the active User entity
     */
    User getActiveUser(Long userId);

    /**
     * Lists all users in the current tenant's context.
     *
     * @return list of UserDTOs
     */
    List<UserDTO> getAllUsers();

    /**
     * Retrieves a user by ID.
     *
     * @param id user ID
     * @return UserDTO
     */
    UserDTO getUserById(Long id);

    /**
     * Retrieves a user by email within the current tenant.
     *
     * @param email user email
     * @return UserDTO
     */
    UserDTO getUserByEmail(String email);

    /**
     * Retrieves a user across all tenants (used during login/initial search).
     *
     * @param email user email
     * @return UserDTO
     */
    UserDTO getUserByEmailDirect(String email);

    /**
     * Creates a new user.
     *
     * @param userDTO user details
     * @return created UserDTO
     */
    UserDTO createUser(UserDTO userDTO);

    /**
     * Updates an existing user's profile.
     *
     * @param id user ID
     * @param userDTO updated fields
     * @return updated UserDTO
     */
    UserDTO updateUser(Long id, UserUpdateDTO userDTO);

    /**
     * Deletes a user by ID.
     *
     * @param id user ID
     */
    void deleteUser(Long id);

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param loginDTO credentials
     * @return JWT access token
     */
    String login(LoginDTO loginDTO);

    /**
     * Searches users with filters and pagination.
     *
     * @param searchDTO filters
     * @param pageable pagination
     * @return paged UserDTOs
     */
    Page<UserDTO> searchUsers(UserSearchDTO searchDTO, Pageable pageable);
}
