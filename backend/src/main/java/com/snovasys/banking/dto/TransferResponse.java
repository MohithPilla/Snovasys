package com.snovasys.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransferResponse {

    private Long transactionId;
    private String senderAccountNumber;
    private String receiverAccountNumber;
    private String receiverName;
    private BigDecimal amount;
    private String status;
    private String idempotencyKey;
    private LocalDateTime timestamp;
    private String message;

    public TransferResponse() {
    }

    public TransferResponse(Long transactionId, String senderAccountNumber, String receiverAccountNumber,
                            String receiverName, BigDecimal amount, String status, String idempotencyKey,
                            LocalDateTime timestamp, String message) {
        this.transactionId = transactionId;
        this.senderAccountNumber = senderAccountNumber;
        this.receiverAccountNumber = receiverAccountNumber;
        this.receiverName = receiverName;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.timestamp = timestamp;
        this.message = message;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getSenderAccountNumber() {
        return senderAccountNumber;
    }

    public void setSenderAccountNumber(String senderAccountNumber) {
        this.senderAccountNumber = senderAccountNumber;
    }

    public String getReceiverAccountNumber() {
        return receiverAccountNumber;
    }

    public void setReceiverAccountNumber(String receiverAccountNumber) {
        this.receiverAccountNumber = receiverAccountNumber;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
