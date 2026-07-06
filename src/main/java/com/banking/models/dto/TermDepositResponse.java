package com.banking.models.dto;

import com.banking.models.constant.TermDepositStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TermDepositResponse {
    private Long id;
    private String depositNumber;
    private Long customerId;
    private String customerName;
    private Long bankAccountId;
    private BigDecimal principalAmount;
    private BigDecimal annualInterestRate;
    private BigDecimal demandInterestRate;
    private Integer termMonths;
    private LocalDate openedDate;
    private LocalDate maturityDate;
    private LocalDate settlementDate;
    private Long actualDepositDays;
    private BigDecimal interestAppliedRate;
    private BigDecimal interestAmount;
    private BigDecimal settlementAmount;
    private Boolean earlySettlement;
    private TermDepositStatus status;
}
