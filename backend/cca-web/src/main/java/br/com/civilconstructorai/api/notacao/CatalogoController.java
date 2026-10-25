package br.com.civilconstructorai.api.notacao;

import br.com.civilconstructorai.notacao.CatalogoComandos;
import br.com.civilconstructorai.notacao.CatalogoComandos.Perfil;
import br.com.civilconstructorai.notacao.ParserVi;
import br.com.civilconstructorai.notacao.TabelaA;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** RF28 catálogo de comandos (público) e RF02 Tabela B / Tabela A para autocompletar e validar no console. */
@RestController
@RequestMapping("/api/v1")
public class CatalogoController {

    @GetMapping("/catalogo/comandos")
    public Map<String, Object> comandos(@RequestParam(defaultValue = "CIVILCONSTRUCTOR") Perfil perfil, @RequestParam(required = false) String q) {
        var lista = q == null || q.isBlank() ? CatalogoComandos.CATALOGO : CatalogoComandos.buscar(q, perfil, 50);
        return Map.of(
                "perfil", perfil,
                "comandos", lista.stream().map(c -> Map.of("codigo", c.codigo(), "nomePtBr", c.nomePtBr(), "nomeEn", c.nomeEn(), "categoria", c.categoria(),
                        "aliases", c.aliases().getOrDefault(perfil, List.of()), "roteiro", c.roteiro(), "papeis", c.papeis(), "rf", c.rf(), "extensao", c.extensao(), "descricao", c.descricao())).toList(),
                "conflitos", CatalogoComandos.conflitos(),
                "teclasReservadasNavegador", CatalogoComandos.TECLAS_RESERVADAS_NAVEGADOR,
                "prioridadeOsnap", CatalogoComandos.prioridadeOsnap(),
                "camadas", CatalogoComandos.CAMADAS_PADRAO);
    }

    @GetMapping("/catalogo/comandos/resolver")
    public Map<String, Object> resolver(@RequestParam String alias, @RequestParam(defaultValue = "CIVILCONSTRUCTOR") Perfil perfil) {
        var c = CatalogoComandos.resolver(alias, perfil);
        return c.<Map<String, Object>>map(x -> Map.of("codigo", x.codigo(), "nomePtBr", x.nomePtBr(), "categoria", x.categoria(), "roteiro", x.roteiro()))
                .orElse(Map.of("codigo", "", "sugestoes", CatalogoComandos.buscar(alias, perfil, 5).stream().map(CatalogoComandos.Comando::codigo).toList()));
    }

    @GetMapping("/vi/catalogo")
    public Map<String, Object> tabelaB() {
        return Map.of("funcoes", CatalogoComandos.tabelaB().stream().map(c -> Map.of("codigo", c.codigo(), "descricao", c.descricao(), "argumentos", c.roteiro().stream().map(p -> Map.of("nome", p.nome(), "tipo", p.tipo(), "padrao", p.padrao() == null ? "" : p.padrao(), "opcional", p.opcional())).toList(), "aliases", c.aliases().getOrDefault(Perfil.CIVILCONSTRUCTOR, List.of()))).toList(),
                "materiais", TabelaA.materiais());
    }

    public record ValidarReq(String vi) {
    }

    /** valida texto VI sem aplicar: todos os erros de uma vez, com linha/coluna (RF02) */
    @PostMapping("/vi/validar")
    public Map<String, Object> validar(@RequestBody ValidarReq req) {
        var prog = ParserVi.parse(req.vi() == null ? "" : req.vi());
        // RF02 CA-02: função desconhecida também é erro, junto com os de sintaxe, com sugestão do catálogo
        var erros = new java.util.ArrayList<Object>(prog.erros());
        for (var c : prog.comandos()) {
            if (CatalogoComandos.porCodigo(c.codigo()).isEmpty()) {
                var sugestoes = CatalogoComandos.buscar(c.codigo(), Perfil.CIVILCONSTRUCTOR, 3).stream().map(CatalogoComandos.Comando::codigo).toList();
                erros.add(Map.of("codigo", "VI-001", "linha", c.linha(), "coluna", 4,
                        "mensagem", "Função desconhecida " + c.codigo() + (sugestoes.isEmpty() ? "." : " — você quis dizer " + String.join(" ou ", sugestoes) + "?")));
            }
        }
        erros.sort(java.util.Comparator.comparingInt(e -> e instanceof ParserVi.ErroVi v ? v.linha() : (Integer) ((Map<?, ?>) e).get("linha")));
        return Map.of("valido", erros.isEmpty(), "comandos", prog.comandos().stream().map(c -> Map.of("codigo", c.codigo(), "linha", c.linha(), "conhecido", CatalogoComandos.porCodigo(c.codigo()).isPresent())).toList(), "erros", erros);
    }
}
