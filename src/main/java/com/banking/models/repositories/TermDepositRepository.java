package com.banking.models.repositories;

import com.banking.models.entities.TermDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermDepositRepository extends JpaRepository<TermDeposit, Long> {
    Optional<TermDeposit> findByDepositNumber(String depositNumber);

    boolean existsByDepositNumber(String depositNumber);

    List<TermDeposit> findByCustomerId(Long customerId);
}
