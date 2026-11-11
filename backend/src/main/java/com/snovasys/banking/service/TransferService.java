package com.snovasys.banking.service;

import com.snovasys.banking.dto.TransferRequest;
import com.snovasys.banking.dto.TransferResponse;
import com.snovasys.banking.entity.Account;
import com.snovasys.banking.entity.Transaction;
import com.snovasys.banking.entity.TransactionStatus;
import com.snovasys.banking.entity.User;
import com.snovasys.banking.exception.*;
import com.snovasys.banking.repository.AccountRepository;
import com.snovasys.banking.repository.TransactionRepository;
import com.snovasys.banking.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SecurityUtils securityUtils;
    private final AuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           SecurityUtils securityUtils,
                           AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.securityUtils = securityUtils;
        this.auditService = auditService;
    }

    /**
     * Executes an ACID-compliant, secure money transfer.
     *
     * KEY SECURITY FEATURES:
     * 1. @Transactional ensures all debit and credit operations succeed together or all roll back.
     * 2. Sender account is resolved strictly from the authenticated security context (anti-IDOR & anti-spoofing).
     * 3. Idempotency key checking prevents duplicate or replayed submissions.
     * 4. Pessimistic write locking prevents race conditions and concurrent double-spending.
     * 5. Deterministic ordering of locks (min account ID, then max account ID) completely eliminates deadlocks.
     * 6. EntityManager.refresh() ensures the latest committed balance is loaded from the locked row,
     *    avoiding stale first-level cache reads across concurrent transactions.
     * 7. Strict validation: amount > 0, accounts active, sender != receiver, balance >= amount.
     * 8. Audit log generated inside the transaction boundary.
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TransferResponse transferMoney(TransferRequest request) {
        // 1. Identify logged-in user from Security Context
        User senderUser = securityUtils.getCurrentUser();
        Long actorId = senderUser.getId();

        // 2. Validate amount > 0
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            auditService.logEvent(null, actorId, "TRANSFER_FAILED", "Attempted transfer with non-positive amount: " + amount);
            throw new InvalidAmountException("Transfer amount must be strictly greater than zero");
        }
        amount = amount.setScale(4, RoundingMode.HALF_UP);

        // 3. Prevent duplicate requests using idempotency key
        String idempotencyKey = request.getIdempotencyKey().trim();
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            auditService.logEvent(null, actorId, "TRANSFER_DUPLICATE_ATTEMPT",
                    "Duplicate transfer attempt with idempotency key: " + idempotencyKey);
            throw new DuplicateTransactionException("A transfer with this idempotency key has already been processed: " + idempotencyKey);
        }

        // 4. Resolve sender account directly from authenticated user
        Account senderAccount = accountRepository.findByUserId(senderUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for authenticated user"));

        if (!senderAccount.isActive()) {
            auditService.logEvent(null, actorId, "TRANSFER_FAILED", "Blocked sender attempted transfer: " + senderAccount.getAccountNumber());
            throw new AccountInactiveException("Your account is currently blocked or inactive. Transfers are disabled.");
        }

        // 5. Resolve receiver account by account number
        String receiverAccountNumber = request.getReceiverAccountNumber().trim();
        Account receiverAccount = accountRepository.findByAccountNumber(receiverAccountNumber)
                .orElseThrow(() -> {
                    auditService.logEvent(null, actorId, "TRANSFER_FAILED", "Receiver account not found: " + receiverAccountNumber);
                    return new ResourceNotFoundException("Receiver account not found with account number: " + receiverAccountNumber);
                });

        // 6. Prevent self-transfer
        if (senderAccount.getId().equals(receiverAccount.getId())) {
            auditService.logEvent(null, actorId, "TRANSFER_FAILED", "Self-transfer attempted on account: " + senderAccount.getAccountNumber());
            throw new SelfTransferException("Self-transfers are not allowed. Sender and receiver accounts must be different.");
        }

        // 7. Verify receiver account status
        if (!receiverAccount.isActive()) {
            auditService.logEvent(null, actorId, "TRANSFER_FAILED", "Transfer attempted to blocked receiver: " + receiverAccountNumber);
            throw new AccountInactiveException("The receiver account is currently inactive or blocked.");
        }

        // 8. Concurrency Control: Acquire PESSIMISTIC_WRITE locks in deterministic order
        Long senderId = senderAccount.getId();
        Long receiverId = receiverAccount.getId();
        Long firstLockId = Math.min(senderId, receiverId);
        Long secondLockId = Math.max(senderId, receiverId);

        Account firstLocked = accountRepository.findByIdWithLock(firstLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for locking: " + firstLockId));
        Account secondLocked = accountRepository.findByIdWithLock(secondLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for locking: " + secondLockId));

        // Refresh entity from database to ensure fresh balance under lock
        entityManager.refresh(firstLocked);
        entityManager.refresh(secondLocked);

        Account lockedSender = senderId.equals(firstLockId) ? firstLocked : secondLocked;
        Account lockedReceiver = receiverId.equals(firstLockId) ? firstLocked : secondLocked;

        // 9. Check balance on locked sender account
        if (lockedSender.getBalance().compareTo(amount) < 0) {
            auditService.logEvent(null, actorId, "TRANSFER_FAILED",
                    String.format("Insufficient funds. Available: %s, Requested: %s", lockedSender.getBalance(), amount));
            throw new InsufficientBalanceException(String.format("Insufficient balance. Your available balance is %s",
                    lockedSender.getBalance().setScale(2, RoundingMode.HALF_UP)));
        }

        // 10. Atomically debit sender and credit receiver
        lockedSender.setBalance(lockedSender.getBalance().subtract(amount));
        lockedReceiver.setBalance(lockedReceiver.getBalance().add(amount));

        accountRepository.save(lockedSender);
        accountRepository.save(lockedReceiver);

        // 11. Record transaction record
        Transaction transaction = new Transaction(
                lockedSender,
                lockedReceiver,
                amount,
                TransactionStatus.SUCCESS,
                idempotencyKey
        );
        Transaction savedTx = transactionRepository.save(transaction);

        // 12. Create immutable audit log entry
        auditService.logEvent(
                savedTx.getId(),
                actorId,
                "TRANSFER_SUCCESS",
                String.format("Transferred %s from account %s to account %s",
                        amount.setScale(2, RoundingMode.HALF_UP),
                        lockedSender.getAccountNumber(),
                        lockedReceiver.getAccountNumber())
        );

        log.info("TRANSFER SUCCESS: TxID={}, Sender={}, Receiver={}, Amount={}",
                savedTx.getId(), lockedSender.getAccountNumber(), lockedReceiver.getAccountNumber(), amount);

        return new TransferResponse(
                savedTx.getId(),
                lockedSender.getAccountNumber(),
                lockedReceiver.getAccountNumber(),
                lockedReceiver.getUser().getName(),
                amount.setScale(2, RoundingMode.HALF_UP),
                TransactionStatus.SUCCESS.name(),
                idempotencyKey,
                savedTx.getCreatedAt(),
                "Money transferred successfully"
        );
    }
}
