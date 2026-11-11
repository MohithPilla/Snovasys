package com.snovasys.banking.service;

import com.snovasys.banking.dto.AdminUserResponse;
import com.snovasys.banking.entity.*;
import com.snovasys.banking.exception.ResourceNotFoundException;
import com.snovasys.banking.repository.AccountRepository;
import com.snovasys.banking.repository.AuditLogRepository;
import com.snovasys.banking.repository.TransactionRepository;
import com.snovasys.banking.repository.UserRepository;
import com.snovasys.banking.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    public AdminService(UserRepository userRepository,
                        AccountRepository accountRepository,
                        TransactionRepository transactionRepository,
                        AuditLogRepository auditLogRepository,
                        AuditService auditService,
                        SecurityUtils securityUtils) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        List<AdminUserResponse> responses = new ArrayList<>();

        for (User user : users) {
            Account account = accountRepository.findByUserId(user.getId()).orElse(null);
            responses.add(new AdminUserResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getAccountStatus().name(),
                    account != null ? account.getId() : null,
                    account != null ? account.getAccountNumber() : "N/A",
                    account != null ? account.getBalance() : BigDecimal.ZERO,
                    account != null ? account.getAccountStatus().name() : "N/A",
                    user.getCreatedAt()
            ));
        }
        return responses;
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long targetUserId, AccountStatus newStatus) {
        Long adminActorId = securityUtils.getCurrentUserId();

        if (adminActorId.equals(targetUserId)) {
            throw new IllegalArgumentException("Admins cannot change their own account status");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + targetUserId));

        user.setAccountStatus(newStatus);
        userRepository.save(user);

        Account account = accountRepository.findByUserId(user.getId()).orElse(null);
        if (account != null) {
            account.setAccountStatus(newStatus);
            accountRepository.save(account);
        }

        String action = (newStatus == AccountStatus.BLOCKED) ? "USER_BLOCKED" : "USER_UNBLOCKED";
        String description = String.format("Admin (ID: %d) set status of user %s (ID: %d) to %s",
                adminActorId, user.getEmail(), user.getId(), newStatus.name());

        auditService.logEvent(null, adminActorId, action, description);

        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getAccountStatus().name(),
                account != null ? account.getId() : null,
                account != null ? account.getAccountNumber() : "N/A",
                account != null ? account.getBalance() : BigDecimal.ZERO,
                account != null ? account.getAccountStatus().name() : "N/A",
                user.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getSuspiciousActivities() {
        return auditLogRepository.findAllByOrderByTimestampDesc().stream()
                .filter(log -> log.getAction().contains("FAILED")
                        || log.getAction().contains("BLOCKED")
                        || log.getAction().contains("DUPLICATE"))
                .collect(Collectors.toList());
    }
}
