package com.banking.models.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OpenTermDepositRequest {

    @NotNull(message = "Mã khách hàng không được để trống")
    private Long customerId;

    private Long bankAccountId;

    @NotNull(message = "Tiền gốc không được để trống")
    @DecimalMin(value = "0.01", message = "Tiền gốc phải lớn hơn 0")
    private BigDecimal principalAmount;

    @NotNull(message = "Lãi suất có kỳ hạn không được để trống")
    @DecimalMin(value = "0.0001", message = "Lãi suất phải lớn hơn 0")
    @DecimalMax(value = "1.0", message = "Lãi suất nhập theo dạng thập phân, ví dụ 0.06 cho 6%/năm")
    private BigDecimal annualInterestRate;

    @NotNull(message = "Kỳ hạn không được để trống")
    private Integer termMonths;

    @PastOrPresent(message = "Ngày gửi không được lớn hơn ngày hiện tại")
    private LocalDate openedDate;
}
