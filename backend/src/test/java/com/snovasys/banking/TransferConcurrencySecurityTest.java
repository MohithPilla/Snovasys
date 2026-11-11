package com.snovasys.banking;

import com.snovasys.banking.dto.TransferRequest;
import com.snovasys.banking.entity.Account;
import com.snovasys.banking.entity.AccountStatus;
import com.snovasys.banking.entity.User;
import com.snovasys.banking.repository.AccountRepository;
import com.snovasys.banking.repository.UserRepository;
import com.snovasys.banking.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferConcurrencySecurityTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;
    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        alice = userRepository.findByEmail("alice@bank.com").orElseThrow();
        bob = userRepository.findByEmail("bob@bank.com").orElseThrow();

        aliceAccount = accountRepository.findByUserId(alice.getId()).orElseThrow();
        bobAccount = accountRepository.findByUserId(bob.getId()).orElseThrow();

        // Alice starts with exactly $100.00
        aliceAccount.setBalance(new BigDecimal("100.0000"));
        aliceAccount.setAccountStatus(AccountStatus.ACTIVE);
        accountRepository.saveAndFlush(aliceAccount);

        // Bob starts with $0.00
        bobAccount.setBalance(new BigDecimal("0.0000"));
        bobAccount.setAccountStatus(AccountStatus.ACTIVE);
        accountRepository.saveAndFlush(bobAccount);
    }

    @Test
    @DisplayName("Concurrency & Double-Spending Test: 5 simultaneous threads transfer $60 from $100 balance")
    void testConcurrentTransfersDoubleSpendingPrevention() throws InterruptedException {
        int threadCount = 5;
        BigDecimal transferAmount = new BigDecimal("60.00");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                // Configure thread security context as Alice
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(
                        alice.getEmail(),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                ));
                SecurityContextHolder.setContext(context);

                try {
                    startLatch.await(); // Synchronize all threads to fire at the exact same instant
                    TransferRequest request = new TransferRequest(
                            bobAccount.getAccountNumber(),
                            transferAmount,
                            UUID.randomUUID().toString()
                    );
                    transferService.transferMoney(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                    finishLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();

        // CRITICAL INVARIANT VERIFICATION:
        // Because balance is $100, exactly ONE thread can withdraw $60.
        // The remaining 4 threads MUST fail with InsufficientBalanceException.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(4);

        // Verify balances in database
        Account finalAlice = accountRepository.findById(aliceAccount.getId()).orElseThrow();
        Account finalBob = accountRepository.findById(bobAccount.getId()).orElseThrow();

        assertThat(finalAlice.getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(finalBob.getBalance().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo(new BigDecimal("60.00"));

        // Money conservation check: Total starting ($100) == Total final ($40 + $60)
        BigDecimal totalFinal = finalAlice.getBalance().add(finalBob.getBalance()).setScale(2, RoundingMode.HALF_UP);
        assertThat(totalFinal).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
