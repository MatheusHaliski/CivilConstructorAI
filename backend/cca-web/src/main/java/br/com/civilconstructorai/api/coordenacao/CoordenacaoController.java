package br.com.civilconstructorai.api.coordenacao;

import br.com.civilconstructorai.coordenacao.CoordenacaoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Conflitos de coordenação do modelo (proposta P-COORD-01). */
@RestController
@RequestMapping("/api/v1/obras/{obraId}/coordenacao")
public class CoordenacaoController {

    private final CoordenacaoService coordenacao;

    public CoordenacaoController(CoordenacaoService coordenacao) {
        this.coordenacao = coordenacao;
    }

    @GetMapping("/conflitos")
    public List<CoordenacaoService.Visao> listar(@PathVariable UUID obraId) {
        return coordenacao.listar(obraId);
    }

    @PostMapping("/verificar")
    public CoordenacaoService.Verificacao verificar(@PathVariable UUID obraId) {
        return coordenacao.verificar(obraId);
    }

    /** verificação das redes de instalações a partir das conexões do modelo (AUD-03/04) */
    @GetMapping("/instalacoes")
    public CoordenacaoService.VerificacaoRedes instalacoes(@PathVariable UUID obraId, @org.springframework.web.bind.annotation.RequestParam(required = false) Double tensao,
                                                          @org.springframework.web.bind.annotation.RequestParam(required = false) Double fp) {
        return coordenacao.verificarRedes(obraId, tensao, fp);
    }

    public record Decisao(String estado, String justificativa) {
    }

    @PostMapping("/conflitos/{id}/estado")
    public CoordenacaoService.Visao decidir(@PathVariable UUID obraId, @PathVariable UUID id, @RequestBody Decisao d) {
        return coordenacao.decidir(obraId, id, d.estado(), d.justificativa());
    }
}
