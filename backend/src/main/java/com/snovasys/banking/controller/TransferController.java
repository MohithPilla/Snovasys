package com.snovasys.banking.controller;

import com.snovasys.banking.dto.TransferRequest;
import com.snovasys.banking.dto.TransferResponse;
import com.snovasys.banking.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> executeTransfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.transferMoney(request);
        return ResponseEntity.ok(response);
    }
}
