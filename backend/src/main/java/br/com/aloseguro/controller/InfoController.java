package br.com.aloseguro.controller;

import br.com.aloseguro.dto.ConsultaDtos.MotorResponse;
import br.com.aloseguro.dto.ConsultaDtos.StatusResponse;
import br.com.aloseguro.ia.ClienteLlm;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.NumeroSuspeitoRepository;
import br.com.aloseguro.service.RiscoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Rotas públicas de informação: como o motor funciona e se o sistema está no ar.
 */
@RestController
@RequestMapping("/api")
public class InfoController {

    private final RiscoService riscoService;
    private final ClienteLlm llm;
    private final NumeroSuspeitoRepository numeros;
    private final DenunciaRepository denuncias;

    public InfoController(RiscoService riscoService, ClienteLlm llm,
                          NumeroSuspeitoRepository numeros, DenunciaRepository denuncias) {
        this.riscoService = riscoService;
        this.llm = llm;
        this.numeros = numeros;
        this.denuncias = denuncias;
    }

    /** GET /api/motor — fatores, pesos, faixas e tags do motor de risco. */
    @GetMapping("/motor")
    public MotorResponse motor() {
        return riscoService.explicarMotor();
    }

    /** GET /api/status — usado pelo Render para saber se a aplicação está no ar. */
    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(
                "AlôSeguro API",
                "2.0",
                llm.ativo(),
                llm.getProvedor(),
                llm.getModelo(),
                llm.getUltimoSucesso(),
                llm.getUltimoErro(),
                numeros.count(),
                denuncias.count(),
                Instant.now());
    }
}
