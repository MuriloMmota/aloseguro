package br.com.aloseguro.service;

import br.com.aloseguro.dto.AuthDtos.CadastroRequest;
import br.com.aloseguro.dto.AuthDtos.LoginRequest;
import br.com.aloseguro.dto.AuthDtos.TokenResponse;
import br.com.aloseguro.exception.NegocioException;
import br.com.aloseguro.model.Usuario;
import br.com.aloseguro.repository.UsuarioRepository;
import br.com.aloseguro.security.JwtService;
import br.com.aloseguro.security.LimiteTentativasLogin;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LimiteTentativasLogin limite;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LimiteTentativasLogin limite) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limite = limite;
    }

    @Transactional
    public TokenResponse cadastrar(CadastroRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado.");
        }

        // A senha nunca é salva em texto puro: só o hash BCrypt
        Usuario usuario = usuarios.save(new Usuario(req.nome().trim(), email, passwordEncoder.encode(req.senha())));
        return token(usuario);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        String email = req.email().trim();

        long bloqueado = limite.segundosBloqueado(email);
        if (bloqueado > 0) {
            throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas com senha errada. Tente de novo em " + Math.max(1, bloqueado / 60) + " minuto(s).");
        }

        Optional<Usuario> usuario = usuarios.findByEmailIgnoreCase(email)
                .filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()));
        if (usuario.isEmpty()) {
            limite.registrarFalha(email);
            throw new BadCredentialsException("E-mail ou senha inválidos.");
        }
        limite.registrarSucesso(email);
        return token(usuario.get());
    }

    private TokenResponse token(Usuario usuario) {
        return new TokenResponse(
                jwtService.gerarToken(usuario),
                "Bearer",
                jwtService.getExpiracaoMinutos(),
                usuario.getNome(),
                usuario.getEmail());
    }
}
