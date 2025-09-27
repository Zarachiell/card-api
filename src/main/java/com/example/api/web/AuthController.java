package com.example.api.web;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping("/validate")
    public Map<String, Object> validate(JwtAuthenticationToken auth) {
        // Se chegou aqui, o JWT já foi validado (assinatura + exp + issuer)
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authenticated", auth.isAuthenticated());
        out.put("tokenType", "JWT");

        var jwt = auth.getToken();
        out.put("principal", jwt.getSubject()); // 'sub'
        out.put("clientId", jwt.getClaimAsString("azp")); // authorized party (Keycloak)

        out.put("expiresAt", Optional.ofNullable(jwt.getExpiresAt()).map(Instant::toString).orElse(null));

        // Autoridades (scopes => SCOPE_xxx + roles => ROLE_xxx se você usou o converter)
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        out.put("authorities", authorities);

        // (Opcional) expor algumas claims úteis sem vazar tudo
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
        claims.put("scope", jwt.getClaimAsString("scope"));
        claims.put("realm_access", jwt.getClaim("realm_access"));
        claims.put("resource_access", jwt.getClaim("resource_access"));
        out.put("claims", claims);

        return out;
    }
}
