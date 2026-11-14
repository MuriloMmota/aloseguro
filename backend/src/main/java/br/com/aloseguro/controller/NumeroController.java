package br.com.aloseguro.controller;

import br.com.aloseguro.dto.ConsultaDtos.ConsultaResponse;
import br.com.aloseguro.dto.ConsultaDtos.RankingItemResponse;
import br.com.aloseguro.service.DenunciaService;
import br.com.aloseguro.service.RiscoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/numeros")
public class NumeroController {

    private final RiscoService riscoService;
    private final DenunciaService denunciaService;

    public NumeroController(RiscoService riscoService, DenunciaService denunciaService) {
        this.riscoService = riscoService;
        this.denunciaService = denunciaService;
    }

    /** GET /api/numeros/ranking — números mais denunciados no período (exige login). */
    @GetMapping("/ranking")
    public List<RankingItemResponse> ranking(@RequestParam(defaultValue = "30") int dias,
                                             @RequestParam(defaultValue = "10") int limite) {
        return denunciaService.ranking(dias, limite);
    }

    /** GET /api/numeros/{numero} — consulta pública com o score de risco e os fatores. */
    @GetMapping("/{numero}")
    public ConsultaResponse consultar(@PathVariable String numero) {
        return riscoService.consultar(numero);
    }
}
