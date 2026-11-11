package com.snovasys.banking.repository;

import com.snovasys.banking.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByTimestampDesc();

    List<AuditLog> findByActorIdOrderByTimestampDesc(Long actorId);

    List<AuditLog> findByTransactionIdOrderByTimestampDesc(Long transactionId);
}
