package com.banking.controllers;

import com.banking.advice.ApiResponse;
import com.banking.models.dto.OpenTermDepositRequest;
import com.banking.models.dto.SettleTermDepositRequest;
import com.banking.models.dto.TermDepositResponse;
import com.banking.models.services.TermDepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/term-deposits")
@RequiredArgsConstructor
public class TermDepositController {

    private final TermDepositService termDepositService;

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<TermDepositResponse>> openTermDeposit(
            @Valid @RequestBody OpenTermDepositRequest request) {
        TermDepositResponse data = termDepositService.openTermDeposit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Mở sổ tiết kiệm thành công"));
    }

    @PostMapping("/{id}/settle")
    public ResponseEntity<ApiResponse<TermDepositResponse>> settleTermDeposit(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) SettleTermDepositRequest request) {
        TermDepositResponse data = termDepositService.settleTermDeposit(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Tất toán sổ tiết kiệm thành công"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TermDepositResponse>> getTermDeposit(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(termDepositService.getTermDeposit(id),
                "Lấy thông tin sổ tiết kiệm thành công"));
    }

    @GetMapping("/customers/{customerId}")
    public ResponseEntity<ApiResponse<List<TermDepositResponse>>> getTermDepositsByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success(termDepositService.getTermDepositsByCustomer(customerId),
                "Lấy danh sách sổ tiết kiệm của khách hàng thành công"));
    }
}
