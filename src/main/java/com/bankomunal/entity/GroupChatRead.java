package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Marca de "hasta qué momento" un usuario leyó el chat de un grupo. El chat
 * directo (1 a 1) usa el campo {@code leido} de ChatMessage, que sí tiene
 * sentido ahí porque hay un solo destinatario — pero un mensaje de grupo
 * tiene N destinatarios, así que necesita su propio registro de lectura
 * por (grupo, usuario) en vez de un booleano en el mensaje.
 */
@Entity
@Table(name = "group_chat_reads", uniqueConstraints = @UniqueConstraint(columnNames = { "group_id", "user_id" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChatRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;
}
