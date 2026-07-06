package com.banking.models.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SettleTermDepositRequest {

    @PastOrPresent(message = "Ngày tất toán không được lớn hơn ngày hiện tại")
    private LocalDate settlementDate;
}
