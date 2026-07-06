package com.banking.models.services;

import com.banking.exceptions.BusinessException;
import com.banking.models.constant.TermDepositStatus;
import com.banking.models.dto.SettleTermDepositRequest;
import com.banking.models.dto.TermDepositResponse;
import com.banking.models.entities.Customer;
import com.banking.models.entities.TermDeposit;
import com.banking.models.repositories.BankAccountRepository;
import com.banking.models.repositories.CustomerRepository;
import com.banking.models.repositories.TermDepositRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermDepositServiceTest {

    @Mock
    private TermDepositRepository termDepositRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @InjectMocks
    private TermDepositService termDepositService;

    @Test
    void settleTermDepositBeforeMaturityUsesDemandInterestRate() {
        TermDeposit termDeposit = activeTermDeposit(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 1));
        SettleTermDepositRequest request = new SettleTermDepositRequest();
        request.setSettlementDate(LocalDate.of(2026, 3, 1));

        when(termDepositRepository.findById(1L)).thenReturn(Optional.of(termDeposit));
        when(termDepositRepository.save(any(TermDeposit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermDepositResponse response = termDepositService.settleTermDeposit(1L, request);

        assertTrue(response.getEarlySettlement());
        assertEquals(new BigDecimal("0.001"), response.getInterestAppliedRate());
        assertEquals(new BigDecimal("16164.38"), response.getInterestAmount());
        assertEquals(TermDepositStatus.SETTLED, response.getStatus());
    }

    @Test
    void settleTermDepositOnMaturityUsesOriginalTermInterestRate() {
        TermDeposit termDeposit = activeTermDeposit(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 7, 1));
        SettleTermDepositRequest request = new SettleTermDepositRequest();
        request.setSettlementDate(LocalDate.of(2025, 7, 1));

        when(termDepositRepository.findById(1L)).thenReturn(Optional.of(termDeposit));
        when(termDepositRepository.save(any(TermDeposit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermDepositResponse response = termDepositService.settleTermDeposit(1L, request);

        assertFalse(response.getEarlySettlement());
        assertEquals(new BigDecimal("0.06"), response.getInterestAppliedRate());
        assertEquals(new BigDecimal("2975342.47"), response.getInterestAmount());
        assertEquals(TermDepositStatus.SETTLED, response.getStatus());
    }

    @Test
    void settleTermDepositTwiceThrowsBadRequestBusinessException() {
        TermDeposit termDeposit = activeTermDeposit(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 1));
        termDeposit.setStatus(TermDepositStatus.SETTLED);

        when(termDepositRepository.findById(1L)).thenReturn(Optional.of(termDeposit));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> termDepositService.settleTermDeposit(1L, new SettleTermDepositRequest()));

        assertEquals(400, exception.getCode());
        assertEquals("Sổ tiết kiệm đã được tất toán trước đó", exception.getMessage());
    }

    private TermDeposit activeTermDeposit(LocalDate openedDate, LocalDate maturityDate) {
        Customer customer = Customer.builder()
                .id(1L)
                .fullName("Nguyen Van A")
                .build();

        return TermDeposit.builder()
                .id(1L)
                .depositNumber("TD-TEST001")
                .principalAmount(new BigDecimal("100000000"))
                .annualInterestRate(new BigDecimal("0.06"))
                .demandInterestRate(new BigDecimal("0.001"))
                .termMonths(6)
                .openedDate(openedDate)
                .maturityDate(maturityDate)
                .status(TermDepositStatus.ACTIVE)
                .customer(customer)
                .build();
    }
}
