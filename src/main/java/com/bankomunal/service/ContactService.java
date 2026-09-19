package com.bankomunal.service;

import com.bankomunal.dto.response.FrequentContactResponse;
import com.bankomunal.entity.Account;
import com.bankomunal.entity.FrequentContact;
import com.bankomunal.entity.User;
import com.bankomunal.repository.AccountRepository;
import com.bankomunal.repository.FrequentContactRepository;
import com.bankomunal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final FrequentContactRepository contactRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public List<FrequentContactResponse> getMisContactos(Long ownerUserId) {
        return contactRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId).stream()
                .map(c -> FrequentContactResponse.builder()
                        .id(c.getId())
                        .nombre(c.getNombre())
                        .email(c.getEmail())
                        .cuentaNumero(c.getCuentaNumero())
                        .usuarioRegistrado(c.getCuentaNumero() != null)
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * Agrega un contacto frecuente.
     */
    @Transactional
    public FrequentContactResponse agregarContacto(Long ownerUserId, Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        if (email.isEmpty())
            throw new IllegalArgumentException("El email del contacto es requerido.");

        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (email.equalsIgnoreCase(owner.getEmail()))
            throw new IllegalArgumentException("No puedes agregarte a ti mismo como contacto.");

        // Evitar duplicados: si ya existe, simplemente lo devolvemos sin error
        var existente = contactRepository.findByOwnerUserIdAndEmail(ownerUserId, email);
        if (existente.isPresent()) {
            FrequentContact c = existente.get();
            return FrequentContactResponse.builder()
                    .id(c.getId()).nombre(c.getNombre()).email(c.getEmail())
                    .cuentaNumero(c.getCuentaNumero()).usuarioRegistrado(c.getCuentaNumero() != null)
                    .createdAt(c.getCreatedAt())
                    .build();
        }

        var usuarioOpt = userRepository.findByEmail(email);

        // Resolver nombre real si el email corresponde a un usuario de la plataforma
        String nombreResuelto = usuarioOpt
                .map(u -> (u.getFirstName() != null ? u.getFirstName() : "") +
                        (u.getLastName() != null ? " " + u.getLastName() : ""))
                .map(String::trim)
                .filter(n -> !n.isEmpty())
                .orElse(email);

        // Número de cuenta: siempre resuelto desde el registro real del socio, nunca del body.
        String cuentaResuelta = usuarioOpt
                .flatMap(u -> accountRepository.findFirstByOwnerUserIdAndAccountTypeAndStatus(
                        u.getId(), Account.AccountType.individual, Account.AccountStatus.active))
                .map(Account::getAccountCode)
                .orElse(null);

        FrequentContact contact = FrequentContact.builder()
                .ownerUser(owner)
                .nombre(nombreResuelto)
                .email(email)
                .cuentaNumero(cuentaResuelta)
                .build();
        contactRepository.save(contact);

        return FrequentContactResponse.builder()
                .id(contact.getId()).nombre(contact.getNombre()).email(contact.getEmail())
                .cuentaNumero(contact.getCuentaNumero()).usuarioRegistrado(contact.getCuentaNumero() != null)
                .createdAt(contact.getCreatedAt())
                .build();
    }

    @Transactional
    public void eliminarContacto(Long ownerUserId, Long contactId) {
        contactRepository.deleteByIdAndOwnerUserId(contactId, ownerUserId);
    }
}
