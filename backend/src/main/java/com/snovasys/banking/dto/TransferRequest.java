package com.snovasys.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class TransferRequest {

    @NotBlank(message = "Receiver account number is required")
    private String receiverAccountNumber;

    @NotNull(message = "Transfer amount is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Transfer amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required to prevent duplicate transfers")
    private String idempotencyKey;

    public TransferRequest() {
    }

    public TransferRequest(String receiverAccountNumber, BigDecimal amount, String idempotencyKey) {
        this.receiverAccountNumber = receiverAccountNumber;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public String getReceiverAccountNumber() {
        return receiverAccountNumber;
    }

    public void setReceiverAccountNumber(String receiverAccountNumber) {
        this.receiverAccountNumber = receiverAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
