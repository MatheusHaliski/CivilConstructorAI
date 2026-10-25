package br.com.civilconstructorai.api.lgpd;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.lgpd.DadosTitularService;
import br.com.civilconstructorai.lgpd.ExportacaoService;
import br.com.civilconstructorai.lgpd.PedidoTitular;
import br.com.civilconstructorai.lgpd.PedidoTitularService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF25 · "Seus dados": ver, corrigir, exportar, compartilhamentos e pedidos do titular. */
@RestController
@RequestMapping("/api/v1/me")
public class DadosTitularController {

    public record Correcao(@NotBlank String valor) {
    }

    public record NovoPedido(@NotNull PedidoTitular.Tipo tipo, UUID empresaId, String categoria, @NotBlank String descricao) {
    }

    private final DadosTitularService dados;
    private final ExportacaoService exportacoes;
    private final PedidoTitularService pedidos;
    private final ContextoUsuario contexto;

    public DadosTitularController(DadosTitularService dados, ExportacaoService exportacoes, PedidoTitularService pedidos, ContextoUsuario contexto) {
        this.dados = dados;
        this.exportacoes = exportacoes;
        this.pedidos = pedidos;
        this.contexto = contexto;
    }

    @GetMapping("/dados")
    public List<DadosTitularService.Categoria> resumo() {
        return dados.resumo(contexto.exigir().contaId());
    }

    @PutMapping("/dados/{campo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void corrigir(@PathVariable String campo, @Valid @RequestBody Correcao corpo) {
        dados.corrigir(contexto.exigir().contaId(), campo, corpo.valor());
    }

    @GetMapping("/compartilhamentos")
    public List<DadosTitularService.Compartilhamento> compartilhamentos() {
        return dados.compartilhamentos(contexto.exigir().contaId());
    }

    @PostMapping("/exportacoes")
    public ResponseEntity<Map<String, UUID>> exportar() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", exportacoes.solicitar()));
    }

    @GetMapping("/exportacoes")
    public List<ExportacaoService.Estado> exportacoes() {
        return exportacoes.minhas();
    }

    @GetMapping("/exportacoes/{id}")
    public ExportacaoService.Estado exportacao(@PathVariable UUID id) {
        return exportacoes.consultar(id);
    }

    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> abrirPedido(@Valid @RequestBody NovoPedido corpo) {
        var p = pedidos.abrir(corpo.tipo(), corpo.empresaId(), corpo.categoria(), corpo.descricao());
        return Map.of("id", p.getId(), "estado", p.getEstado(), "prazoEm", p.getPrazoEm());
    }

    @GetMapping("/pedidos")
    public List<Map<String, Object>> meusPedidos() {
        return pedidos.meus().stream().map(DadosTitularController::pedido).toList();
    }

    static Map<String, Object> pedido(PedidoTitular p) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("id", p.getId());
        m.put("tipo", p.getTipo());
        m.put("categoria", p.getCategoria());
        m.put("descricao", p.getDescricao());
        m.put("estado", p.getEstado());
        m.put("prazoEm", p.getPrazoEm());
        m.put("criadoEm", p.getCriadoEm());
        if (p.getResposta() != null) {
            m.put("resposta", p.getResposta());
            m.put("respondidoEm", p.getRespondidoEm());
        }
        return m;
    }
}
