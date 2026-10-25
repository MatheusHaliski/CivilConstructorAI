package br.com.civilconstructorai.api.modelo;

import br.com.civilconstructorai.modelo.DiffModelo;
import br.com.civilconstructorai.modelo.EstadoJson;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloRepositorio;
import br.com.civilconstructorai.modelo.ModeloService;
import br.com.civilconstructorai.modelo.ModeloService.PedidoComando;
import br.com.civilconstructorai.modelo.ModeloService.ResultadoComando;
import br.com.civilconstructorai.modelo.RelatorioAuditoria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF01/RF02/RF03/RF13/RF17 · modelo BIM da obra. Toda escrita exige X-XSRF-TOKEN (csrf.spa). */
@RestController
@RequestMapping("/api/v1/obras/{obraId}/modelo")
public class ModeloController {

    private final ModeloService service;
    private final EstadoJson json;

    public ModeloController(ModeloService service, EstadoJson json) {
        this.service = service;
        this.json = json;
    }

    public record ComandoReq(UUID pavimentoId, String codigo, Map<String, Object> args, String vi, String origem, Long versaoBase) {
        PedidoComando pedido() {
            return new PedidoComando(pavimentoId, codigo, args, vi, origem == null ? OrigemComando.CANVAS : OrigemComando.valueOf(origem.toUpperCase()), versaoBase);
        }
    }

    public record LoteReq(@NotEmpty List<ComandoReq> comandos) {
    }

    public record TextoNaturalReq(UUID pavimentoId, @jakarta.validation.constraints.NotBlank String texto) {
    }

    public record EixoReq(Map<String, Object> p1, Map<String, Object> p2, String nome, Long versaoBase) {
    }

    public record MalhaReq(Integer nx, Double dx, Integer ny, Double dy, List<Double> x, List<Double> y, Map<String, Object> origem, Long versaoBase) {
    }

    public record PavimentoReq(String nome, @jakarta.validation.constraints.NotNull Double z, String tipo, Long versaoBase) {
    }

    public record MoverEixoReq(@jakarta.validation.constraints.NotNull Double posicao) {
    }

    public record LiberarReq(boolean liberar) {
    }

    public record DecisaoAlertaReq(@jakarta.validation.constraints.NotBlank String decisao, String justificativa) {
    }

    @GetMapping
    public Map<String, Object> obter(@PathVariable UUID obraId, @RequestParam(required = false) Long versao) {
        var v = service.obter(obraId, versao);
        var out = new LinkedHashMap<String, Object>();
        out.put("obraId", v.obraId());
        out.put("versao", v.versao());
        out.put("liberadaCliente", v.liberadaCliente());
        out.put("pavimentos", v.pavimentos().stream().map(json::pavimento).toList());
        out.put("eixos", v.eixos().stream().map(json::eixo).toList());
        out.put("elementos", v.elementos().stream().map(json::elemento).toList());
        return out;
    }

    @GetMapping("/diff")
    public Map<String, Object> diff(@PathVariable UUID obraId, @RequestParam long desde) {
        return diffJson(service.diffDesde(obraId, desde));
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot(@PathVariable UUID obraId, @RequestParam(required = false) Long versao) {
        var s = service.snapshot(obraId, versao);
        var out = new LinkedHashMap<String, Object>(s);
        @SuppressWarnings("unchecked")
        var camadas = (Map<String, List<br.com.civilconstructorai.modelo.Elemento>>) s.get("camadas");
        var camadasJson = new LinkedHashMap<String, Object>();
        camadas.forEach((k, l) -> camadasJson.put(k, l.stream().map(json::elemento).toList()));
        out.put("camadas", camadasJson);
        out.put("pavimentos", ((List<br.com.civilconstructorai.modelo.Pavimento>) s.get("pavimentos")).stream().map(json::pavimento).toList());
        out.put("eixos", ((List<br.com.civilconstructorai.modelo.Eixo>) s.get("eixos")).stream().map(json::eixo).toList());
        return out;
    }

    @PostMapping("/comandos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> aplicar(@PathVariable UUID obraId, @RequestBody ComandoReq req) {
        return resultadoJson(service.aplicarComando(obraId, req.pedido()));
    }

    @PostMapping("/comandos/lote")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> lote(@PathVariable UUID obraId, @Valid @RequestBody LoteReq req) {
        return service.aplicarLote(obraId, req.comandos().stream().map(ComandoReq::pedido).toList()).stream().map(this::resultadoJson).toList();
    }

    @PostMapping("/comandos/{comandoId}/desfazer")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> desfazer(@PathVariable UUID obraId, @PathVariable UUID comandoId) {
        return resultadoJson(service.desfazer(obraId, comandoId));
    }

    @GetMapping("/comandos")
    public List<Map<String, Object>> historico(@PathVariable UUID obraId, @RequestParam(defaultValue = "0") long desde) {
        return service.historico(obraId, desde).stream().map(c -> Map.<String, Object>of("id", c.id(), "codigo", c.codigo(), "vi", c.vi(), "origem", c.origem(), "versaoBase", c.versaoBase(), "versaoNova", c.versaoNova(), "criadoEm", c.criadoEm())).toList();
    }

    @PostMapping("/texto-natural")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> textoNatural(@PathVariable UUID obraId, @Valid @RequestBody TextoNaturalReq req) {
        var resultados = service.textoNatural(obraId, req.pavimentoId(), req.texto());
        return Map.of("geradoPorIa", true, "aviso", "Rascunho gerado por IA: confirme para aprovar (RF27).", "comandos", resultados.stream().map(this::resultadoJson).toList());
    }

    // ---------------- RF03
    @PostMapping("/eixos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> eixo(@PathVariable UUID obraId, @RequestBody EixoReq req) {
        var args = new LinkedHashMap<String, Object>();
        args.put("p1", req.p1());
        args.put("p2", req.p2());
        args.put("nome", req.nome());
        return resultadoJson(service.aplicarComando(obraId, new PedidoComando(null, "EIXO", args, null, OrigemComando.CANVAS, req.versaoBase())));
    }

    @PostMapping("/malha")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> malha(@PathVariable UUID obraId, @RequestBody MalhaReq req) {
        var args = new LinkedHashMap<String, Object>();
        args.put("nx", req.nx());
        args.put("dx", req.dx());
        args.put("ny", req.ny());
        args.put("dy", req.dy());
        args.put("x", req.x());
        args.put("y", req.y());
        args.put("origem", req.origem());
        return resultadoJson(service.aplicarComando(obraId, new PedidoComando(null, "MALHA", args, null, OrigemComando.CANVAS, req.versaoBase())));
    }

    @PatchMapping("/eixos/{eixoId}")
    public Map<String, Object> moverEixo(@PathVariable UUID obraId, @PathVariable UUID eixoId, @Valid @RequestBody MoverEixoReq req) {
        return resultadoJson(service.moverEixo(obraId, eixoId, req.posicao()));
    }

    @PostMapping("/pavimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> pavimento(@PathVariable UUID obraId, @Valid @RequestBody PavimentoReq req) {
        var args = new LinkedHashMap<String, Object>();
        args.put("nome", req.nome());
        args.put("z", req.z());
        args.put("tipo", req.tipo());
        return resultadoJson(service.aplicarComando(obraId, new PedidoComando(null, "PAVIMENTO", args, null, OrigemComando.CANVAS, req.versaoBase())));
    }

    // ---------------- RF13 / RF16 / RF17
    @PatchMapping("/liberar-cliente")
    public Map<String, Object> liberar(@PathVariable UUID obraId, @RequestBody LiberarReq req) {
        service.liberarCliente(obraId, req.liberar());
        return Map.of("liberadaCliente", req.liberar());
    }

    @PostMapping("/verificar-conclusao")
    public Map<String, Object> verificarConclusao(@PathVariable UUID obraId, @RequestParam(required = false) UUID pavimentoId) {
        var v = service.verificarConclusao(obraId, pavimentoId);
        return Map.of("aprovado", v.stream().noneMatch(x -> x.severidade() == br.com.civilconstructorai.modelo.Enums.Severidade.VIOLACAO), "itens", v);
    }

    @GetMapping("/alertas-transicao")
    public List<ModeloRepositorio.AlertaTransicao> alertas(@PathVariable UUID obraId) {
        return service.alertasTransicao(obraId);
    }

    @PostMapping("/alertas-transicao/{alertaId}/decidir")
    public ModeloRepositorio.AlertaTransicao decidir(@PathVariable UUID obraId, @PathVariable UUID alertaId, @Valid @RequestBody DecisaoAlertaReq req) {
        return service.decidirAlerta(obraId, alertaId, req.decisao(), req.justificativa());
    }

    // ---------------- serialização
    Map<String, Object> resultadoJson(ResultadoComando r) {
        var out = new LinkedHashMap<String, Object>();
        out.put("comandoId", r.comandoId());
        out.put("vi", r.vi());
        out.put("versao", r.versao());
        out.put("diff", diffJson(r.diff()));
        out.put("relatorio", relatorioJson(r.relatorio()));
        return out;
    }

    Map<String, Object> diffJson(DiffModelo d) {
        var out = new LinkedHashMap<String, Object>();
        out.put("versaoNova", d.versaoNova);
        out.put("criados", d.criados.stream().map(json::elemento).toList());
        out.put("alterados", d.alterados.stream().map(json::elemento).toList());
        out.put("removidos", d.removidos);
        out.put("pavimentosCriados", d.pavimentosCriados.stream().map(json::pavimento).toList());
        out.put("eixosCriados", d.eixosCriados.stream().map(json::eixo).toList());
        out.put("eixosAlterados", d.eixosAlterados.stream().map(json::eixo).toList());
        out.put("geradoPorIa", d.geradoPorIa);
        return out;
    }

    Map<String, Object> relatorioJson(RelatorioAuditoria r) {
        return json.relatorio(r);
    }
}
