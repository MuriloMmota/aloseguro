package br.com.aloseguro.motor;

import br.com.aloseguro.model.NivelRisco;
import br.com.aloseguro.model.TagRelato;
import br.com.aloseguro.model.TipoLinha;
import br.com.aloseguro.motor.MotorRisco.DadosDenuncia;
import br.com.aloseguro.motor.MotorRisco.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de unidade do motor de risco: o cálculo é testado sozinho, sem banco nem HTTP.
 */
class MotorRiscoTest {

    private final MotorRisco motor = new MotorRisco();
    private final Instant agora = Instant.parse("2026-11-20T12:00:00Z");

    private DadosDenuncia denuncia(double diasAtras, long usuario, double idadeContaDias, TagRelato... tags) {
        return new DadosDenuncia(
                agora.minus(Duration.ofMinutes(Math.round(diasAtras * 1440))),
                usuario,
                agora.minus(Duration.ofMinutes(Math.round(idadeContaDias * 1440))),
                Set.of(tags));
    }

    private int pontos(Resultado r, String codigo) {
        return r.fatores().stream().filter(f -> f.codigo().equals(codigo)).findFirst().orElseThrow().pontos();
    }

    @Test
    @DisplayName("Sem denúncias e sem vizinhos: score 0 e SEM_REGISTROS")
    void semDenuncias() {
        Resultado r = motor.calcular(List.of(), TipoLinha.CELULAR, 0, false, agora);
        assertEquals(0, r.score());
        assertEquals(NivelRisco.SEM_REGISTROS, r.nivel());
        assertEquals(6, r.fatores().size());
    }

    @Test
    @DisplayName("Número oficial de banco é sempre CENTRAL_OFICIAL, mesmo com denúncias")
    void centralOficial() {
        Resultado r = motor.calcular(List.of(denuncia(0.1, 1, 100, TagRelato.PEDIU_SENHA)),
                TipoLinha.SERVICO_0800, 0, true, agora);
        assertEquals(NivelRisco.CENTRAL_OFICIAL, r.nivel());
    }

    @Test
    @DisplayName("Denúncia antiga pesa menos que denúncia recente")
    void recenciaDecai() {
        int recente = pontos(motor.calcular(List.of(denuncia(0, 1, 100, TagRelato.OUTRO)), TipoLinha.FIXO, 0, false, agora), "RECENCIA");
        int antiga = pontos(motor.calcular(List.of(denuncia(60, 1, 100, TagRelato.OUTRO)), TipoLinha.FIXO, 0, false, agora), "RECENCIA");
        assertEquals(10, recente);     // 25 × (1 − e^(−1/2)) = 9,8
        assertEquals(0, antiga);       // e^(−60/15) ≈ 0,02
    }

    @Test
    @DisplayName("Conta criada há menos de 1 dia pesa 0,3 no fator pessoas")
    void contaNovaPesaMenos() {
        int antiga = pontos(motor.calcular(List.of(denuncia(0, 1, 100, TagRelato.OUTRO)), TipoLinha.FIXO, 0, false, agora), "PESSOAS");
        int nova = pontos(motor.calcular(List.of(denuncia(0, 1, 0.1, TagRelato.OUTRO)), TipoLinha.FIXO, 0, false, agora), "PESSOAS");
        assertEquals(8, antiga);       // 25 × (1 − e^(−1/2,5)) = 8,2
        assertEquals(3, nova);         // 25 × (1 − e^(−0,3/2,5)) = 2,8
    }

    @Test
    @DisplayName("A mesma pessoa conta uma vez só no fator pessoas")
    void pessoaRepetidaContaUmaVez() {
        List<DadosDenuncia> mesmaPessoa = List.of(denuncia(0, 7, 100, TagRelato.OUTRO), denuncia(3, 7, 100, TagRelato.OUTRO));
        assertEquals(8, pontos(motor.calcular(mesmaPessoa, TipoLinha.FIXO, 0, false, agora), "PESSOAS"));
    }

    @Test
    @DisplayName("Volume satura: 50 denúncias não ultrapassam o máximo de cada fator nem 100 no total")
    void saturacao() {
        List<DadosDenuncia> muitas = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            muitas.add(denuncia(0.1, i, 100, TagRelato.PEDIU_PIX));
        }
        Resultado r = motor.calcular(muitas, TipoLinha.CELULAR, 30, false, agora);
        r.fatores().forEach(f -> assertTrue(f.pontos() <= f.maximo(), f.nome()));
        assertEquals(100, r.score());
        assertEquals(NivelRisco.ALTO, r.nivel());
        assertEquals("ALTA", r.confianca());
    }

    @Test
    @DisplayName("Relatos graves pesam mais que relatos leves")
    void gravidade() {
        List<DadosDenuncia> graves = List.of(denuncia(1, 1, 100, TagRelato.PEDIU_SENHA),
                denuncia(1, 2, 100, TagRelato.PEDIU_PIX), denuncia(1, 3, 100, TagRelato.ACESSO_REMOTO));
        List<DadosDenuncia> leves = List.of(denuncia(1, 1, 100, TagRelato.OUTRO),
                denuncia(1, 2, 100, TagRelato.OUTRO), denuncia(1, 3, 100, TagRelato.OUTRO));
        assertEquals(20, pontos(motor.calcular(graves, TipoLinha.CELULAR, 0, false, agora), "GRAVIDADE"));
        assertEquals(4, pontos(motor.calcular(leves, TipoLinha.CELULAR, 0, false, agora), "GRAVIDADE"));
    }

    @Test
    @DisplayName("Celular denunciado soma 10 no perfil da linha; fixo soma 4")
    void perfilDaLinha() {
        List<DadosDenuncia> uma = List.of(denuncia(1, 1, 100, TagRelato.OUTRO));
        assertEquals(10, pontos(motor.calcular(uma, TipoLinha.CELULAR, 0, false, agora), "LINHA"));
        assertEquals(4, pontos(motor.calcular(uma, TipoLinha.FIXO, 0, false, agora), "LINHA"));
    }

    @Test
    @DisplayName("Três denúncias em 24 horas atingem o máximo do fator pico")
    void pico() {
        List<DadosDenuncia> tres = List.of(denuncia(0.1, 1, 100, TagRelato.OUTRO),
                denuncia(0.5, 2, 100, TagRelato.OUTRO), denuncia(0.9, 3, 100, TagRelato.OUTRO));
        assertEquals(10, pontos(motor.calcular(tres, TipoLinha.FIXO, 0, false, agora), "PICO"));
    }

    @Test
    @DisplayName("Número sem denúncias num bloco muito denunciado vira ATENÇÃO")
    void blocoSuspeito() {
        Resultado r = motor.calcular(List.of(), TipoLinha.CELULAR, 20, false, agora);
        assertEquals(10, r.score());
        assertEquals(NivelRisco.ATENCAO, r.nivel());
    }

    @Test
    @DisplayName("Faixas: abaixo de 30 é BAIXO, de 30 a 59 ATENÇÃO, a partir de 60 ALTO")
    void faixas() {
        assertEquals(NivelRisco.BAIXO, motor.nivel(29, 1, 0, false));
        assertEquals(NivelRisco.ATENCAO, motor.nivel(30, 1, 0, false));
        assertEquals(NivelRisco.ATENCAO, motor.nivel(59, 1, 0, false));
        assertEquals(NivelRisco.ALTO, motor.nivel(60, 1, 0, false));
    }
}
