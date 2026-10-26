package br.com.aloseguro.config;

import br.com.aloseguro.model.CentralOficial;
import br.com.aloseguro.model.Denuncia;
import br.com.aloseguro.model.NumeroSuspeito;
import br.com.aloseguro.model.TipoAbordagem;
import br.com.aloseguro.model.Usuario;
import br.com.aloseguro.repository.CentralOficialRepository;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.NumeroSuspeitoRepository;
import br.com.aloseguro.repository.UsuarioRepository;
import br.com.aloseguro.util.NumeroTelefone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Popula o banco com dados FICTÍCIOS para a demonstração do projeto.
 * Só roda se o banco estiver vazio e se aloseguro.dados-demo=true.
 *
 * Números para testar a consulta:
 *   (11) 90000-0001  -> ALTO risco
 *   (21) 90000-0002  -> ATENÇÃO
 *   (11) 3000-0003   -> risco BAIXO
 *   0800 700 1000    -> CENTRAL OFICIAL (alerta de spoofing)
 *   (11) 91234-5678  -> SEM REGISTROS
 *
 * Login de demonstração: demo@aloseguro.com / demo123
 */
@Component
@ConditionalOnProperty(name = "aloseguro.dados-demo", havingValue = "true")
public class DadosDemonstracao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);
    private static final String SENHA_DEMO = "demo123";

    private final UsuarioRepository usuarios;
    private final NumeroSuspeitoRepository numeros;
    private final DenunciaRepository denuncias;
    private final CentralOficialRepository centrais;
    private final PasswordEncoder passwordEncoder;

    public DadosDemonstracao(UsuarioRepository usuarios, NumeroSuspeitoRepository numeros,
                             DenunciaRepository denuncias, CentralOficialRepository centrais,
                             PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.numeros = numeros;
        this.denuncias = denuncias;
        this.centrais = centrais;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0 || centrais.count() > 0) {
            return;
        }
        log.info("Banco vazio: inserindo dados de demonstração do AlôSeguro");

        // Bancos fictícios: substituir pelos números oficiais reais antes de publicar
        centrais.saveAll(List.of(
                new CentralOficial("Banco Aurora (fictício)", "08007001000", "SAC"),
                new CentralOficial("Banco Aurora (fictício)", "40041000", "Capitais e regiões metropolitanas"),
                new CentralOficial("Banco Horizonte (fictício)", "08007002000", "SAC"),
                new CentralOficial("Banco Horizonte (fictício)", "30032000", "Capitais e regiões metropolitanas")));

        String hash = passwordEncoder.encode(SENHA_DEMO);
        Usuario demo = usuarios.save(new Usuario("Usuário Demonstração", "demo@aloseguro.com", hash));
        Usuario maria = usuarios.save(new Usuario("Maria Souza", "maria@exemplo.com", hash));
        Usuario joao = usuarios.save(new Usuario("João Lima", "joao@exemplo.com", hash));
        Usuario ana = usuarios.save(new Usuario("Ana Costa", "ana@exemplo.com", hash));
        Usuario pedro = usuarios.save(new Usuario("Pedro Alves", "pedro@exemplo.com", hash));
        Usuario lucas = usuarios.save(new Usuario("Lucas Rocha", "lucas@exemplo.com", hash));

        // ALTO risco: 5 pessoas, 3 nas últimas 24h, celular, pediu senha e Pix
        NumeroSuspeito alto = numero("11900000001");
        denuncia(alto, maria, "Banco Aurora", TipoAbordagem.PEDIU_SENHA,
                "Disse que minha conta tinha sido invadida e pediu a senha do aplicativo para cancelar uma compra.", 2);
        denuncia(alto, joao, "Banco Aurora", TipoAbordagem.PEDIU_TRANSFERENCIA,
                "Pediu para fazer um Pix para uma conta segura enquanto a fraude era investigada.", 5);
        denuncia(alto, ana, "Banco Horizonte", TipoAbordagem.PEDIU_CODIGO,
                "Falou que ia mandar um código por SMS e pediu para eu ler em voz alta.", 20);
        denuncia(alto, pedro, "Banco Aurora", TipoAbordagem.PEDIU_SENHA,
                "Transferiu a ligação para um suposto setor de segurança, com música de espera.", 72);
        denuncia(alto, lucas, "Banco Aurora", TipoAbordagem.PEDIU_APLICATIVO,
                "Pediu para instalar um aplicativo de acesso remoto para proteger a conta.", 140);

        // ATENÇÃO: 2 pessoas, celular, um relato pediu transferência
        NumeroSuspeito atencao = numero("21900000002");
        denuncia(atencao, maria, "Banco Horizonte", TipoAbordagem.PEDIU_TRANSFERENCIA,
                "Informou uma compra suspeita e pediu transferência para estornar.", 50);
        denuncia(atencao, ana, "Banco Horizonte", TipoAbordagem.OUTRO,
                "Confirmou meu nome e CPF e desligou quando perguntei o número do protocolo.", 200);

        // BAIXO: 1 relato, telefone fixo
        NumeroSuspeito baixo = numero("1130000003");
        denuncia(baixo, joao, "Banco Aurora", TipoAbordagem.OUTRO,
                "Ligação dizendo ser do banco oferecendo renegociação. Achei estranho.", 300);

        // CENTRAL OFICIAL falsificada: número verdadeiro do banco usado em golpe (spoofing)
        NumeroSuspeito spoofing = numero("08007001000");
        denuncia(spoofing, pedro, "Banco Aurora", TipoAbordagem.PEDIU_SENHA,
                "Apareceu o 0800 do banco na tela, mas pediram a senha do cartão.", 10);
        denuncia(spoofing, demo, "Banco Aurora", TipoAbordagem.PEDIU_CARTAO,
                "Disseram que um motoboy iria buscar o cartão clonado.", 30);

        log.info("Dados de demonstração inseridos. Login: demo@aloseguro.com / {}", SENHA_DEMO);
    }

    private NumeroSuspeito numero(String digitos) {
        return numeros.save(new NumeroSuspeito(digitos, NumeroTelefone.ddd(digitos), NumeroTelefone.tipo(digitos)));
    }

    private void denuncia(NumeroSuspeito numero, Usuario usuario, String banco, TipoAbordagem tipo,
                          String descricao, long horasAtras) {
        Instant quando = Instant.now().minus(Duration.ofHours(horasAtras));
        Denuncia d = new Denuncia(numero, usuario, banco, tipo, descricao,
                LocalDateTime.ofInstant(quando, ZoneId.of("America/Sao_Paulo")));
        d.definirCriadoEm(quando);
        denuncias.save(d);
    }
}
