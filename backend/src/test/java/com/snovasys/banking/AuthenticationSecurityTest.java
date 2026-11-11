package com.snovasys.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snovasys.banking.dto.AuthRequest;
import com.snovasys.banking.dto.RegisterRequest;
import com.snovasys.banking.entity.User;
import com.snovasys.banking.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Test 1: Successful login with valid credentials returns 200 and JWT token")
    void testValidLogin() throws Exception {
        AuthRequest request = new AuthRequest("alice@bank.com", "Alice@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value("alice@bank.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("Test 2: Invalid password returns 401 Unauthorized")
    void testInvalidPassword() throws Exception {
        AuthRequest request = new AuthRequest("alice@bank.com", "WrongPassword!999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 3: Unauthenticated request to protected endpoint returns 401")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Test 4: Password Hashing - Never store plaintext passwords in database")
    void testPasswordHashing() throws Exception {
        String rawPassword = "SecurePassword@2026";
        RegisterRequest register = new RegisterRequest("Test User", "testuser@bank.com", rawPassword);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("testuser@bank.com").orElseThrow();

        // 1. Stored password must NOT be equal to raw password
        assertThat(user.getPassword()).isNotEqualTo(rawPassword);

        // 2. Stored password must start with standard BCrypt prefix ($2a$ or $2b$)
        assertThat(user.getPassword()).startsWith("$2a$");

        // 3. BCryptPasswordEncoder must verify matching hash
        assertThat(passwordEncoder.matches(rawPassword, user.getPassword())).isTrue();
    }
}
