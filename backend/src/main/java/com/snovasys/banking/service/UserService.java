package com.snovasys.banking.service;

import com.snovasys.banking.dto.UserProfileResponse;
import com.snovasys.banking.entity.Account;
import com.snovasys.banking.entity.Transaction;
import com.snovasys.banking.entity.User;
import com.snovasys.banking.exception.ResourceNotFoundException;
import com.snovasys.banking.repository.AccountRepository;
import com.snovasys.banking.repository.TransactionRepository;
import com.snovasys.banking.repository.UserRepository;
import com.snovasys.banking.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SecurityUtils securityUtils;

    public UserService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       TransactionRepository transactionRepository,
                       SecurityUtils securityUtils) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.securityUtils = securityUtils;
    }

    /**
     * Retrieves the profile of the currently authenticated user.
     * Guaranteed IDOR-safe because the user identity is read from the server-side SecurityContext.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        User user = securityUtils.getCurrentUser();
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + user.getId()));

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getAccountStatus().name(),
                account.getAccountNumber(),
                account.getBalance(),
                user.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentUserBalance() {
        User user = securityUtils.getCurrentUser();
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + user.getId()));
        return account.getBalance();
    }

    @Transactional(readOnly = true)
    public List<Transaction> getCurrentUserTransactions() {
        User user = securityUtils.getCurrentUser();
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + user.getId()));
        return transactionRepository.findByAccountId(account.getId());
    }

    /**
     * Looks up an account by its account number so users can confirm the receiver's name
     * prior to transferring money.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> lookupAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        Map<String, Object> details = new HashMap<>();
        details.put("accountNumber", account.getAccountNumber());
        details.put("accountHolderName", account.getUser().getName());
        details.put("status", account.getAccountStatus().name());
        return details;
    }
}
