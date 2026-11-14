package br.com.aloseguro.config;

import br.com.aloseguro.model.TagRelato;
import br.com.aloseguro.model.TipoAbordagem;
import br.com.aloseguro.model.TipoLinha;
import br.com.aloseguro.util.NumeroTelefone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Carga de dados FICTÍCIOS para testar o motor de risco com volume (padrão: 1000 números).
 *
 * Tudo é gerado a partir de uma semente fixa (Random(2026)): rodar duas vezes gera exatamente
 * os mesmos dados, o que permite repetir testes e demonstrações.
 *
 * Os números e bancos são inventados. Os números de exemplo usam sequências como 90000-0001
 * para deixar claro que não são de ninguém.
 *
 * A inserção usa JDBC em lote (batch), e não o JPA linha a linha, para carregar milhares de
 * registros em poucos segundos mesmo com o banco na nuvem.
 *
 * Só roda se o banco estiver vazio. Para apagar tudo e gerar de novo: DADOS_DEMO_RECRIAR=true.
 */
@Component
@ConditionalOnProperty(name = "aloseguro.demo.ativo", havingValue = "true")
public class CargaDadosFicticios implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaDadosFicticios.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    public static final String SENHA_DEMO = "demo123";
    public static final String EMAIL_DEMO = "demo@aloseguro.com";

    /** Números usados nos exemplos do site e do roteiro de apresentação. */
    public static final String EX_ALTO = "11900000001";
    public static final String EX_ATENCAO = "21900000002";
    public static final String EX_BAIXO = "1130000003";
    public static final String EX_CENTRAL = "08007001000";
    public static final String EX_SEM_REGISTRO = "11912345678";
    public static final String EX_BLOCO_SUSPEITO = "11977709999";

    private static final List<String> BANCOS =
            List.of("Banco Aurora", "Banco Horizonte", "Banco Atlântico", "Banco Prisma", "Banco Vértice");

    /** Blocos de numeração "usados por golpistas" (DDD + 5 dígitos). */
    private static final List<String> BLOCOS =
            List.of("1190000", "1197770", "2198880", "3199990", "4198123", "7199456", "8198765", "5197001");

    private static final List<String> DDDS =
            List.of("11", "11", "11", "21", "21", "31", "41", "51", "61", "71", "81", "85", "19", "27", "48", "62", "92");

    private static final List<String> NOMES = List.of("Ana", "Bruno", "Carla", "Diego", "Eduarda", "Felipe", "Gabriela",
            "Henrique", "Isabela", "João", "Larissa", "Marcos", "Natália", "Otávio", "Paula", "Rafaela", "Samuel",
            "Tatiane", "Vinícius", "Yasmin");
    private static final List<String> SOBRENOMES = List.of("Silva", "Souza", "Oliveira", "Santos", "Lima", "Costa",
            "Pereira", "Almeida", "Ferreira", "Rocha", "Barbosa", "Ribeiro");

    /** Relato fictício já com as tags (não chamamos a IA para milhares de dados de teste). */
    private record Relato(String texto, Set<TagRelato> tags) {
        static Relato de(String texto, TagRelato... tags) {
            Set<TagRelato> conjunto = EnumSet.noneOf(TagRelato.class);
            conjunto.addAll(List.of(tags));
            return new Relato(texto, conjunto);
        }
    }

    private static final List<Relato> RELATOS_GRAVES = List.of(
            Relato.de("Disse que minha conta tinha sido invadida e pediu a senha do aplicativo para cancelar uma compra.",
                    TagRelato.COMPRA_SUSPEITA, TagRelato.PEDIU_SENHA),
            Relato.de("Falou que era da central de segurança do %s e pediu um Pix para uma conta segura.",
                    TagRelato.FALSO_SETOR, TagRelato.PEDIU_PIX),
            Relato.de("Pediu o código que chegou por SMS para confirmar minha identidade.", TagRelato.PEDIU_CODIGO),
            Relato.de("Mandou instalar um aplicativo para proteger a conta. Era um app de acesso remoto.",
                    TagRelato.ACESSO_REMOTO),
            Relato.de("Disse que um motoboy ia buscar meu cartão clonado para perícia.",
                    TagRelato.RETIRADA_CARTAO, TagRelato.COMPRA_SUSPEITA),
            Relato.de("Apareceu o número do %s na tela, mas pediram a senha do cartão.", TagRelato.PEDIU_SENHA),
            Relato.de("Falou de uma compra de R$ 2.890 no meu cartão e pediu para digitar a senha no teclado do telefone.",
                    TagRelato.COMPRA_SUSPEITA, TagRelato.PEDIU_SENHA),
            Relato.de("Pediu um Pix de teste de R$ 1 para validar a conta.", TagRelato.PEDIU_PIX),
            Relato.de("Disse que eu estava sendo investigado e precisava transferir o saldo para uma conta de proteção.",
                    TagRelato.URGENCIA, TagRelato.PEDIU_PIX),
            Relato.de("Mandou um link por SMS para baixar o app de segurança do banco.", TagRelato.ACESSO_REMOTO),
            Relato.de("Pediu o token do aplicativo para sincronizar com o celular novo.", TagRelato.PEDIU_SENHA),
            Relato.de("Pediu para cortar o cartão ao meio sem cortar o chip, que alguém viria buscar.",
                    TagRelato.RETIRADA_CARTAO),
            Relato.de("Transferiu para o setor de fraudes, com música de espera igual à do banco, e pediu uma transferência.",
                    TagRelato.FALSO_SETOR, TagRelato.PEDIU_PIX));

    private static final List<Relato> RELATOS_LEVES = List.of(
            Relato.de("Confirmou meu nome e CPF e pediu a data de nascimento.", TagRelato.DADOS_PESSOAIS),
            Relato.de("Ficou me pressionando dizendo que a conta seria bloqueada em 10 minutos.", TagRelato.URGENCIA),
            Relato.de("Gravação dizendo que houve uma compra suspeita e para digitar 1 para falar com atendente.",
                    TagRelato.COMPRA_SUSPEITA),
            Relato.de("Ligaram oferecendo renegociação de dívida com desconto. Achei estranho e desliguei.", TagRelato.OUTRO),
            Relato.de("Ligação muda. Depois ligaram de novo dizendo ser do banco.", TagRelato.OUTRO),
            Relato.de("Disse ser o gerente da minha agência e pediu para atualizar o cadastro com CPF e endereço.",
                    TagRelato.FALSO_SETOR, TagRelato.DADOS_PESSOAIS),
            Relato.de("Insistiu para eu não desligar e não falar com ninguém da família.", TagRelato.URGENCIA));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final int quantidade;
    private final boolean recriar;

    private final Random aleatorio = new Random(2026);
    private final Instant agora = Instant.now();

    // estado da geração
    private final List<Usuario> usuarios = new ArrayList<>();
    private final Map<String, List<Denuncia>> denunciasPorNumero = new LinkedHashMap<>();
    private final Set<String> usados = new HashSet<>();

    private record Usuario(String nome, String email, Instant criadoEm) {
    }

    private record Denuncia(String emailUsuario, String banco, TipoAbordagem tipo, String descricao,
                            Set<TagRelato> tags, Instant criadoEm) {
    }

    public CargaDadosFicticios(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                               @Value("${aloseguro.demo.quantidade:1000}") int quantidade,
                               @Value("${aloseguro.demo.recriar:false}") boolean recriar) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.quantidade = Math.max(50, quantidade);
        this.recriar = recriar;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (recriar) {
            log.warn("DADOS_DEMO_RECRIAR=true: apagando todos os dados para gerar a carga de novo");
            jdbc.update("delete from denuncias");
            jdbc.update("delete from numeros_suspeitos");
            jdbc.update("delete from usuarios");
            jdbc.update("delete from centrais_oficiais");
        } else {
            Long existentes = jdbc.queryForObject("select count(*) from usuarios", Long.class);
            if (existentes != null && existentes > 0) {
                return;
            }
        }

        long inicio = System.currentTimeMillis();
        gerar();
        gravar();
        int totalDenuncias = denunciasPorNumero.values().stream().mapToInt(List::size).sum();
        log.info("Carga fictícia concluída: {} usuários, {} números, {} denúncias em {} ms. Login: {} / {}",
                usuarios.size(), denunciasPorNumero.size(), totalDenuncias,
                System.currentTimeMillis() - inicio, EMAIL_DEMO, SENHA_DEMO);
    }

    // =============================================================== geração em memória

    private void gerar() {
        gerarUsuarios();
        usados.addAll(List.of(EX_SEM_REGISTRO, EX_BLOCO_SUSPEITO));
        gerarExemplos();

        int porBloco = Math.max(5, (int) Math.round(quantidade * 0.36 / BLOCOS.size()));
        for (String prefixo : BLOCOS) {
            for (int i = 0; i < porBloco; i++) {
                String numero = numeroLivre(() -> prefixo + "%04d".formatted(aleatorio.nextInt(9000)));
                boolean ativoAgora = aleatorio.nextDouble() < 0.25;
                adicionarDenuncias(numero, 2 + aleatorio.nextInt(7), ativoAgora ? 2 : 20, 0.85);
            }
        }

        int isolados = (int) Math.round(quantidade * 0.40);
        for (int i = 0; i < isolados; i++) {
            String numero = numeroLivre(this::celularAleatorio);
            adicionarDenuncias(numero, 1 + aleatorio.nextInt(4), 120, aleatorio.nextDouble() < 0.5 ? 0.7 : 0.3);
        }

        int fixos = (int) Math.round(quantidade * 0.12);
        for (int i = 0; i < fixos; i++) {
            String numero = numeroLivre(this::fixoAleatorio);
            adicionarDenuncias(numero, 1 + aleatorio.nextInt(2), 180, 0.2);
        }

        // Centrais oficiais que aparecem em denúncias: indício de spoofing
        for (int i = 2; i <= 3; i++) {
            String central = "0800700" + i + "000";
            usados.add(central);
            adicionarDenuncias(central, 1 + aleatorio.nextInt(3), 60, 0.9);
        }

        // Completa a quantidade com números antigos (denúncias de 2 a 10 meses atrás)
        while (denunciasPorNumero.size() < quantidade) {
            String numero = numeroLivre(this::celularAleatorio);
            List<Denuncia> lista = new ArrayList<>();
            int n = 1 + aleatorio.nextInt(3);
            for (int i = 0; i < n; i++) {
                double dias = 60 + aleatorio.nextDouble() * 240;
                criarDenuncia(lista, dias, 0.4).ifPresent(lista::add);
            }
            if (!lista.isEmpty()) {
                denunciasPorNumero.put(numero, lista);
            }
        }
    }

    private void gerarUsuarios() {
        usuarios.add(new Usuario("Usuário Demonstração", EMAIL_DEMO, agora.minus(Duration.ofDays(200))));
        int total = Math.max(30, quantidade / 8);
        for (int i = 1; i <= total; i++) {
            double sorteio = aleatorio.nextDouble();
            double idadeDias = sorteio < 0.85 ? 30 + aleatorio.nextDouble() * 370
                    : sorteio < 0.95 ? 2 + aleatorio.nextDouble() * 27
                    : aleatorio.nextDouble() * 0.9;
            String nome = NOMES.get(aleatorio.nextInt(NOMES.size())) + " " + SOBRENOMES.get(aleatorio.nextInt(SOBRENOMES.size()));
            usuarios.add(new Usuario(nome, "usuario%03d@exemplo.com".formatted(i), antes(idadeDias)));
        }
    }

    /** Números fixos usados nos exemplos do site, com resultado conhecido. */
    private void gerarExemplos() {
        List<Usuario> antigos = usuarios.stream()
                .filter(u -> u.criadoEm().isBefore(agora.minus(Duration.ofDays(60))))
                .toList();

        // ALTO: 6 pessoas, 3 nas últimas 24 horas, todos relatos graves, dentro de um bloco suspeito
        double[] horasAlto = {2, 5, 20, 72, 144, 240};
        List<Denuncia> alto = new ArrayList<>();
        for (int i = 0; i < horasAlto.length; i++) {
            Relato r = RELATOS_GRAVES.get(i % RELATOS_GRAVES.size());
            alto.add(denuncia(antigos.get(i).email(), r, antes(horasAlto[i] / 24)));
        }
        registrar(EX_ALTO, alto);

        // ATENÇÃO: 2 pessoas, um pedido de Pix e um relato leve
        registrar(EX_ATENCAO, List.of(
                denuncia(antigos.get(10).email(), RELATOS_GRAVES.get(7), antes(50.0 / 24)),
                denuncia(antigos.get(11).email(), RELATOS_LEVES.get(4), antes(200.0 / 24))));

        // BAIXO: telefone fixo, 1 relato leve de 12 dias atrás
        registrar(EX_BAIXO, List.of(denuncia(antigos.get(12).email(), RELATOS_LEVES.get(3), antes(12.5))));

        // CENTRAL OFICIAL: o 0800 verdadeiro aparecendo em golpes (spoofing)
        registrar(EX_CENTRAL, List.of(
                denuncia(antigos.get(13).email(), RELATOS_GRAVES.get(5), antes(0.4)),
                denuncia(EMAIL_DEMO, RELATOS_GRAVES.get(4), antes(1.3))));
    }

    private void adicionarDenuncias(String numero, int quantidadeDenuncias, double idadeMaximaDias, double chanceGrave) {
        List<Denuncia> lista = new ArrayList<>();
        Set<String> quemJaDenunciou = new HashSet<>();
        for (int i = 0; i < quantidadeDenuncias; i++) {
            double dias = aleatorio.nextDouble() * idadeMaximaDias;
            criarDenuncia(lista, dias, chanceGrave)
                    .filter(d -> quemJaDenunciou.add(d.emailUsuario()))
                    .ifPresent(lista::add);
        }
        if (!lista.isEmpty()) {
            denunciasPorNumero.put(numero, lista);
        }
    }

    private java.util.Optional<Denuncia> criarDenuncia(List<Denuncia> jaExistentes, double idadeDias, double chanceGrave) {
        Instant quando = antes(idadeDias);
        // escolhe alguém cuja conta já existia na data da denúncia
        for (int tentativa = 0; tentativa < 15; tentativa++) {
            Usuario u = usuarios.get(1 + aleatorio.nextInt(usuarios.size() - 1));
            boolean repetido = jaExistentes.stream().anyMatch(d -> d.emailUsuario().equals(u.email()));
            if (u.criadoEm().isBefore(quando) && !repetido) {
                List<Relato> fonte = aleatorio.nextDouble() < chanceGrave ? RELATOS_GRAVES : RELATOS_LEVES;
                return java.util.Optional.of(denuncia(u.email(), fonte.get(aleatorio.nextInt(fonte.size())), quando));
            }
        }
        return java.util.Optional.empty();
    }

    private Denuncia denuncia(String email, Relato relato, Instant quando) {
        String banco = BANCOS.get(aleatorio.nextInt(BANCOS.size()));
        boolean comComentario = aleatorio.nextDouble() < 0.8;
        String texto = comComentario ? relato.texto().formatted(banco) : null;
        Set<TagRelato> tags = comComentario ? relato.tags() : EnumSet.of(principal(relato.tags()));
        return new Denuncia(email, banco, abordagem(relato.tags()), texto, tags, quando);
    }

    private void registrar(String numero, List<Denuncia> lista) {
        usados.add(numero);
        denunciasPorNumero.put(numero, new ArrayList<>(lista));
    }

    private String numeroLivre(java.util.function.Supplier<String> gerador) {
        for (int i = 0; i < 1000; i++) {
            String n = gerador.get();
            if (!usados.contains(n)) {
                usados.add(n);
                return n;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um número livre");
    }

    private String celularAleatorio() {
        String n;
        do {
            n = DDDS.get(aleatorio.nextInt(DDDS.size())) + "9" + "%08d".formatted(aleatorio.nextInt(100_000_000));
        } while (BLOCOS.contains(n.substring(0, 7)) || n.startsWith("1191234") || n.startsWith("2190000"));
        return n;
    }

    private String fixoAleatorio() {
        String n;
        do {
            n = DDDS.get(aleatorio.nextInt(DDDS.size())) + (2 + aleatorio.nextInt(4)) + "%07d".formatted(aleatorio.nextInt(10_000_000));
        } while (n.startsWith("113000"));
        return n;
    }

    private static TagRelato principal(Set<TagRelato> tags) {
        return tags.stream().max((a, b) -> Double.compare(a.getGravidade(), b.getGravidade())).orElse(TagRelato.OUTRO);
    }

    private static TipoAbordagem abordagem(Set<TagRelato> tags) {
        if (tags.contains(TagRelato.PEDIU_SENHA)) return TipoAbordagem.PEDIU_SENHA;
        if (tags.contains(TagRelato.PEDIU_CODIGO)) return TipoAbordagem.PEDIU_CODIGO;
        if (tags.contains(TagRelato.PEDIU_PIX)) return TipoAbordagem.PEDIU_TRANSFERENCIA;
        if (tags.contains(TagRelato.ACESSO_REMOTO)) return TipoAbordagem.PEDIU_APLICATIVO;
        if (tags.contains(TagRelato.RETIRADA_CARTAO)) return TipoAbordagem.PEDIU_CARTAO;
        return TipoAbordagem.OUTRO;
    }

    private Instant antes(double dias) {
        return agora.minus(Duration.ofMinutes(Math.round(dias * 1440)));
    }

    // =============================================================== gravação em lote

    private void gravar() {
        String hash = passwordEncoder.encode(SENHA_DEMO);   // um hash só: BCrypt é lento de propósito
        jdbc.batchUpdate("insert into usuarios (nome, email, senha_hash, criado_em) values (?, ?, ?, ?)",
                usuarios.stream().map(u -> new Object[]{u.nome(), u.email(), hash, ts(u.criadoEm())}).toList());
        Map<String, Long> idUsuario = new HashMap<>();
        jdbc.query("select id, email from usuarios", (RowCallbackHandler) rs -> {
            idUsuario.put(rs.getString("email"), rs.getLong("id"));
        });

        List<Object[]> centrais = new ArrayList<>();
        for (int i = 0; i < BANCOS.size(); i++) {
            String banco = BANCOS.get(i) + " (fictício)";
            centrais.add(new Object[]{banco, "0800700" + (i + 1) + "000", "SAC"});
            centrais.add(new Object[]{banco, "400" + (i + 1) + (i + 1) + "000", "Capitais e regiões metropolitanas"});
        }
        jdbc.batchUpdate("insert into centrais_oficiais (banco, numero, descricao) values (?, ?, ?)", centrais);

        List<Object[]> numeros = new ArrayList<>();
        denunciasPorNumero.forEach((numero, lista) -> {
            Instant primeira = lista.stream().map(Denuncia::criadoEm).min(Instant::compareTo).orElse(agora);
            TipoLinha tipo = NumeroTelefone.tipo(numero);
            numeros.add(new Object[]{numero, NumeroTelefone.ddd(numero), tipo.name(), ts(primeira)});
        });
        jdbc.batchUpdate("insert into numeros_suspeitos (numero, ddd, tipo_linha, criado_em) values (?, ?, ?, ?)", numeros);
        Map<String, Long> idNumero = new HashMap<>();
        jdbc.query("select id, numero from numeros_suspeitos", (RowCallbackHandler) rs -> {
            idNumero.put(rs.getString("numero"), rs.getLong("id"));
        });

        List<Object[]> linhas = new ArrayList<>();
        denunciasPorNumero.forEach((numero, lista) -> {
            for (Denuncia d : lista) {
                LocalDateTime ligacao = LocalDateTime.ofInstant(d.criadoEm().minus(Duration.ofMinutes(20)), FUSO);
                linhas.add(new Object[]{
                        idNumero.get(numero), idUsuario.get(d.emailUsuario()), d.banco(), d.tipo().name(),
                        d.descricao(), Timestamp.valueOf(ligacao), TagRelato.paraTexto(d.tags()), "SEED",
                        ts(d.criadoEm())});
            }
        });
        jdbc.batchUpdate("insert into denuncias (numero_id, usuario_id, banco_alegado, tipo_abordagem, descricao, "
                + "data_hora_ligacao, tags, tags_origem, criado_em) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", linhas);
    }

    private static Timestamp ts(Instant instante) {
        return Timestamp.from(instante);
    }
}
