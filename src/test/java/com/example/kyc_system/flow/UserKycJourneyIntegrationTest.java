package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.UserDTO;
// import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
// import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class UserKycJourneyIntegrationTest extends BaseIntegrationTest {

        @Test
        void testCompleteUserKycFlow() throws Exception {
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String email = "john.doe." + uniqueId + "@example.com";
                String password = "Password@123";

                String randomDigits = String.format("%06d", (int) (Math.random() * 1000000));

                // 1. Registration Flow
                UserDTO newUser = UserDTO.builder()
                                .name("John Doe")
                                .email(email)
                                .mobileNumber("9876" + randomDigits)
                                .password(password)
                                .dob(LocalDate.of(1990, 1, 1))
                                .build();

                MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newUser)))
                                .andExpect(status().isCreated())
                                .andReturn();

                String registerResponse = registerResult.getResponse().getContentAsString();
                Long userId = objectMapper.readTree(registerResponse).get("id").asLong();

                // 2. Authentication Flow (Login to get JWT Token)
                LoginDTO loginDTO = new LoginDTO();
                loginDTO.setEmail(email);
                loginDTO.setPassword(password);

                // Correct password flow
                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", "default")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginDTO)))
                                .andExpect(status().isOk())
                                .andReturn();

                String tokenResponse = loginResult.getResponse().getContentAsString();
                String jwtToken = objectMapper.readTree(tokenResponse).get("accessToken").asText();
                assertNotNull(jwtToken);

                // 3. KYC Upload Flow
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "id_card.jpg",
                                MediaType.IMAGE_JPEG_VALUE,
                                "dummy image content".getBytes());

                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", String.valueOf(userId))
                                .param("documentType", "PAN")
                                .param("documentNumber", "ABCDE1234F")
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.requestId").exists())
                                .andExpect(jsonPath("$.message").value("KYC request submitted successfully"));

                // 4. Checking KYC Status Flow
                mockMvc.perform(get("/api/kyc/status/" + userId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").exists())
                                .andExpect(jsonPath("$.requestId").exists());

                // 5. Validating all history (since user has access to themselves)
                mockMvc.perform(get("/api/kyc/status/all/" + userId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .header("X-Tenant-ID", "default"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$[0].requestId").exists());
        }
}
