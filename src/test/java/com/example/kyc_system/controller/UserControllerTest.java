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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createUser_Success() throws Exception {
        UserDTO userDTO = new UserDTO();
        userDTO.setName("New User");
        userDTO.setEmail("new@example.com");
        userDTO.setPassword("password");

        UserDTO createdUser = new UserDTO();
        createdUser.setId(1L);
        createdUser.setName("New User");
        createdUser.setEmail("new@example.com");

        given(userService.createUser(any(UserDTO.class))).willReturn(createdUser);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New User"));
    }

    @Test
    void getAllUsers_Success() throws Exception {
        UserDTO user1 = new UserDTO();
        user1.setId(1L);
        user1.setName("User 1");

        UserDTO user2 = new UserDTO();
        user2.setId(2L);
        user2.setName("User 2");

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

        UserDTO updatedUser = new UserDTO();
        updatedUser.setId(1L);
        updatedUser.setName("Updated User");

        given(userService.updateUser(eq(1L), any(UserDTO.class))).willReturn(updatedUser);

        mockMvc.perform(put("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated User"));
    }

    @Test
    void deleteUser_Success() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void forgotPassword_Success() throws Exception {
        given(userService.forgotPassword(1L)).willReturn("newPassword123");

        mockMvc.perform(post("/api/users/1/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("New Password: newPassword123"));
    }
}
