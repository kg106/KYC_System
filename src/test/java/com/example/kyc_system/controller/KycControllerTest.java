package com.example.kyc_system.controller;

import com.example.kyc_system.entity.KycRequest;
import com.example.kyc_system.enums.DocumentType;
import com.example.kyc_system.enums.KycStatus;
import com.example.kyc_system.service.KycOrchestrationService;
import com.example.kyc_system.service.KycRequestService;
import com.example.kyc_system.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KycController.class)
@AutoConfigureMockMvc(addFilters = false)
class KycControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private KycOrchestrationService orchestrationService;

        @MockBean
        private KycRequestService requestService;

        @MockBean
        private UserService userService;

        @MockBean
        private com.example.kyc_system.security.JwtTokenProvider jwtTokenProvider;

        @MockBean
        private com.example.kyc_system.security.CustomAuthenticationEntryPoint authenticationEntryPoint;

        @MockBean
        private com.example.kyc_system.security.CustomAccessDeniedHandler accessDeniedHandler;

        @MockBean
        private com.example.kyc_system.security.SecurityService securityService;

        @Test
        void uploadDocument_Success() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "test.jpg",
                                MediaType.IMAGE_JPEG_VALUE,
                                "test image content".getBytes());

                given(orchestrationService.submitKyc(
                                eq(1L),
                                eq(DocumentType.AADHAAR),
                                any(),
                                eq("1234567890"))).willReturn(100L);

                mockMvc.perform(multipart("/api/kyc/upload")
                                .file(file)
                                .param("userId", "1")
                                .param("documentType", "AADHAAR")
                                .param("documentNumber", "1234567890"))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.message").value("KYC request submitted successfully"))
                                .andExpect(jsonPath("$.requestId").value(100));
        }

        @Test
        @WithMockUser
        void getKycStatus_Success() throws Exception {
                KycRequest request = new KycRequest();
                request.setId(1L);
                request.setStatus(KycStatus.VERIFIED.name());
                request.setFailureReason(null);
                request.setAttemptNumber(1);
                request.setCreatedAt(java.time.LocalDateTime.now());

                given(requestService.getLatestByUser(1L)).willReturn(Optional.of(request));
                given(securityService.canAccessUser(1L)).willReturn(true);

                mockMvc.perform(get("/api/kyc/status/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("VERIFIED"));
        }

        @Test
        void getKycStatus_NotFound() throws Exception {
                given(requestService.getLatestByUser(1L)).willReturn(Optional.empty());

                mockMvc.perform(get("/api/kyc/status/1"))
                                .andExpect(status().isNotFound());
        }
}
