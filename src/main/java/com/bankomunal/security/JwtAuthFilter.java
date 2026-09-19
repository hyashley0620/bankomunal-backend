package com.bankomunal.security;

import com.bankomunal.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        String token = authHeader.substring(7);

        // Token mal formado o expirado
        if (!jwtUtil.isValid(token)) {
            sendUnauthorized(res, "Token inválido o expirado. Inicia sesión de nuevo.");
            return;
        }

        String email = jwtUtil.extractEmail(token);
        var userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            sendUnauthorized(res, "Usuario no encontrado.");
            return;
        }

        var user = userOpt.get();

        // Sesión única — si hay un token activo y no coincide, rechazar con 401
        if (user.getActiveToken() != null && !token.equals(user.getActiveToken())) {
            sendUnauthorized(res,
                    "Sesión inválida. Se detectó otro inicio de sesión activo. " +
                            "Por favor inicia sesión de nuevo.");
            return;
        }

        // Autenticar
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
                .toList();

        var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(req, res);
    }

    private void sendUnauthorized(HttpServletResponse res, String mensaje) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getWriter(),
                Map.of("error", "No autorizado", "mensaje", mensaje));
    }
}
