package com.example.kyc_system.controller;

import com.example.kyc_system.dto.UserDTO;
import com.example.kyc_system.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private com.example.kyc_system.security.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.example.kyc_system.security.CustomAuthenticationEntryPoint authenticationEntryPoint;

    @MockBean
    private com.example.kyc_system.security.CustomAccessDeniedHandler accessDeniedHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllUsers_Success() throws Exception {
        UserDTO user1 = new UserDTO();
        user1.setId(1L);
        user1.setName("User One");
        user1.setEmail("user1@example.com");
        user1.setMobileNumber("1234567890");

        UserDTO user2 = new UserDTO();
        user2.setId(2L);
        user2.setName("User Two");
        user2.setEmail("user2@example.com");
        user2.setMobileNumber("0987654321");

        List<UserDTO> users = Arrays.asList(user1, user2);

        given(userService.getAllUsers()).willReturn(users);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getUserById_Success() throws Exception {
        UserDTO user = new UserDTO();
        user.setId(1L);
        user.setName("User 1");

        given(userService.getUserById(1L)).willReturn(user);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("User 1"));
    }

    @Test
    void updateUser_Success() throws Exception {
        UserDTO updateDTO = new UserDTO();
        updateDTO.setName("Updated User");
        updateDTO.setEmail("updated@example.com");
        updateDTO.setMobileNumber("1234567890");
        updateDTO.setPassword("Password@123");

        UserDTO updatedUser = new UserDTO();
        updatedUser.setId(1L);
        updatedUser.setName("Updated User");
        updatedUser.setEmail("updated@example.com");
        updatedUser.setMobileNumber("1234567890");

        given(userService.updateUser(eq(1L), any(UserDTO.class))).willReturn(updatedUser);

        mockMvc.perform(patch("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated User"));
    }

    @Test
    void deleteUser_Success() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("User deleted successfully"));
    }

    @Test
    void forgotPassword_Success() throws Exception {
        given(userService.forgotPassword(1L)).willReturn("newPassword123");

        mockMvc.perform(post("/api/users/1/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("New Password: newPassword123"));
    }

    @Test
    void getUserById_ShouldReturnNotFound_WhenUserDoesNotExist() throws Exception {
        given(userService.getUserById(999L)).willThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteUser_ShouldReturnNotFound_WhenUserDoesNotExist() throws Exception {
        doThrow(new RuntimeException("User not found")).when(userService).deleteUser(999L);

        mockMvc.perform(delete("/api/users/999"))
                .andExpect(status().isInternalServerError());
    }
}
