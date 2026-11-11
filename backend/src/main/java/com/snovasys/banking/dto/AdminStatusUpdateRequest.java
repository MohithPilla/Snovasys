package com.snovasys.banking.dto;

import com.snovasys.banking.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public class AdminStatusUpdateRequest {

    @NotNull(message = "Account status is required (ACTIVE or BLOCKED)")
    private AccountStatus status;

    public AdminStatusUpdateRequest() {
    }

    public AdminStatusUpdateRequest(AccountStatus status) {
        this.status = status;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
