package br.com.aloseguro.security;

import br.com.aloseguro.model.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Gera e valida os tokens JWT (assinatura HMAC-SHA256).
 */
@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMinutos;

    public JwtService(@Value("${aloseguro.jwt.secret}") String segredo,
                      @Value("${aloseguro.jwt.expiracao-minutos:120}") long expiracaoMinutos) {
        // Keys.hmacShaKeyFor exige pelo menos 32 bytes (256 bits)
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMinutos = expiracaoMinutos;
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("nome", usuario.getNome())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoMinutos, ChronoUnit.MINUTES)))
                .signWith(chave)
                .compact();
    }

    /**
     * Devolve o e-mail (subject) do token.
     * Lança JwtException se o token estiver adulterado, malformado ou expirado.
     */
    public String extrairEmail(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long getExpiracaoMinutos() {
        return expiracaoMinutos;
    }
}
