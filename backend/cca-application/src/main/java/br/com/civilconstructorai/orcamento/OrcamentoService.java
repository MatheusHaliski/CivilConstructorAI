package br.com.civilconstructorai.orcamento;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.Json;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import br.com.civilconstructorai.quantitativo.Quantificador;
import br.com.civilconstructorai.quantitativo.QuantitativoService;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF12 · orçamento a partir do quantitativo (RF11): vínculo direto pela Tabela A (material → código SINAPI) e,
 * para o que sobra, sugestão da IA (rota associacao-sinapi, tool use {@code consultar_sinapi} executado
 * localmente) marcada {@code geradoPorIa} e validada no backend. Só vale após aprovação por profissional.
 */
@Service
@Transactional
public class OrcamentoService {

    /** material da Tabela A → (serviço, código SINAPI, unidade do quantitativo) */
    static final Map<String, String[]> TABELA_A = Map.of(
            "MAT_CONC_FCK30", new String[]{"Concreto fck 30 MPa", "94965", "m3"},
            "MAT_CONC_FCK25", new String[]{"Concreto fck 25 MPa", "94964", "m3"},
            "MAT_ACO_CA50", new String[]{"Aço CA-50", "92779", "kg"},
            "MAT_ALV_BLOCO14", new String[]{"Alvenaria bloco cerâmico 14 cm", "103323", "m2"},
            "MAT_ALV_BLOCO19", new String[]{"Alvenaria estrutural 19 cm", "103355", "m2"},
            "MAT_ACO_PERFIL_W", new String[]{"Perfil de aço W", "100777", "kg"});

    static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["associacoes"],
             "properties":{"associacoes":{"type":"array","items":{"type":"object","additionalProperties":false,
               "required":["servico","codigo","unidade","justificativa"],
               "properties":{"servico":{"type":"string"},"codigo":{"type":"string"},"unidade":{"type":"string"},"justificativa":{"type":"string"}}}}}}
            """;

    static final String SYSTEM = """
            Você é um orçamentista de obras no Brasil. Para cada serviço sem vínculo, escolha a composição SINAPI mais
            adequada usando a ferramenta consultar_sinapi (nunca invente códigos; só use códigos devolvidos pela
            ferramenta) e respeite a unidade do quantitativo. Responda SOMENTE com o JSON do esquema.
            """;

    public record NovoItem(String etapa, String servico, String elementoTipo, String codigoSinapi, String unidade, BigDecimal quantidade) {
    }

    private final Repositorios.Orcamentos orcamentos;
    private final Repositorios.Itens itens;
    private final Repositorios.Aditivos aditivos;
    private final SinapiService sinapi;
    private final QuantitativoService quantitativos;
    private final ModeloLinguagem ia;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final Json json;

    public OrcamentoService(Repositorios.Orcamentos orcamentos, Repositorios.Itens itens, Repositorios.Aditivos aditivos, SinapiService sinapi,
                            QuantitativoService quantitativos, ModeloLinguagem ia, AcessoObra acesso, ContextoUsuario contexto,
                            RegistroOperacoes registro, Relogio relogio, Json json) {
        this.orcamentos = orcamentos;
        this.itens = itens;
        this.aditivos = aditivos;
        this.sinapi = sinapi;
        this.quantitativos = quantitativos;
        this.ia = ia;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
        this.json = json;
    }

    public Orcamento gerar(UUID obraId, String uf, boolean desonerado, boolean usarIa) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ORCAMENTISTA, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var q = quantitativos.atual(obraId);
        var tabela = sinapi.vigente(uf == null ? "PR" : uf, desonerado);
        for (var o : orcamentos.findByObraIdOrderByCriadoEmDesc(obraId)) {
            if (o.getVersaoModelo() != q.versaoModelo()) {
                o.tornarObsoleto();
            }
        }
        var orc = orcamentos.save(new Orcamento(u.empresaAtivaId(), obraId, q.id(), q.versaoModelo(), tabela.getId(), tabela.rotulo()));
        orc.iniciar();
        // agrega o quantitativo por serviço
        var servicos = new LinkedHashMap<String, BigDecimal>(); // chave: etapa|servico|unidade|codigo?
        for (var l : q.linhas()) {
            acumular(servicos, "Estrutura", "Concreto " + l.tipo().name().toLowerCase(), "m3", materialCodigo(l.materialId(), "m3"), l.concretoM3());
            acumular(servicos, "Estrutura", "Fôrma " + l.tipo().name().toLowerCase(), "m2", null, l.formaM2());
            acumular(servicos, "Estrutura", "Aço CA-50 " + l.tipo().name().toLowerCase(), "kg", "92779", l.acoKg());
            acumular(servicos, "Vedações", "Alvenaria", "m2", materialCodigo(l.materialId(), "m2"), l.alvenariaM2());
        }
        var semVinculo = new ArrayList<String>();
        var lista = new ArrayList<ItemOrcamento>();
        for (var e : servicos.entrySet()) {
            var k = e.getKey().split("\\|", -1);
            if (e.getValue().signum() <= 0) {
                continue;
            }
            if (!k[3].isEmpty()) {
                var comp = sinapi.porCodigo(tabela.getId(), k[3]);
                lista.add(new ItemOrcamento(u.empresaAtivaId(), orc.getId(), k[0], k[1], null, comp, k[2], e.getValue(), false, null));
            } else {
                semVinculo.add(e.getKey());
            }
        }
        int itensIa = 0;
        if (usarIa && !semVinculo.isEmpty()) {
            itensIa = sugerirComIa(orc, tabela.getId(), semVinculo, servicos, lista, u.empresaAtivaId());
        }
        itens.saveAll(lista);
        orc.concluirGeracao(itensIa > 0, total(lista), itensIa);
        registro.registrar(new RegistroOperacoes.Operacao("ORCAMENTO_GERADO", u.contaId(), u.empresaAtivaId(), obraId, "Orcamento", orc.getId(), Map.of("itensIa", String.valueOf(itensIa))));
        return orc;
    }

    private int sugerirComIa(Orcamento orc, UUID tabelaId, List<String> semVinculo, Map<String, BigDecimal> servicos, List<ItemOrcamento> lista, UUID tenantId) {
        // execução local da ferramenta consultar_sinapi: candidatos por termo da descrição
        var candidatos = new LinkedHashMap<String, Object>();
        for (var chave : semVinculo) {
            var k = chave.split("\\|", -1);
            var termo = k[1].split(" ")[0];
            candidatos.put(k[1], sinapi.buscar(tabelaId, termo).stream().map(c -> Map.of("codigo", c.getCodigo(), "descricao", c.getDescricao(), "unidade", c.getUnidade())).toList());
        }
        var entrada = json.escrever(Map.of("servicos", semVinculo.stream().map(s -> { var k = s.split("\\|", -1); return Map.of("etapa", k[0], "servico", k[1], "unidade", k[2]); }).toList(),
                "ferramenta", Map.of("nome", "consultar_sinapi", "strict", true, "additionalProperties", false, "resultados", candidatos)));
        var resposta = ia.gerarEstruturado(RotaIa.ASSOCIACAO_SINAPI, SYSTEM, entrada, SCHEMA, Map.of("rf", "RF12"));
        if (!resposta.completa()) {
            return 0;
        }
        int n = 0;
        JsonNode raiz = json.mapper().readTree(resposta.conteudoJson());
        for (JsonNode a : raiz.path("associacoes")) {
            var chave = semVinculo.stream().filter(s -> s.split("\\|", -1)[1].equals(a.path("servico").asText())).findFirst().orElse(null);
            if (chave == null) {
                continue;
            }
            var k = chave.split("\\|", -1);
            // valida: código existe (consulta sem exceção — uma exceção num método @Transactional participante
            // marcaria a transação inteira como rollback-only)
            var comp = sinapi.buscarPorCodigo(tabelaId, a.path("codigo").asText()).orElse(null);
            if (comp == null) {
                continue;
            }
            try {
                lista.add(new ItemOrcamento(tenantId, orc.getId(), k[0], k[1], null, comp, k[2], servicos.get(chave), true, a.path("justificativa").asText())); // valida: unidade compatível
                n++;
            } catch (ExcecaoDominio e) {
                // sugestão com unidade incompatível é descartada; o item fica sem vínculo para o orçamentista resolver
            }
        }
        return n;
    }

    private static void acumular(Map<String, BigDecimal> mapa, String etapa, String servico, String unidade, String codigo, double valor) {
        if (valor <= 0) {
            return;
        }
        mapa.merge(etapa + "|" + servico + "|" + unidade + "|" + (codigo == null ? "" : codigo), BigDecimal.valueOf(valor), BigDecimal::add);
    }

    private static String materialCodigo(String materialId, String unidade) {
        var m = materialId == null ? null : TABELA_A.get(materialId);
        return m != null && m[2].equals(unidade) ? m[1] : null;
    }

    private static BigDecimal total(List<ItemOrcamento> lista) {
        return lista.stream().map(ItemOrcamento::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    public ItemOrcamento adicionarItem(UUID obraId, UUID orcId, NovoItem n) {
        var orc = editavel(obraId, orcId);
        var comp = sinapi.porCodigo(orc.getTabelaSinapiId(), n.codigoSinapi());
        var item = itens.save(new ItemOrcamento(contexto.exigir().empresaAtivaId(), orc.getId(), n.etapa(), n.servico(), n.elementoTipo(), comp, n.unidade(), n.quantidade(), false, null));
        recalcular(orc);
        return item;
    }

    public ItemOrcamento confirmarItemIa(UUID obraId, UUID orcId, UUID itemId) {
        var orc = editavel(obraId, orcId);
        var item = itens.findByIdAndOrcamentoId(itemId, orc.getId()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Item"));
        item.confirmar(contexto.exigir().contaId());
        recalcular(orc);
        return item;
    }

    public void removerItem(UUID obraId, UUID orcId, UUID itemId) {
        var orc = editavel(obraId, orcId);
        var item = itens.findByIdAndOrcamentoId(itemId, orc.getId()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Item"));
        itens.delete(item);
        recalcular(orc);
    }

    private Orcamento editavel(UUID obraId, UUID orcId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ORCAMENTISTA, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var orc = orcamentos.findByIdAndObraId(orcId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Orçamento"));
        if (!orc.editavel()) {
            throw new ExcecaoDominio.RegraDeNegocio("orcamento_fechado", "Orçamento em " + orc.getEstado() + " não aceita alterações.");
        }
        return orc;
    }

    private void recalcular(Orcamento orc) {
        var lista = itens.findByOrcamentoIdOrderByEtapaAscServicoAsc(orc.getId());
        orc.atualizarTotal(total(lista), (int) lista.stream().filter(ItemOrcamento::pendenteDeConfirmacao).count());
    }

    public Orcamento decidir(UUID obraId, UUID orcId, boolean aprovar) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var orc = orcamentos.findByIdAndObraId(orcId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Orçamento"));
        if (aprovar) {
            if (!u.podeAprovar(obraId)) {
                throw new ExcecaoDominio.AcessoNegado("Aprovar um orçamento exige papel aprovador, selo profissional verificado e 2FA ativo.");
            }
            var pendentes = (int) itens.findByOrcamentoIdOrderByEtapaAscServicoAsc(orc.getId()).stream().filter(ItemOrcamento::pendenteDeConfirmacao).count();
            orc.aprovar(u.contaId(), relogio.agora(), pendentes);
        } else {
            acesso.exigirPapel(obraId, PapelObra.ORCAMENTISTA, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
            orc.rejeitar(u.contaId(), relogio.agora());
        }
        registro.registrar(RegistroOperacoes.Operacao.de(aprovar ? "ORCAMENTO_APROVADO" : "ORCAMENTO_REJEITADO", u.contaId(), u.empresaAtivaId(), obraId, "Orcamento", orc.getId()));
        return orc;
    }

    public Orcamento liberarParaCliente(UUID obraId, UUID orcId, boolean totais, boolean detalhe) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var orc = orcamentos.findByIdAndObraId(orcId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Orçamento"));
        orc.liberarParaCliente(totais, detalhe);
        var u = contexto.exigir();
        registro.registrar(new RegistroOperacoes.Operacao("ORCAMENTO_LIBERADO_CLIENTE", u.contaId(), u.empresaAtivaId(), obraId, "Orcamento", orc.getId(), Map.of("detalhe", String.valueOf(detalhe))));
        return orc;
    }

    /** visão do orçamento respeitando DN-02: CLIENTE vê só totais por etapa (ou o detalhe, se liberado) */
    @Transactional(readOnly = true)
    public Map<String, Object> visao(UUID obraId, UUID orcId) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var orc = orcamentos.findByIdAndObraId(orcId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Orçamento"));
        var papeis = u.papeisNaObra(obraId);
        boolean soCliente = !papeis.isEmpty() && papeis.stream().allMatch(p -> p == PapelObra.CLIENTE);
        var lista = itens.findByOrcamentoIdOrderByEtapaAscServicoAsc(orc.getId());
        var out = new LinkedHashMap<String, Object>();
        out.put("id", orc.getId());
        out.put("estado", orc.getEstado());
        out.put("dataBase", orc.getDataBase());
        out.put("total", orc.getTotal());
        out.put("bdiPct", orc.getBdiPct());
        out.put("geradoPorIa", orc.getItensGeradosPorIa() > 0);
        var porEtapa = new LinkedHashMap<String, BigDecimal>();
        for (var i : lista) {
            porEtapa.merge(i.getEtapa(), i.getTotal(), BigDecimal::add);
        }
        out.put("totaisPorEtapa", porEtapa);
        if (soCliente) {
            if (!orc.isLiberadoCliente()) {
                throw new ExcecaoDominio.RecursoNaoEncontrado("Orçamento");
            }
            if (orc.isDetalheLiberadoCliente()) {
                out.put("itens", lista);
            }
            return out;
        }
        out.put("itens", lista);
        out.put("itensPendentesIa", lista.stream().filter(ItemOrcamento::pendenteDeConfirmacao).count());
        return out;
    }

    @Transactional(readOnly = true)
    public List<Orcamento> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var papeis = u.papeisNaObra(obraId);
        boolean soCliente = !papeis.isEmpty() && papeis.stream().allMatch(p -> p == PapelObra.CLIENTE);
        return orcamentos.findByObraIdOrderByCriadoEmDesc(obraId).stream().filter(o -> !soCliente || o.isLiberadoCliente()).toList();
    }

    public Aditivo proporAditivo(UUID obraId, UUID orcId, String descricao, BigDecimal valor) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ORCAMENTISTA, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var orc = orcamentos.findByIdAndObraId(orcId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Orçamento"));
        if (!orc.isLiberadoCliente()) {
            throw new ExcecaoDominio.RegraDeNegocio("orcamento_nao_liberado", "Aditivo só sobre orçamento aprovado e liberado ao cliente.");
        }
        return aditivos.save(new Aditivo(u.empresaAtivaId(), orc.getId(), descricao, valor, u.contaId()));
    }

    public Aditivo decidirAditivo(UUID obraId, UUID orcId, UUID aditivoId, boolean aprovar) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.CLIENTE);
        var u = contexto.exigir();
        var a = aditivos.findByIdAndOrcamentoId(aditivoId, orcId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Aditivo"));
        a.decidirCliente(aprovar, u.contaId(), relogio.agora());
        registro.registrar(RegistroOperacoes.Operacao.de(aprovar ? "ADITIVO_APROVADO_CLIENTE" : "ADITIVO_RECUSADO_CLIENTE", u.contaId(), u.empresaAtivaId(), obraId, "Aditivo", a.getId()));
        return a;
    }

    @Transactional(readOnly = true)
    public List<Aditivo> aditivos(UUID obraId, UUID orcId) {
        acesso.exigirMembro(obraId);
        return aditivos.findByOrcamentoIdOrderByCriadoEmDesc(orcId);
    }

    static List<Quantificador.Linha> nenhuma() {
        return List.of();
    }
}
