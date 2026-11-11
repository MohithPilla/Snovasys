package com.snovasys.banking.service;

import com.snovasys.banking.entity.AuditLog;
import com.snovasys.banking.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Appends an immutable audit log entry.
     * Uses REQUIRES_NEW propagation where appropriate to ensure audit records are persisted
     * even if subsequent operations roll back or when recording failed attempts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logEvent(Long transactionId, Long actorId, String action, String description) {
        log.info("AUDIT EVENT: [Actor: {}, Action: {}, Tx: {}, Desc: {}]",
                actorId, action, transactionId, description);

        AuditLog auditLog = new AuditLog(transactionId, actorId, action, description);
        return auditLogRepository.save(auditLog);
    }

    /**
     * Read-only view for system administrators.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsForActor(Long actorId) {
        return auditLogRepository.findByActorIdOrderByTimestampDesc(actorId);
    }
}
