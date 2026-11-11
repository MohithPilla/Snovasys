package com.snovasys.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snovasys.banking.dto.AdminStatusUpdateRequest;
import com.snovasys.banking.entity.AccountStatus;
import com.snovasys.banking.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // Generate valid JWT tokens for tests
        userToken = "Bearer " + jwtTokenProvider.generateToken(2L, "alice@bank.com", "ROLE_USER", "Alice Walker");
        adminToken = "Bearer " + jwtTokenProvider.generateToken(1L, "admin@bank.com", "ROLE_ADMIN", "System Admin");
    }

    @Test
    @DisplayName("Test 1: USER role accessing ADMIN users list receives 403 Forbidden")
    void testUserAccessAdminUsersEndpointForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 2: USER role attempting to block an account receives 403 Forbidden")
    void testUserAttemptBlockAccountForbidden() throws Exception {
        AdminStatusUpdateRequest request = new AdminStatusUpdateRequest(AccountStatus.BLOCKED);

        mockMvc.perform(put("/api/admin/users/3/status")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 3: USER role attempting to access audit logs receives 403 Forbidden")
    void testUserAccessAuditLogsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 4: ADMIN role successfully accesses ADMIN endpoints")
    void testAdminAccessAdminEndpointsSuccess() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Test 5: IDOR Protection - User profile endpoint binds strictly to SecurityContext")
    void testIdorProtectionProfileEndpoint() throws Exception {
        mockMvc.perform(get("/api/user/profile")
                        .header("Authorization", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@bank.com"))
                .andExpect(jsonPath("$.name").value("Alice Walker"));
    }
}
