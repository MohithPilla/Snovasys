package com.snovasys.banking.controller;

import com.snovasys.banking.dto.UserProfileResponse;
import com.snovasys.banking.entity.Transaction;
import com.snovasys.banking.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, BigDecimal>> getBalance() {
        BigDecimal balance = userService.getCurrentUserBalance();
        return ResponseEntity.ok(Collections.singletonMap("balance", balance));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions() {
        return ResponseEntity.ok(userService.getCurrentUserTransactions());
    }

    @GetMapping("/lookup/{accountNumber}")
    public ResponseEntity<Map<String, Object>> lookupAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(userService.lookupAccount(accountNumber));
    }
}
