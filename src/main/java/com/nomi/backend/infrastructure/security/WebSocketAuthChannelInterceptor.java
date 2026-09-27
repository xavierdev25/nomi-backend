package com.nomi.backend.infrastructure.security;

import com.nomi.backend.domain.port.out.TokenBlacklistPort;
import com.nomi.backend.domain.port.out.TokenServicePort;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Autentica las conexiones STOMP con el JWT de la cabecera {@code Authorization} del
 * {@code CONNECT}.
 *
 * <p>Solo valida el {@code CONNECT}: las suscripciones ({@code SUBSCRIBE}) no se autorizan por
 * recurso (ver la auditoría técnica, A1).
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final TokenServicePort tokenServicePort;
    private final TokenBlacklistPort tokenBlacklistPort;

    public WebSocketAuthChannelInterceptor(TokenServicePort tokenServicePort,
                                           TokenBlacklistPort tokenBlacklistPort) {
        this.tokenServicePort = tokenServicePort;
        this.tokenBlacklistPort = tokenBlacklistPort;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = firstHeader(accessor, "Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new SecurityException("WebSocket requiere token Bearer en el handshake");
            }
            String token = authHeader.substring(7);
            if (!tokenServicePort.isTokenValid(token) || tokenBlacklistPort.isBlacklisted(token)) {
                throw new SecurityException("Token inválido o revocado");
            }
            String email = tokenServicePort.extractEmail(token);
            String role = tokenServicePort.extractRole(token);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            accessor.setUser(auth);
        }
        return message;
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
