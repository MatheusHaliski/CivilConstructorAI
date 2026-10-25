package br.com.civilconstructorai.api.orcamento;

import br.com.civilconstructorai.orcamento.Aditivo;
import br.com.civilconstructorai.orcamento.ComposicaoSinapi;
import br.com.civilconstructorai.orcamento.ItemOrcamento;
import br.com.civilconstructorai.orcamento.Orcamento;
import br.com.civilconstructorai.orcamento.OrcamentoService;
import br.com.civilconstructorai.orcamento.SinapiService;
import br.com.civilconstructorai.orcamento.TabelaSinapi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF12 orçamento (SINAPI / Tabela A), itens sugeridos pela IA, aprovação profissional, aditivos do cliente. */
@RestController
@RequestMapping("/api/v1")
public class OrcamentoController {

    private final OrcamentoService orcamentos;
    private final SinapiService sinapi;

    public OrcamentoController(OrcamentoService orcamentos, SinapiService sinapi) {
        this.orcamentos = orcamentos;
        this.sinapi = sinapi;
    }

    public record GerarRequest(String uf, Boolean desonerado, Boolean usarIa) {
    }

    public record ItemRequest(@NotBlank String etapa, @NotBlank String servico, String elementoTipo, @NotBlank String codigoSinapi, @NotBlank String unidade, @NotNull BigDecimal quantidade) {
    }

    public record LiberacaoRequest(boolean totais, boolean detalhe) {
    }

    public record AditivoRequest(@NotBlank String descricao, @NotNull BigDecimal valor) {
    }

    public record ImportacaoRequest(@NotBlank String uf, @NotNull LocalDate mesReferencia, boolean desonerado, @NotBlank String csv) {
    }

    @PostMapping("/obras/{obraId}/orcamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public Orcamento gerar(@PathVariable UUID obraId, @RequestBody(required = false) GerarRequest r) {
        return orcamentos.gerar(obraId, r == null ? null : r.uf(), r != null && Boolean.TRUE.equals(r.desonerado()), r == null || !Boolean.FALSE.equals(r.usarIa()));
    }

    @GetMapping("/obras/{obraId}/orcamentos")
    public List<Orcamento> listar(@PathVariable UUID obraId) {
        return orcamentos.listar(obraId);
    }

    @GetMapping("/obras/{obraId}/orcamentos/{id}")
    public Map<String, Object> visao(@PathVariable UUID obraId, @PathVariable UUID id) {
        return orcamentos.visao(obraId, id);
    }

    @PostMapping("/obras/{obraId}/orcamentos/{id}/itens")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemOrcamento adicionarItem(@PathVariable UUID obraId, @PathVariable UUID id, @Valid @RequestBody ItemRequest r) {
        return orcamentos.adicionarItem(obraId, id, new OrcamentoService.NovoItem(r.etapa(), r.servico(), r.elementoTipo(), r.codigoSinapi(), r.unidade(), r.quantidade()));
    }

    @PostMapping("/obras/{obraId}/orcamentos/{id}/itens/{itemId}/confirmar")
    public ItemOrcamento confirmarItem(@PathVariable UUID obraId, @PathVariable UUID id, @PathVariable UUID itemId) {
        return orcamentos.confirmarItemIa(obraId, id, itemId);
    }

    @DeleteMapping("/obras/{obraId}/orcamentos/{id}/itens/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerItem(@PathVariable UUID obraId, @PathVariable UUID id, @PathVariable UUID itemId) {
        orcamentos.removerItem(obraId, id, itemId);
    }

    @PostMapping("/obras/{obraId}/orcamentos/{id}/aprovar")
    public Orcamento aprovar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return orcamentos.decidir(obraId, id, true);
    }

    @PostMapping("/obras/{obraId}/orcamentos/{id}/rejeitar")
    public Orcamento rejeitar(@PathVariable UUID obraId, @PathVariable UUID id) {
        return orcamentos.decidir(obraId, id, false);
    }

    @PostMapping("/obras/{obraId}/orcamentos/{id}/liberar-cliente")
    public Orcamento liberar(@PathVariable UUID obraId, @PathVariable UUID id, @RequestBody LiberacaoRequest r) {
        return orcamentos.liberarParaCliente(obraId, id, r.totais(), r.detalhe());
    }

    @GetMapping("/obras/{obraId}/orcamentos/{id}/aditivos")
    public List<Aditivo> aditivos(@PathVariable UUID obraId, @PathVariable UUID id) {
        return orcamentos.aditivos(obraId, id);
    }

    @PostMapping("/obras/{obraId}/orcamentos/{id}/aditivos")
    @ResponseStatus(HttpStatus.CREATED)
    public Aditivo proporAditivo(@PathVariable UUID obraId, @PathVariable UUID id, @Valid @RequestBody AditivoRequest r) {
        return orcamentos.proporAditivo(obraId, id, r.descricao(), r.valor());
    }

    @PostMapping("/obras/{obraId}/orcamentos/{id}/aditivos/{aditivoId}/decidir")
    public Aditivo decidirAditivo(@PathVariable UUID obraId, @PathVariable UUID id, @PathVariable UUID aditivoId, @RequestParam boolean aprovar) {
        return orcamentos.decidirAditivo(obraId, id, aditivoId, aprovar);
    }

    @GetMapping("/sinapi/tabelas")
    public List<TabelaSinapi> tabelas() {
        return sinapi.tabelas();
    }

    @GetMapping("/sinapi/composicoes")
    public List<ComposicaoSinapi> composicoes(@RequestParam UUID tabelaId, @RequestParam(required = false) String termo) {
        return sinapi.buscar(tabelaId, termo);
    }

    @PostMapping("/sinapi/importar")
    @ResponseStatus(HttpStatus.CREATED)
    public TabelaSinapi importar(@Valid @RequestBody ImportacaoRequest r) {
        return sinapi.importar(new SinapiService.Importacao(r.uf(), r.mesReferencia(), r.desonerado(), r.csv()));
    }
}
