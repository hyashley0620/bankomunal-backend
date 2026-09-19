package com.bankomunal.controller;

import com.bankomunal.dto.response.FrequentContactResponse;
import com.bankomunal.entity.User;
import com.bankomunal.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contactos")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<List<FrequentContactResponse>> misContactos(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(contactService.getMisContactos(user.getId()));
    }

    @PostMapping
    public ResponseEntity<FrequentContactResponse> agregar(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(contactService.agregarContacto(user.getId(), body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        contactService.eliminarContacto(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}