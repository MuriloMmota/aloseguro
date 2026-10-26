package br.com.aloseguro.service;

import br.com.aloseguro.dto.AuthDtos.CadastroRequest;
import br.com.aloseguro.dto.AuthDtos.LoginRequest;
import br.com.aloseguro.dto.AuthDtos.TokenResponse;
import br.com.aloseguro.exception.NegocioException;
import br.com.aloseguro.model.Usuario;
import br.com.aloseguro.repository.UsuarioRepository;
import br.com.aloseguro.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        Usuario usuario = usuarios.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()))
                .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos."));
        return token(usuario);
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
