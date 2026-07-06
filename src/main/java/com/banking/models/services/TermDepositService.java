package com.banking.models.services;

import com.banking.exceptions.BusinessException;
import com.banking.models.constant.TermDepositStatus;
import com.banking.models.dto.OpenTermDepositRequest;
import com.banking.models.dto.SettleTermDepositRequest;
import com.banking.models.dto.TermDepositResponse;
import com.banking.models.entities.BankAccount;
import com.banking.models.entities.Customer;
import com.banking.models.entities.TermDeposit;
import com.banking.models.repositories.BankAccountRepository;
import com.banking.models.repositories.CustomerRepository;
import com.banking.models.repositories.TermDepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TermDepositService {

    private static final BigDecimal DEFAULT_DEMAND_INTEREST_RATE = new BigDecimal("0.001");
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final Set<Integer> SUPPORTED_TERMS = Set.of(1, 6, 12);

    private final TermDepositRepository termDepositRepository;
    private final CustomerRepository customerRepository;
    private final BankAccountRepository bankAccountRepository;

    @Transactional
    public TermDepositResponse openTermDeposit(OpenTermDepositRequest request) {
        validateTermMonths(request.getTermMonths());

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST.value(), "Không tìm thấy khách hàng"));

        BankAccount bankAccount = null;
        if (request.getBankAccountId() != null) {
            bankAccount = getValidBankAccount(request.getBankAccountId(), customer.getId());
            if (bankAccount.getBalance().compareTo(request.getPrincipalAmount()) < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Số dư tài khoản không đủ để mở sổ tiết kiệm");
            }
            bankAccount.setBalance(bankAccount.getBalance().subtract(request.getPrincipalAmount()));
            bankAccountRepository.save(bankAccount);
        }

        LocalDate openedDate = request.getOpenedDate() != null ? request.getOpenedDate() : LocalDate.now();
        LocalDate maturityDate = openedDate.plusMonths(request.getTermMonths());

        TermDeposit termDeposit = TermDeposit.builder()
                .depositNumber(generateDepositNumber())
                .principalAmount(request.getPrincipalAmount())
                .annualInterestRate(request.getAnnualInterestRate())
                .demandInterestRate(DEFAULT_DEMAND_INTEREST_RATE)
                .termMonths(request.getTermMonths())
                .openedDate(openedDate)
                .maturityDate(maturityDate)
                .interestAmount(BigDecimal.ZERO)
                .settlementAmount(BigDecimal.ZERO)
                .status(TermDepositStatus.ACTIVE)
                .customer(customer)
                .bankAccount(bankAccount)
                .build();

        return toResponse(termDepositRepository.save(termDeposit));
    }

    @Transactional
    public TermDepositResponse settleTermDeposit(Long id, SettleTermDepositRequest request) {
        TermDeposit termDeposit = termDepositRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST.value(), "Không tìm thấy sổ tiết kiệm"));

        if (TermDepositStatus.SETTLED.equals(termDeposit.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Sổ tiết kiệm đã được tất toán trước đó");
        }

        LocalDate settlementDate = request != null && request.getSettlementDate() != null
                ? request.getSettlementDate()
                : LocalDate.now();

        long actualDays = ChronoUnit.DAYS.between(termDeposit.getOpenedDate(), settlementDate);
        if (actualDays < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Ngày tất toán không được trước ngày gửi");
        }

        boolean earlySettlement = settlementDate.isBefore(termDeposit.getMaturityDate());
        BigDecimal appliedRate = earlySettlement
                ? termDeposit.getDemandInterestRate()
                : termDeposit.getAnnualInterestRate();
        BigDecimal interestAmount = calculateInterest(termDeposit.getPrincipalAmount(), appliedRate, actualDays);
        BigDecimal settlementAmount = termDeposit.getPrincipalAmount().add(interestAmount).setScale(2, RoundingMode.HALF_UP);

        termDeposit.setSettlementDate(settlementDate);
        termDeposit.setActualDepositDays(actualDays);
        termDeposit.setEarlySettlement(earlySettlement);
        termDeposit.setInterestAppliedRate(appliedRate);
        termDeposit.setInterestAmount(interestAmount);
        termDeposit.setSettlementAmount(settlementAmount);
        termDeposit.setStatus(TermDepositStatus.SETTLED);

        BankAccount bankAccount = termDeposit.getBankAccount();
        if (bankAccount != null) {
            bankAccount.setBalance(bankAccount.getBalance().add(settlementAmount));
            bankAccountRepository.save(bankAccount);
        }

        return toResponse(termDepositRepository.save(termDeposit));
    }

    @Transactional(readOnly = true)
    public TermDepositResponse getTermDeposit(Long id) {
        TermDeposit termDeposit = termDepositRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST.value(), "Không tìm thấy sổ tiết kiệm"));
        return toResponse(termDeposit);
    }

    @Transactional(readOnly = true)
    public List<TermDepositResponse> getTermDepositsByCustomer(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Không tìm thấy khách hàng");
        }
        return termDepositRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private BigDecimal calculateInterest(BigDecimal principalAmount, BigDecimal annualRate, long actualDays) {
        if (actualDays <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return principalAmount
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(actualDays))
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    private BankAccount getValidBankAccount(Long bankAccountId, Long customerId) {
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST.value(), "Không tìm thấy tài khoản thanh toán"));

        if (!bankAccount.getCustomer().getId().equals(customerId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Tài khoản thanh toán không thuộc khách hàng này");
        }
        if (!BankAccount.AccountStatus.ACTIVE.equals(bankAccount.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Tài khoản thanh toán không ở trạng thái hoạt động");
        }
        return bankAccount;
    }

    private void validateTermMonths(Integer termMonths) {
        if (termMonths == null || !SUPPORTED_TERMS.contains(termMonths)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), "Kỳ hạn chỉ hỗ trợ 1 tháng, 6 tháng hoặc 12 tháng");
        }
    }

    private String generateDepositNumber() {
        String depositNumber;
        do {
            depositNumber = "TD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (termDepositRepository.existsByDepositNumber(depositNumber));
        return depositNumber;
    }

    private TermDepositResponse toResponse(TermDeposit termDeposit) {
        BankAccount bankAccount = termDeposit.getBankAccount();
        return TermDepositResponse.builder()
                .id(termDeposit.getId())
                .depositNumber(termDeposit.getDepositNumber())
                .customerId(termDeposit.getCustomer().getId())
                .customerName(termDeposit.getCustomer().getFullName())
                .bankAccountId(bankAccount != null ? bankAccount.getId() : null)
                .principalAmount(termDeposit.getPrincipalAmount())
                .annualInterestRate(termDeposit.getAnnualInterestRate())
                .demandInterestRate(termDeposit.getDemandInterestRate())
                .termMonths(termDeposit.getTermMonths())
                .openedDate(termDeposit.getOpenedDate())
                .maturityDate(termDeposit.getMaturityDate())
                .settlementDate(termDeposit.getSettlementDate())
                .actualDepositDays(termDeposit.getActualDepositDays())
                .interestAppliedRate(termDeposit.getInterestAppliedRate())
                .interestAmount(termDeposit.getInterestAmount())
                .settlementAmount(termDeposit.getSettlementAmount())
                .earlySettlement(termDeposit.getEarlySettlement())
                .status(termDeposit.getStatus())
                .build();
    }
}
