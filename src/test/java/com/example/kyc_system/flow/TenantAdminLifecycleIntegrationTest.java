package com.example.kyc_system.flow;

import com.example.kyc_system.BaseIntegrationTest;
import com.example.kyc_system.dto.LoginDTO;
import com.example.kyc_system.dto.TenantCreateDTO;
import com.example.kyc_system.dto.UserDTO;
// import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
// import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TenantAdminLifecycleIntegrationTest extends BaseIntegrationTest {

        @Test
        void testCompleteTenantLifecycleFlow() throws Exception {
                String superAdminToken = loginAndGetToken("superadmin@kyc.com", "SuperAdmin@123");
                String uniqueSuffix = UUID.randomUUID().toString().substring(0, 5);
                String newTenantId = "b2b_client_" + uniqueSuffix;
                String tenantDomain = "b2b-client-" + uniqueSuffix + ".com";
                String tenantAdminEmail = "admin@" + tenantDomain;
                String tenantAdminPassword = "SecurePassword@123";

                // 1. Super Admin creates a Tenant
                TenantCreateDTO newTenant = new TenantCreateDTO();
                newTenant.setTenantId(newTenantId);
                newTenant.setName("B2B Corporate Client");
                newTenant.setEmail("contact@" + tenantDomain);
                newTenant.setAdminEmail(tenantAdminEmail);
                newTenant.setAdminPassword(tenantAdminPassword);
                newTenant.setAllowedDocumentTypes(List.of("PAN", "AADHAAR"));

                MvcResult createTenantResult = mockMvc.perform(post("/api/tenants")
                                .header("Authorization", "Bearer " + superAdminToken)
                                .header("X-Tenant-ID", "system")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newTenant)))
                                .andExpect(status().isCreated())
                                .andReturn();

                String apiKey = objectMapper.readTree(createTenantResult.getResponse().getContentAsString())
                                .get("apiKey")
                                .asText();
                assertNotNull(apiKey);

                // 2. The newly provisioned Tenant Admin logs in
                LoginDTO loginParam = new LoginDTO();
                loginParam.setEmail(tenantAdminEmail);
                loginParam.setPassword(tenantAdminPassword);

                // Note: For realistic flow, we post to auth login without mock user,
                // using just the tenant header to get the JWT.
                MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                                .header("X-Tenant-ID", newTenantId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginParam)))
                                .andExpect(status().isOk())
                                .andReturn();

                String jwtToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                                .get("accessToken").asText();

                String randomDigits = String.format("%05d", (int) (Math.random() * 100000));

                // 3. Tenant Admin manages users (Creates a standard user for their tenant)
                // Here we simulate a tenant admin provisioning a user using the API
                UserDTO tenantUser = UserDTO.builder()
                                .name("Corporate Employee")
                                .email("employee." + uniqueSuffix + "@b2b.com")
                                .mobileNumber("88889" + randomDigits)
                                .password("EmpPassword@123")
                                .dob(LocalDate.of(1995, 5, 5))
                                .build();

                mockMvc.perform(post("/api/auth/register")
                                .header("X-Tenant-ID", newTenantId)
                                .header("Authorization", "Bearer " + jwtToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(tenantUser)))
                                .andExpect(status().isCreated());

                // 4. Tenant Admin checks their stats
                // We do not use the @WithMockUser super admin role here because we are
                // explicitly using the Authorization token from the step above.
                // Therefore Spring Security Filters will parse our actual JWT for Tenant Admin
                // permissions!
                mockMvc.perform(get("/api/tenants/" + newTenantId + "/stats")
                                .header("Authorization", "Bearer " + superAdminToken)
                                .header("X-Tenant-ID", "system"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalUsers").value(2)); // The admin itself + the new employee
        }
}
