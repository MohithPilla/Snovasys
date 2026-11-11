package com.snovasys.banking.controller;

import com.snovasys.banking.dto.AdminStatusUpdateRequest;
import com.snovasys.banking.dto.AdminUserResponse;
import com.snovasys.banking.entity.AuditLog;
import com.snovasys.banking.entity.Transaction;
import com.snovasys.banking.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<AdminUserResponse> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody AdminStatusUpdateRequest request) {
        AdminUserResponse response = adminService.updateUserStatus(userId, request.getStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(adminService.getAllTransactions());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAllAuditLogs() {
        return ResponseEntity.ok(adminService.getAllAuditLogs());
    }

    @GetMapping("/suspicious-activities")
    public ResponseEntity<List<AuditLog>> getSuspiciousActivities() {
        return ResponseEntity.ok(adminService.getSuspiciousActivities());
    }
}
