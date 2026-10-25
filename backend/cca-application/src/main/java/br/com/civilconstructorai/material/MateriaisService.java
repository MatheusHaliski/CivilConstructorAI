package br.com.civilconstructorai.material;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.dimensionamento.DimensionadorNbr6118;
import br.com.civilconstructorai.notacao.CatalogoMateriais;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Catálogo técnico + materiais personalizados da empresa. Resolve códigos para o cálculo (o dimensionamento lê o fck
 * daqui, não do nome do material) e valida a criação de personalizados: família aceita, propriedades conhecidas,
 * unidade igual à do catálogo, faixa plausível, origem informada pela pessoa (especificado, declarado pelo fabricante
 * com documentação ou ensaio com laudo) — valores estimados e adotados são do sistema, não do formulário.
 */
@Service
@Transactional
public class MateriaisService {

    static final Set<String> FAMILIAS_PERSONALIZAVEIS = Set.of("CONCRETO", "ACO_ARMADURA", "ALVENARIA", "ARGAMASSA", "TUBO");
    static final Map<String, double[]> FAIXAS = Map.of("fck", new double[]{20, 100}, "fyk", new double[]{250, 800}, "largura", new double[]{0.05, 0.40});

    public record Propriedade(String chave, Object valor, String unidade, String origem) {
    }

    public record Produto(String fabricante, String referencia, String documento) {
    }

    public record NovoMaterial(String familia, String baseadoEm, String nome, List<Propriedade> propriedades, Produto produto) {
    }

    public record Personalizado(UUID id, String codigo, String nome, String familia, String baseadoEm, String situacao, Object item, Object historico) {
    }

    private final Repositorios.MateriaisPersonalizados personalizados;
    private final UsoDeMaterial uso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final ObjectMapper mapper;

    public MateriaisService(Repositorios.MateriaisPersonalizados personalizados, UsoDeMaterial uso, ContextoUsuario contexto, RegistroOperacoes registro,
                            Relogio relogio, ObjectMapper mapper) {
        this.personalizados = personalizados;
        this.uso = uso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
        this.mapper = mapper;
    }

    /** catálogo completo para a tela: famílias, referências, itens com propriedades derivadas (pelo agregado), personalizados */
    @Transactional(readOnly = true)
    public Map<String, Object> catalogo(String agregado) {
        var cat = CatalogoMateriais.padrao();
        var out = new LinkedHashMap<String, Object>();
        out.put("versao", cat.versao());
        out.put("familias", cat.familias().values());
        out.put("referencias", cat.referencias().values());
        var itens = new ArrayList<Map<String, Object>>();
        for (var i : cat.itens()) {
            itens.add(item(i, cat.propriedadesCompletas(i, agregado)));
        }
        out.put("itens", itens);
        var bits = new LinkedHashMap<String, Object>();
        for (var codigo : List.of("MAT.ACO_ARMADURA.CA50", "MAT.ACO_ARMADURA.CA60")) {
            bits.put(codigo, cat.bitolas(codigo).stream().map(b -> Map.of("mm", b.mm(), "areaCm2", Math.round(b.areaCm2() * 1000) / 1000.0,
                    "kgPorMetro", Math.round(b.kgPorMetro() * 1000) / 1000.0, "situacao", b.situacao())).toList());
        }
        out.put("bitolas", bits);
        out.put("composicoes", cat.composicoes());
        out.put("servicos", cat.servicos());
        out.put("personalizados", listarPersonalizados());
        return out;
    }

    private static Map<String, Object> item(CatalogoMateriais.Item i, List<CatalogoMateriais.Propriedade> props) {
        var m = new LinkedHashMap<String, Object>();
        m.put("codigo", i.codigo());
        m.put("nome", i.nome());
        m.put("familia", i.familia());
        m.put("resumo", i.resumo());
        m.put("legado", i.legado());
        m.put("aplicacoes", i.aplicacoes());
        m.put("cor", i.cor());
        m.put("propriedades", props);
        m.put("personalizado", i.personalizado());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Personalizado> listarPersonalizados() {
        var u = contexto.exigir();
        if (u.empresaAtivaId() == null) {
            return List.of();
        }
        return personalizados.findByTenantIdOrderByNome(u.empresaAtivaId()).stream().map(this::visao).toList();
    }

    private Personalizado visao(MaterialPersonalizado p) {
        return new Personalizado(p.getId(), p.getCodigo(), p.getNome(), p.getFamilia(), p.getBaseadoEm(), p.getSituacao().name(),
                mapper.readTree(p.getItemJson()), mapper.readTree(p.getHistoricoJson()));
    }

    /** resolve um código (catálogo, legado ou personalizado da empresa ativa) para o dimensionamento */
    @Transactional(readOnly = true)
    public Optional<DimensionadorNbr6118.InfoMaterial> info(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        var cat = CatalogoMateriais.padrao();
        var it = cat.item(codigo);
        if (it.isPresent()) {
            var fck = it.get().numero("fck");
            return Optional.of(new DimensionadorNbr6118.InfoMaterial(it.get().codigo(), it.get().nome(), fck.isPresent() ? fck.getAsDouble() : null, false, "CATALOGO"));
        }
        var u = contexto.exigir();
        if (u.empresaAtivaId() == null) {
            return Optional.empty();
        }
        return personalizados.findByTenantIdAndCodigoIgnoreCase(u.empresaAtivaId(), codigo).map(p -> {
            var item = CatalogoMateriais.item(mapper.readTree(p.getItemJson()), true, p.getSituacao().name());
            var fck = item.numero("fck");
            return new DimensionadorNbr6118.InfoMaterial(p.getCodigo(), p.getNome(), fck.isPresent() ? fck.getAsDouble() : null, true, p.getSituacao().name());
        });
    }

    /** material personalizado da empresa ativa que pode ser aplicado a elementos (não rejeitado) */
    @Transactional(readOnly = true)
    public boolean personalizadoAplicavel(String codigo) {
        var u = contexto.atual().orElse(null);
        if (codigo == null || u == null || u.empresaAtivaId() == null) {
            return false;
        }
        return personalizados.findByTenantIdAndCodigoIgnoreCase(u.empresaAtivaId(), codigo).filter(p -> p.getSituacao() != MaterialPersonalizado.Situacao.REJEITADO).isPresent();
    }

    public Personalizado criar(NovoMaterial n) {
        var u = contexto.exigir();
        if (u.empresaAtivaId() == null) {
            throw new ExcecaoDominio.AcessoNegado("Materiais personalizados pertencem a uma empresa: crie ou escolha uma empresa ativa.");
        }
        var cat = CatalogoMateriais.padrao();
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        if (n.familia() == null || !FAMILIAS_PERSONALIZAVEIS.contains(n.familia())) {
            erros.add(new ExcecaoDominio.Validacao.Erro("familia", "Família não aceita material personalizado. Aceitas: " + String.join(", ", FAMILIAS_PERSONALIZAVEIS.stream().sorted().toList()) + "."));
        }
        if (n.nome() == null || n.nome().isBlank()) {
            erros.add(new ExcecaoDominio.Validacao.Erro("nome", "Dê um nome que diferencie o material (ex.: \"Concreto C30 com fibra — fornecedor X\")."));
        }
        CatalogoMateriais.Item base = null;
        if (n.baseadoEm() != null && !n.baseadoEm().isBlank()) {
            base = cat.item(n.baseadoEm()).orElse(null);
            if (base == null || !base.familia().equals(n.familia())) {
                erros.add(new ExcecaoDominio.Validacao.Erro("baseadoEm", "O material de origem precisa existir no catálogo e ser da mesma família."));
            }
        }
        // definição de cada chave (unidade, tipo) tirada do catálogo para a família
        var definicoes = new LinkedHashMap<String, CatalogoMateriais.Propriedade>();
        for (var i : cat.itens()) {
            if (i.familia().equals(n.familia())) {
                i.propriedades().forEach(p -> definicoes.putIfAbsent(p.chave(), p));
            }
        }
        var props = n.propriedades() == null ? List.<Propriedade>of() : n.propriedades();
        boolean temProduto = n.produto() != null && naoVazio(n.produto().fabricante()) && naoVazio(n.produto().referencia()) && naoVazio(n.produto().documento());
        for (int k = 0; k < props.size(); k++) {
            var p = props.get(k);
            var def = definicoes.get(p.chave());
            String campo = "propriedades[" + k + "]";
            if (def == null) {
                erros.add(new ExcecaoDominio.Validacao.Erro(campo, "Propriedade \"" + p.chave() + "\" não existe para a família " + n.familia() + "."));
                continue;
            }
            if (!igual(def.unidade(), p.unidade())) {
                erros.add(new ExcecaoDominio.Validacao.Erro(campo, "Unidade incorreta para " + def.rotulo() + ": use " + (def.unidade() == null ? "sem unidade" : def.unidade()) + "."));
            }
            if (def.valor() instanceof Number && !(p.valor() instanceof Number)) {
                erros.add(new ExcecaoDominio.Validacao.Erro(campo, def.rotulo() + " deve ser numérico."));
            } else if (p.valor() instanceof Number num && FAIXAS.containsKey(p.chave())) {
                var f = FAIXAS.get(p.chave());
                if (num.doubleValue() < f[0] || num.doubleValue() > f[1]) {
                    erros.add(new ExcecaoDominio.Validacao.Erro(campo, String.format(Locale.ROOT, "%s fora da faixa aceita (%s a %s %s).", def.rotulo(), f[0], f[1], def.unidade())));
                }
            }
            if (p.origem() == null || !Set.of("ESPECIFICADO", "DECLARADO_FABRICANTE", "ENSAIO").contains(p.origem())) {
                erros.add(new ExcecaoDominio.Validacao.Erro(campo, "Origem do valor: ESPECIFICADO, DECLARADO_FABRICANTE ou ENSAIO (estimados e adotados são calculados pelo sistema)."));
            } else if (("DECLARADO_FABRICANTE".equals(p.origem()) || "ENSAIO".equals(p.origem())) && !temProduto) {
                erros.add(new ExcecaoDominio.Validacao.Erro(campo, "Valor " + ("ENSAIO".equals(p.origem()) ? "de ensaio" : "declarado pelo fabricante")
                        + " exige documentação: fabricante, referência do produto e documento (ficha técnica ou laudo)."));
            }
        }
        if ("CONCRETO".equals(n.familia()) && props.stream().noneMatch(p -> "fck".equals(p.chave()))) {
            erros.add(new ExcecaoDominio.Validacao.Erro("propriedades", "Concreto personalizado precisa de fck (MPa)."));
        }
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
        String codigo = "MAT.PERSONALIZADO." + slug(n.nome());
        if (cat.item(codigo).isPresent() || personalizados.findByTenantIdAndCodigoIgnoreCase(u.empresaAtivaId(), codigo).isPresent()) {
            throw new ExcecaoDominio.Conflito("Já existe um material com o código " + codigo + ": use outro nome.");
        }
        ObjectNode item = mapper.createObjectNode();
        item.put("codigo", codigo).put("nome", n.nome().trim()).put("familia", n.familia()).put("resumo", "personalizado · validação pendente");
        item.putArray("legado");
        var apl = item.putArray("aplicacoes");
        if (base != null) {
            base.aplicacoes().forEach(apl::add);
        }
        var arr = item.putArray("propriedades");
        for (var p : props) {
            var def = definicoes.get(p.chave());
            var o = arr.addObject().put("chave", p.chave()).put("rotulo", def.rotulo()).put("unidade", def.unidade()).put("origem", p.origem())
                    .put("referencia", def.referencia()).put("nota", "DECLARADO_FABRICANTE".equals(p.origem()) || "ENSAIO".equals(p.origem())
                            ? "documento: " + n.produto().documento() : "valor especificado pela empresa");
            if (p.valor() instanceof Number num) {
                o.put("valor", num.doubleValue());
            } else {
                o.put("valor", p.valor() == null ? null : String.valueOf(p.valor()));
            }
        }
        if (temProduto) {
            item.putObject("produto").put("fabricante", n.produto().fabricante()).put("referencia", n.produto().referencia()).put("documento", n.produto().documento());
        }
        var hist = mapper.createArrayNode();
        hist.addObject().put("quando", relogio.agora().toString()).put("quem", u.contaId().toString()).put("acao", "CRIADO");
        var mp = personalizados.save(new MaterialPersonalizado(u.empresaAtivaId(), codigo, n.nome().trim(), n.familia(), base == null ? null : base.codigo(),
                mapper.writeValueAsString(item), u.contaId(), mapper.writeValueAsString(hist)));
        registro.registrar(new RegistroOperacoes.Operacao("MATERIAL_PERSONALIZADO_CRIADO", u.contaId(), u.empresaAtivaId(), null, "MaterialPersonalizado", mp.getId(), Map.of("codigo", codigo)));
        return visao(mp);
    }

    /** validação técnica por profissional com selo verificado (registro do conselho) */
    public Personalizado decidir(UUID id, boolean validar, String motivo) {
        var u = contexto.exigir();
        var mp = personalizados.findByIdAndTenantId(id, u.empresaAtivaId()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Material"));
        if (!u.seloVerificado()) {
            throw new ExcecaoDominio.AcessoNegado("Validar um material exige selo profissional verificado (CREA/CAU).");
        }
        if (!validar && (motivo == null || motivo.isBlank())) {
            throw new ExcecaoDominio.Validacao("motivo", "Explique por que o material foi rejeitado.");
        }
        var hist = (tools.jackson.databind.node.ArrayNode) mapper.readTree(mp.getHistoricoJson());
        hist.addObject().put("quando", relogio.agora().toString()).put("quem", u.contaId().toString()).put("acao", validar ? "VALIDADO" : "REJEITADO").put("motivo", motivo);
        mp.decidir(validar, mapper.writeValueAsString(hist));
        registro.registrar(new RegistroOperacoes.Operacao(validar ? "MATERIAL_PERSONALIZADO_VALIDADO" : "MATERIAL_PERSONALIZADO_REJEITADO", u.contaId(), u.empresaAtivaId(), null,
                "MaterialPersonalizado", mp.getId(), Map.of("codigo", mp.getCodigo())));
        return visao(mp);
    }

    /** exclusão só de material sem uso; em uso → 409 com os elementos */
    public void excluir(UUID id) {
        var u = contexto.exigir();
        var mp = personalizados.findByIdAndTenantId(id, u.empresaAtivaId()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Material"));
        var usos = uso.elementosCom(mp.getCodigo(), 10);
        if (!usos.isEmpty()) {
            throw new ExcecaoDominio.Conflito("O material " + mp.getCodigo() + " está aplicado em " + usos.size() + (usos.size() == 10 ? "+" : "") + " elemento(s) — por exemplo "
                    + usos.stream().limit(3).map(x -> x.obraNome() + " › " + (x.rotulo() == null ? x.tipo().toLowerCase() : x.rotulo())).toList()
                    + ". Troque o material desses elementos antes de excluir.");
        }
        personalizados.delete(mp);
        registro.registrar(new RegistroOperacoes.Operacao("MATERIAL_PERSONALIZADO_EXCLUIDO", u.contaId(), u.empresaAtivaId(), null, "MaterialPersonalizado", mp.getId(), Map.of("codigo", mp.getCodigo())));
    }

    private static boolean naoVazio(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean igual(String a, String b) {
        return (a == null || a.isBlank()) ? (b == null || b.isBlank()) : a.equals(b);
    }

    static String slug(String nome) {
        var s = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
}
