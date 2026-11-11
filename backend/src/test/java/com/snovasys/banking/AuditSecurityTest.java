package com.snovasys.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snovasys.banking.dto.AdminStatusUpdateRequest;
import com.snovasys.banking.dto.TransferRequest;
import com.snovasys.banking.entity.Account;
import com.snovasys.banking.entity.AccountStatus;
import com.snovasys.banking.entity.AuditLog;
import com.snovasys.banking.entity.User;
import com.snovasys.banking.repository.AccountRepository;
import com.snovasys.banking.repository.AuditLogRepository;
import com.snovasys.banking.repository.UserRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    private User alice;
    private User bob;
    private User admin;
    private Account bobAccount;
    private String aliceToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        alice = userRepository.findByEmail("alice@bank.com").orElseThrow();
        bob = userRepository.findByEmail("bob@bank.com").orElseThrow();
        admin = userRepository.findByEmail("admin@bank.com").orElseThrow();

        bobAccount = accountRepository.findByUserId(bob.getId()).orElseThrow();

        aliceToken = "Bearer " + jwtTokenProvider.generateToken(alice.getId(), alice.getEmail(), alice.getRole().name(), alice.getName());
        adminToken = "Bearer " + jwtTokenProvider.generateToken(admin.getId(), admin.getEmail(), admin.getRole().name(), admin.getName());
    }

    @Test
    @DisplayName("Audit Test 1: Successful transfer automatically generates an immutable audit record")
    void testSuccessfulTransferGeneratesAuditRecord() throws Exception {
        int initialCount = auditLogRepository.findAll().size();

        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), new BigDecimal("10.00"), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs.size()).isGreaterThan(initialCount);

        AuditLog latest = logs.get(logs.size() - 1);
        assertThat(latest.getAction()).isEqualTo("TRANSFER_SUCCESS");
        // Verify actor ID was taken securely from Alice's authenticated token
        assertThat(latest.getActorId()).isEqualTo(alice.getId());
        assertThat(latest.getDescription()).contains(bobAccount.getAccountNumber());
    }

    @Test
    @DisplayName("Audit Test 2: Failed transfer attempt records an audit event")
    void testFailedTransferGeneratesAuditRecord() throws Exception {
        int initialCount = auditLogRepository.findAll().size();

        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), new BigDecimal("999999.00"), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs.size()).isGreaterThan(initialCount);

        AuditLog latest = logs.get(logs.size() - 1);
        assertThat(latest.getAction()).isEqualTo("TRANSFER_FAILED");
        assertThat(latest.getActorId()).isEqualTo(alice.getId());
        assertThat(latest.getDescription()).contains("Insufficient funds");
    }

    @Test
    @DisplayName("Audit Test 3: Admin account blocking action records an administrative audit event")
    void testAdminBlockActionGeneratesAuditRecord() throws Exception {
        AdminStatusUpdateRequest request = new AdminStatusUpdateRequest(AccountStatus.BLOCKED);

        mockMvc.perform(put("/api/admin/users/" + bob.getId() + "/status")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        AuditLog latest = logs.get(logs.size() - 1);
        assertThat(latest.getAction()).isEqualTo("USER_BLOCKED");
        assertThat(latest.getActorId()).isEqualTo(admin.getId());
        assertThat(latest.getDescription()).contains("BLOCKED");
    }
}
