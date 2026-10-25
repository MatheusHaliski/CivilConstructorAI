package br.com.civilconstructorai.api.estrutura;

import br.com.civilconstructorai.estrutura.CargasService;
import br.com.civilconstructorai.estrutura.LancadorAutomatico;
import br.com.civilconstructorai.estrutura.RestricoesService;
import br.com.civilconstructorai.estrutura.VaosService;
import br.com.civilconstructorai.estrutura.carga.Carga;
import br.com.civilconstructorai.estrutura.carga.Combinacao;
import br.com.civilconstructorai.estrutura.carga.ParametrosVento;
import br.com.civilconstructorai.estrutura.lancamento.LancamentoEstrutural;
import br.com.civilconstructorai.estrutura.lancamento.RascunhoLancamento;
import br.com.civilconstructorai.estrutura.restricoes.ExcecaoNormativa;
import br.com.civilconstructorai.estrutura.vaos.CalculadoraVaos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** RF04 cargas e lançamento · RF06 vãos · RF08 restrições. A verificação de papel na obra fica nos serviços (camada 2). */
@RestController
@RequestMapping("/api/v1/obras/{obraId}/estrutura")
public class EstruturaController {

    private final CargasService cargas;
    private final LancadorAutomatico lancador;
    private final VaosService vaos;
    private final RestricoesService restricoes;

    public EstruturaController(CargasService cargas, LancadorAutomatico lancador, VaosService vaos, RestricoesService restricoes) {
        this.cargas = cargas;
        this.lancador = lancador;
        this.vaos = vaos;
        this.restricoes = restricoes;
    }

    public record NovaCargaRequest(@NotBlank String pavimentoId, @NotNull br.com.civilconstructorai.estrutura.carga.NaturezaAcao natureza,
                                   @NotNull br.com.civilconstructorai.estrutura.carga.TipoCarga tipo, double valor, String elementoAlvoId,
                                   String descricao, String casoCarga) {
    }

    public record VentoRequest(double v0, Double s1, Double s2, Double s3, Double ca) {
        ParametrosVento para() {
            return new ParametrosVento(v0, s1 == null ? 1.0 : s1, s2 == null ? 1.0 : s2, s3 == null ? 1.0 : s3, ca == null ? 1.2 : ca);
        }
    }

    public record SugestaoRequest(@NotBlank String pavimentoId) {
    }

    public record DecisaoRequest(String motivo, String artRrt) {
    }

    @GetMapping("/cargas")
    public List<Carga> cargas(@PathVariable UUID obraId) {
        return cargas.listar(obraId);
    }

    @PostMapping("/cargas")
    @ResponseStatus(HttpStatus.CREATED)
    public Carga lancar(@PathVariable UUID obraId, @Valid @RequestBody NovaCargaRequest r) {
        return cargas.lancar(obraId, new CargasService.NovaCarga(r.pavimentoId(), r.natureza(), r.tipo(), r.valor(), r.elementoAlvoId(), r.descricao(), r.casoCarga()));
    }

    @DeleteMapping("/cargas/{cargaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID obraId, @PathVariable UUID cargaId) {
        cargas.remover(obraId, cargaId);
    }

    @PutMapping("/vento/{pavimentoId}")
    public LancamentoEstrutural vento(@PathVariable UUID obraId, @PathVariable String pavimentoId, @Valid @RequestBody VentoRequest r) {
        return cargas.definirVento(obraId, pavimentoId, r.para());
    }

    @GetMapping("/combinacoes")
    public List<Combinacao> combinacoes(@PathVariable UUID obraId, @RequestParam String pavimentoId) {
        return cargas.combinacoes(obraId, pavimentoId);
    }

    @GetMapping("/lancamentos")
    public List<LancamentoEstrutural> lancamentos(@PathVariable UUID obraId) {
        return cargas.lancamentos(obraId);
    }

    @PostMapping("/lancamentos/{pavimentoId}/pronto")
    public LancamentoEstrutural pronto(@PathVariable UUID obraId, @PathVariable String pavimentoId) {
        return cargas.marcarPronto(obraId, pavimentoId);
    }

    @PostMapping("/lancamento/sugerir")
    @ResponseStatus(HttpStatus.CREATED)
    public RascunhoLancamento sugerir(@PathVariable UUID obraId, @Valid @RequestBody SugestaoRequest r) {
        return lancador.sugerir(obraId, r.pavimentoId());
    }

    @GetMapping("/lancamento/rascunhos")
    public List<RascunhoLancamento> rascunhos(@PathVariable UUID obraId) {
        return lancador.listar(obraId);
    }

    @PostMapping("/lancamento/rascunhos/{rascunhoId}/aprovar")
    public RascunhoLancamento aprovar(@PathVariable UUID obraId, @PathVariable UUID rascunhoId) {
        return lancador.decidir(obraId, rascunhoId, true, null);
    }

    @PostMapping("/lancamento/rascunhos/{rascunhoId}/rejeitar")
    public RascunhoLancamento rejeitar(@PathVariable UUID obraId, @PathVariable UUID rascunhoId, @RequestBody(required = false) DecisaoRequest r) {
        return lancador.decidir(obraId, rascunhoId, false, r == null ? null : r.motivo());
    }

    @GetMapping("/vaos")
    public List<CalculadoraVaos.RelatorioVaos> vaos(@PathVariable UUID obraId) {
        return vaos.relatorio(obraId);
    }

    @GetMapping("/restricoes")
    public RestricoesService.Restricoes restricoes(@PathVariable UUID obraId) {
        return restricoes.obter(obraId);
    }

    @PutMapping("/restricoes")
    public RestricoesService.Restricoes atualizarRestricoes(@PathVariable UUID obraId, @RequestBody RestricoesService.Restricoes r) {
        return restricoes.atualizar(obraId, r);
    }

    @GetMapping("/excecoes-normativas")
    public List<ExcecaoNormativa> excecoes(@PathVariable UUID obraId) {
        return restricoes.listarExcecoes(obraId);
    }

    @PostMapping("/excecoes-normativas")
    @ResponseStatus(HttpStatus.CREATED)
    public ExcecaoNormativa solicitarExcecao(@PathVariable UUID obraId, @RequestBody RestricoesService.NovaExcecao n) {
        return restricoes.solicitarExcecao(obraId, n);
    }

    @PostMapping("/excecoes-normativas/{id}/decidir")
    public ExcecaoNormativa decidirExcecao(@PathVariable UUID obraId, @PathVariable UUID id, @RequestParam boolean aprovar) {
        return restricoes.decidirExcecao(obraId, id, aprovar);
    }

    @PostMapping("/excecoes-normativas/{id}/revogar")
    public ExcecaoNormativa revogarExcecao(@PathVariable UUID obraId, @PathVariable UUID id) {
        return restricoes.revogarExcecao(obraId, id);
    }
}
