package com.banking.models.entities;

import com.banking.models.constant.TermDepositStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "term_deposits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deposit_number", unique = true, nullable = false, length = 50)
    private String depositNumber;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "annual_interest_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal annualInterestRate;

    @Column(name = "demand_interest_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal demandInterestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "actual_deposit_days")
    private Long actualDepositDays;

    @Column(name = "interest_applied_rate", precision = 10, scale = 6)
    private BigDecimal interestAppliedRate;

    @Column(name = "interest_amount", precision = 19, scale = 4)
    private BigDecimal interestAmount;

    @Column(name = "settlement_amount", precision = 19, scale = 4)
    private BigDecimal settlementAmount;

    @Column(name = "early_settlement")
    private Boolean earlySettlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TermDepositStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private BankAccount bankAccount;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
