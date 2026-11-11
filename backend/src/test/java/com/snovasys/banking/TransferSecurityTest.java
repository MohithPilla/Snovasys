package com.snovasys.banking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snovasys.banking.dto.TransferRequest;
import com.snovasys.banking.entity.Account;
import com.snovasys.banking.entity.AccountStatus;
import com.snovasys.banking.entity.User;
import com.snovasys.banking.repository.AccountRepository;
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
import java.math.RoundingMode;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private String aliceToken;
    private String bobToken;
    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        User alice = userRepository.findByEmail("alice@bank.com").orElseThrow();
        User bob = userRepository.findByEmail("bob@bank.com").orElseThrow();

        aliceAccount = accountRepository.findByUserId(alice.getId()).orElseThrow();
        bobAccount = accountRepository.findByUserId(bob.getId()).orElseThrow();

        // Ensure both accounts are ACTIVE and have known balances
        aliceAccount.setAccountStatus(AccountStatus.ACTIVE);
        aliceAccount.setBalance(new BigDecimal("1000.0000"));
        accountRepository.save(aliceAccount);

        bobAccount.setAccountStatus(AccountStatus.ACTIVE);
        bobAccount.setBalance(new BigDecimal("500.0000"));
        accountRepository.save(bobAccount);

        aliceToken = "Bearer " + jwtTokenProvider.generateToken(alice.getId(), alice.getEmail(), alice.getRole().name(), alice.getName());
        bobToken = "Bearer " + jwtTokenProvider.generateToken(bob.getId(), bob.getEmail(), bob.getRole().name(), bob.getName());
    }

    @Test
    @DisplayName("Test 1: Successful money transfer - debit sender, credit receiver, verify exact balance conservation")
    void testSuccessfulTransfer() throws Exception {
        BigDecimal transferAmount = new BigDecimal("200.00");
        String idempotencyKey = UUID.randomUUID().toString();

        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), transferAmount, idempotencyKey);

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(200.00));

        Account updatedAlice = accountRepository.findById(aliceAccount.getId()).orElseThrow();
        Account updatedBob = accountRepository.findById(bobAccount.getId()).orElseThrow();

        assertThat(updatedAlice.getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(updatedBob.getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    @DisplayName("Test 2: Insufficient balance rejection - balance remains unchanged")
    void testInsufficientBalanceRejection() throws Exception {
        BigDecimal excessiveAmount = new BigDecimal("99999.00");
        String idempotencyKey = UUID.randomUUID().toString();

        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), excessiveAmount, idempotencyKey);

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient balance")));

        Account updatedAlice = accountRepository.findById(aliceAccount.getId()).orElseThrow();
        assertThat(updatedAlice.getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Test 3: Negative amount rejection")
    void testNegativeAmountRejection() throws Exception {
        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), new BigDecimal("-50.00"), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 4: Zero amount rejection")
    void testZeroAmountRejection() throws Exception {
        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), BigDecimal.ZERO, UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 5: Self-transfer rejection")
    void testSelfTransferRejection() throws Exception {
        TransferRequest request = new TransferRequest(aliceAccount.getAccountNumber(), new BigDecimal("50.00"), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Self-transfers are not allowed")));
    }

    @Test
    @DisplayName("Test 6: Inactive/Blocked sender account cannot transfer money")
    void testBlockedSenderTransferRejection() throws Exception {
        aliceAccount.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(aliceAccount);

        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), new BigDecimal("50.00"), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 7: Transfer to blocked receiver is rejected")
    void testBlockedReceiverTransferRejection() throws Exception {
        bobAccount.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(bobAccount);

        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), new BigDecimal("50.00"), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 8: Duplicate transfer rejection via Idempotency Key (HTTP 409 Conflict)")
    void testDuplicateIdempotencyKey() throws Exception {
        String sharedIdempotencyKey = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(bobAccount.getAccountNumber(), new BigDecimal("30.00"), sharedIdempotencyKey);

        // First attempt succeeds
        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second attempt with identical idempotency key is rejected
        mockMvc.perform(post("/api/transfer")
                        .header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been processed")));
    }
}
