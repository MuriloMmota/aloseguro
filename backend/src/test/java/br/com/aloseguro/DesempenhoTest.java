package br.com.aloseguro;

import br.com.aloseguro.config.CargaDadosFicticios;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.NumeroSuspeitoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes não funcionais de desempenho e teste da carga de dados fictícios.
 *
 * Usa um banco H2 separado, carregado com 1000 números pelo mesmo gerador usado em produção.
 * Mede o tempo de resposta da consulta em uso normal (uma requisição por vez) e em pico
 * (20 usuários simultâneos) e grava o resultado em target/relatorio-desempenho.txt.
 *
 * Os tempos aqui são da aplicação rodando na própria máquina, sem rede. O teste com o sistema
 * publicado no Render é feito com o script docs/testes/carga-k6.js.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aloseguro_desempenho;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "aloseguro.demo.ativo=true",
        "aloseguro.demo.quantidade=1000"
})
@AutoConfigureMockMvc
class DesempenhoTest {

    private static final int REQUISICOES_NORMAL = 300;
    private static final int USUARIOS_PICO = 20;
    private static final int REQUISICOES_POR_USUARIO = 25;
    private static final long LIMITE_P95_MS = 500;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NumeroSuspeitoRepository numeros;

    @Autowired
    private DenunciaRepository denuncias;

    @Test
    @DisplayName("Carga fictícia: 1000 números e os exemplos com o resultado esperado")
    void cargaDeDados() throws Exception {
        assertTrue(numeros.count() >= 1000, "pelo menos 1000 números");
        assertTrue(denuncias.count() >= 2000, "milhares de denúncias");

        esperar(CargaDadosFicticios.EX_ALTO, "ALTO");
        esperar(CargaDadosFicticios.EX_ATENCAO, "ATENCAO");
        esperar(CargaDadosFicticios.EX_BAIXO, "BAIXO");
        esperar(CargaDadosFicticios.EX_CENTRAL, "CENTRAL_OFICIAL");
        esperar(CargaDadosFicticios.EX_SEM_REGISTRO, "SEM_REGISTROS");
        esperar(CargaDadosFicticios.EX_BLOCO_SUSPEITO, "ATENCAO");
    }

    @Test
    @DisplayName("Desempenho: tempo de resposta da consulta em uso normal e em pico")
    void tempoDeResposta() throws Exception {
        List<String> amostra = numeros.findAll().stream().limit(200).map(n -> n.getNumero()).toList();

        // aquecimento: a primeira chamada carrega classes e caches
        for (int i = 0; i < 20; i++) {
            consultar(amostra.get(i));
        }

        // ---------------- uso normal: uma requisição por vez
        List<Long> normal = new ArrayList<>();
        for (int i = 0; i < REQUISICOES_NORMAL; i++) {
            normal.add(consultar(amostra.get(i % amostra.size())));
        }

        // ---------------- pico: 20 usuários ao mesmo tempo
        List<Long> pico = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(USUARIOS_PICO);
        long inicioPico = System.nanoTime();
        List<Future<?>> tarefas = new ArrayList<>();
        for (int u = 0; u < USUARIOS_PICO; u++) {
            int deslocamento = u * 7;
            tarefas.add(pool.submit(() -> {
                for (int i = 0; i < REQUISICOES_POR_USUARIO; i++) {
                    try {
                        pico.add(consultar(amostra.get((deslocamento + i) % amostra.size())));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }));
        }
        for (Future<?> t : tarefas) {
            t.get();
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        double segundosPico = (System.nanoTime() - inicioPico) / 1e9;

        String relatorio = """
                RELATÓRIO DE DESEMPENHO — GET /api/numeros/{numero}
                Base: %d números e %d denúncias fictícias (H2 em memória, sem rede)

                Uso normal (%d requisições, uma por vez)
                  média: %.1f ms | mediana: %d ms | p95: %d ms | máximo: %d ms

                Pico (%d usuários simultâneos × %d requisições = %d)
                  média: %.1f ms | mediana: %d ms | p95: %d ms | máximo: %d ms
                  vazão: %.0f requisições por segundo

                Critério de aceitação: p95 abaixo de %d ms nos dois cenários.
                """.formatted(numeros.count(), denuncias.count(),
                normal.size(), media(normal), percentil(normal, 50), percentil(normal, 95), percentil(normal, 100),
                USUARIOS_PICO, REQUISICOES_POR_USUARIO, pico.size(),
                media(pico), percentil(pico, 50), percentil(pico, 95), percentil(pico, 100),
                pico.size() / segundosPico, LIMITE_P95_MS);
        System.out.println(relatorio);
        gravar(relatorio);

        assertEquals(USUARIOS_PICO * REQUISICOES_POR_USUARIO, pico.size(), "nenhuma requisição falhou no pico");
        assertTrue(percentil(normal, 95) < LIMITE_P95_MS, "p95 em uso normal abaixo do limite");
        assertTrue(percentil(pico, 95) < LIMITE_P95_MS, "p95 em pico abaixo do limite");
    }

    // ================================================================ auxiliares

    private long consultar(String numero) throws Exception {
        long inicio = System.nanoTime();
        mvc.perform(get("/api/numeros/" + numero)).andExpect(status().isOk());
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    private void esperar(String numero, String nivel) throws Exception {
        mvc.perform(get("/api/numeros/" + numero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value(nivel));
    }

    private static double media(List<Long> tempos) {
        return tempos.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static long percentil(List<Long> tempos, int p) {
        List<Long> ordenados = new ArrayList<>(tempos);
        Collections.sort(ordenados);
        int indice = (int) Math.ceil(p / 100.0 * ordenados.size()) - 1;
        return ordenados.get(Math.max(0, Math.min(indice, ordenados.size() - 1)));
    }

    private static void gravar(String texto) throws IOException {
        Path destino = Path.of("target", "relatorio-desempenho.txt");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, texto);
    }
}
