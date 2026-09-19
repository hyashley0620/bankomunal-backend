package com.bankomunal.service;

import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public List<AccountResponse> getCuentasUsuario(Long userId) {
        return accountRepository.findByOwnerUserIdAndStatus(userId, Account.AccountStatus.active)
                .stream().map(a -> AccountResponse.builder()
                        .id(a.getId())
                        .numero(a.getAccountCode())
                        .tipo(a.getAccountType().name())
                        .saldo(a.getBalance())
                        .currency(a.getCurrency())
                        .status(a.getStatus().name())
                        .build())
                .toList();
    }

    /**
     * La cuenta única de capital de la entidad. Se crea la primera vez que
     * se necesita, con saldo en cero — el admin la capitaliza después desde
     * el panel de Sistema (ver SystemController#capitalizarFondo).
     */
    @Transactional
    public Account getCuentaFund() {
        return accountRepository.findFirstByAccountTypeAndOwnerUserIsNullAndOwnerGroupIsNull(Account.AccountType.fund)
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .accountCode("FUND-BANKOMUNAL")
                        .accountType(Account.AccountType.fund)
                        .balance(BigDecimal.ZERO)
                        .status(Account.AccountStatus.active)
                        .build()));
    }

    /**
     * La cuenta del fondo común de un grupo.
     */
    @Transactional
    public Account getCuentaGrupo(Group grupo) {
        return accountRepository.findFirstByOwnerGroupIdAndAccountType(grupo.getId(), Account.AccountType.group)
                .orElseGet(() -> {
                    BigDecimal saldoHeredado = grupo.getFondoComun() != null ? grupo.getFondoComun() : BigDecimal.ZERO;
                    return accountRepository.save(Account.builder()
                            .accountCode("FC-GRUPO-" + grupo.getId())
                            .accountType(Account.AccountType.group)
                            .ownerGroup(grupo)
                            .balance(saldoHeredado)
                            .status(Account.AccountStatus.active)
                            .build());
                });
    }
}
