package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.comum.UsuarioAtual;
import br.com.civilconstructorai.ia.ModeloLinguagem;
import br.com.civilconstructorai.ia.RotaIa;
import br.com.civilconstructorai.modelo.Enums.Camada;
import br.com.civilconstructorai.modelo.Enums.OrigemComando;
import br.com.civilconstructorai.modelo.ModeloRepositorio.AlertaTransicao;
import br.com.civilconstructorai.modelo.ModeloRepositorio.ModeloCarregado;
import br.com.civilconstructorai.notacao.FormatadorVi;
import br.com.civilconstructorai.notacao.ParserVi;
import br.com.civilconstructorai.notacao.Sanitizador;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso central do modelo (RF01/RF02/RF03/RF13/RF17): mesmo caminho para canvas, console VI e IA.
 * Trava a versão (409 em conflito), audita (RF16), persiste e devolve o diff.
 */
@Service
public class ModeloService {

    private final ModeloRepositorio repositorio;
    private final AuditorService auditor;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final ModeloLinguagem llm;
    private final ObjectMapper mapper;

    public ModeloService(ModeloRepositorio repositorio, AuditorService auditor, AcessoObra acesso, ContextoUsuario contexto,
                         RegistroOperacoes registro, ModeloLinguagem llm, ObjectMapper mapper) {
        this.repositorio = repositorio;
        this.auditor = auditor;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.llm = llm;
        this.mapper = mapper;
    }

    public record PedidoComando(UUID pavimentoId, String codigo, Map<String, Object> args, String vi, OrigemComando origem, Long versaoBase) {
    }

    public record ResultadoComando(UUID comandoId, String vi, long versao, DiffModelo diff, RelatorioAuditoria relatorio) {
    }

    public record Visao(UUID obraId, long versao, boolean liberadaCliente, List<Pavimento> pavimentos, List<Eixo> eixos, List<Elemento> elementos) {
    }

    // ------------------------------------------------------------------ leitura
    /** histórico de comandos (RF02/RF13): dentro da transação para o RLS enxergar o tenant */
    @Transactional(readOnly = true)
    public List<ModeloRepositorio.ComandoRegistro> historico(UUID obraId, long desdeVersaoExclusiva) {
        acesso.exigirMembro(obraId);
        return repositorio.comandos(obraId, desdeVersaoExclusiva);
    }

    @Transactional(readOnly = true)
    public Visao obter(UUID obraId, Long versao) {
        acesso.exigirMembro(obraId);
        var carregado = carregarOuCriarSomenteLeitura(obraId);
        var estado = versao == null || versao == carregado.versao() ? carregado.estado()
                : repositorio.estadoNaVersao(obraId, versao).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Versão " + versao + " do modelo"));
        var m = new ModeloBim(estado);
        return new Visao(obraId, estado.versao(), carregado.liberadaCliente(), m.pavimentosOrdenados(), m.eixos(), m.elementos());
    }

    @Transactional(readOnly = true)
    public DiffModelo diffDesde(UUID obraId, long desde) {
        acesso.exigirMembro(obraId);
        var carregado = carregarOuCriarSomenteLeitura(obraId);
        if (desde > carregado.versao()) {
            throw new ExcecaoDominio.Conflito("Versão " + desde + " é posterior à atual (" + carregado.versao() + ").");
        }
        var atual = new ModeloBim(carregado.estado());
        if (desde == carregado.versao()) {
            var vazio = new DiffModelo();
            vazio.versaoNova = carregado.versao();
            return vazio;
        }
        var antes = repositorio.estadoNaVersao(obraId, desde).orElseThrow(() -> new ExcecaoDominio.Conflito("Versão " + desde + " não está mais disponível para diff (410). Recarregue o modelo."));
        return diferenca(new ModeloBim(antes), atual);
    }

    /** RF13: geometria por camada para a cena; CLIENTE só vê versão liberada */
    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(UUID obraId, Long versao) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var carregado = carregarOuCriarSomenteLeitura(obraId);
        boolean cliente = u.papeisNaObra(obraId).stream().allMatch(p -> p == PapelObra.CLIENTE || p == PapelObra.FORNECEDOR) && !u.papeisNaObra(obraId).isEmpty();
        long alvo = versao == null ? (cliente ? (carregado.versaoLiberada() == null ? -1 : carregado.versaoLiberada()) : carregado.versao()) : versao;
        if (cliente && (!carregado.liberadaCliente() || carregado.versaoLiberada() == null || alvo != carregado.versaoLiberada())) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Versão do modelo");
        }
        var estado = alvo == carregado.versao() ? carregado.estado() : repositorio.estadoNaVersao(obraId, alvo).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Versão do modelo"));
        var m = new ModeloBim(estado);
        var porCamada = new LinkedHashMap<String, List<Elemento>>();
        for (var c : Camada.values()) {
            porCamada.put(c.rotulo(), new ArrayList<>());
        }
        for (var e : m.elementos()) {
            if (cliente && (e.getCamada() == Camada.ia_rascunho || e.getRascunhoIaId() != null)) {
                continue;
            }
            porCamada.get(e.getCamada().rotulo()).add(e);
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("obraId", obraId);
        out.put("versao", estado.versao());
        out.put("pavimentos", m.pavimentosOrdenados());
        out.put("eixos", m.eixos());
        out.put("intersecoesEixos", m.intersecoesDeEixos());
        out.put("camadas", porCamada);
        out.put("liberadaCliente", carregado.liberadaCliente());
        return out;
    }

    // ------------------------------------------------------------------ escrita
    @Transactional
    public ResultadoComando aplicarComando(UUID obraId, PedidoComando pedido) {
        exigirEdicao(obraId);
        var u = contexto.exigir();
        var carregado = carregarOuCriar(obraId);
        var versaoAtual = repositorio.travarVersao(obraId);
        long base = pedido.versaoBase() == null ? versaoAtual : pedido.versaoBase();
        if (base != versaoAtual) {
            throw new ExcecaoDominio.Conflito("Modelo avançou para a versão " + versaoAtual + " (base " + base + "). Recarregue o diff.");
        }
        var cmd = montarComando(pedido, u, base);
        var papeis = u.papeisNaObra(obraId);
        var def = br.com.civilconstructorai.notacao.CatalogoComandos.porCodigo(cmd.codigo()).orElse(null);
        if (def != null && !def.papeis().isEmpty() && papeis.stream().noneMatch(p -> p.escopoEmpresa() || def.papeis().contains(p.name()))) {
            throw new ExcecaoDominio.AcessoNegado("Só " + String.join(" ou ", def.papeis()) + " pode usar " + def.nomePtBr() + " nesta obra.");
        }
        var atual = new ModeloBim(carregado.estado());
        atual.setVersao(versaoAtual);
        var r = auditor.auditar(obraId, atual, cmd, pedido.pavimentoId());
        r.rascunho().setVersao(versaoAtual + 1);
        r.diff().versaoNova = versaoAtual + 1;
        var vi = FormatadorVi.formatar(r.comandoFinal().codigo(), r.comandoFinal().args());
        repositorio.salvarAplicacao(obraId, atual.estado(), r.rascunho().estado(), r.diff(), r.comandoFinal(), vi, r.relatorio());
        repositorio.salvarCiclo(r.ciclo());
        return new ResultadoComando(r.comandoFinal().id(), vi, versaoAtual + 1, r.diff(), r.relatorio());
    }

    /** lote atômico (COPIAR múltiplo, MATRIZ, comandos da IA): falha em um item reverte todos */
    @Transactional
    public List<ResultadoComando> aplicarLote(UUID obraId, List<PedidoComando> pedidos) {
        var out = new ArrayList<ResultadoComando>();
        Long base = pedidos.isEmpty() ? null : pedidos.get(0).versaoBase();
        for (var p : pedidos) {
            var r = aplicarComando(obraId, new PedidoComando(p.pavimentoId(), p.codigo(), p.args(), p.vi(), p.origem(), base));
            base = r.versao();
            out.add(r);
        }
        return out;
    }

    /** RF01 CA-04: desfazer gera uma nova versão com o estado anterior ao comando (o histórico não é apagado) */
    @Transactional
    public ResultadoComando desfazer(UUID obraId, UUID comandoId) {
        exigirEdicao(obraId);
        var u = contexto.exigir();
        var registroCmd = repositorio.comando(obraId, comandoId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Comando"));
        var versaoAtual = repositorio.travarVersao(obraId);
        var carregado = repositorio.carregar(obraId).orElseThrow();
        var atual = new ModeloBim(carregado.estado());
        atual.setVersao(versaoAtual);
        var alvo = new ModeloBim(registroCmd.estadoAnterior());
        var diff = diferenca(atual, alvo);
        alvo.setVersao(versaoAtual + 1);
        diff.versaoNova = versaoAtual + 1;
        var inverso = new ComandoVi(Ids.novo(), "DESFAZER", Map.of("comando", comandoId.toString()), OrigemComando.CANVAS, versaoAtual, u.contaId(), Instant.now());
        var relatorio = new RelatorioAuditoria(new RelatorioAuditoria.Laco(true, List.of()), new RelatorioAuditoria.Laco2(true, List.of(), List.of()), List.of(), true, 0);
        var vi = FormatadorVi.formatar("DESFAZER", inverso.args());
        repositorio.salvarAplicacao(obraId, atual.estado(), alvo.estado(), diff, inverso, vi, relatorio);
        return new ResultadoComando(inverso.id(), vi, versaoAtual + 1, diff, relatorio);
    }

    /** RF03: mover um eixo reancora os pilares (dentro da tolerância) e reaudita o modelo inteiro */
    @Transactional
    public ResultadoComando moverEixo(UUID obraId, UUID eixoId, double novaPosicao) {
        exigirEdicao(obraId);
        var u = contexto.exigir();
        var versaoAtual = repositorio.travarVersao(obraId);
        var carregado = repositorio.carregar(obraId).orElseThrow();
        var atual = new ModeloBim(carregado.estado());
        atual.setVersao(versaoAtual);
        var rascunho = atual.copia();
        var movidos = rascunho.moverEixo(eixoId, novaPosicao, 0.1);
        var diff = new DiffModelo();
        diff.alterados.addAll(movidos);
        rascunho.eixos().stream().filter(e -> e.id().equals(eixoId)).findFirst().ifPresent(diff.eixosAlterados::add);
        var relatorio = new Auditor(new ServicoGeometriaLocal()).auditar(rascunho, diff, Auditor.Opcoes.padrao());
        if (!relatorio.aprovado()) {
            throw new ComandoReprovadoException(relatorio);
        }
        var cmd = new ComandoVi(Ids.novo(), "MOVER_EIXO", Map.of("eixo", eixoId.toString(), "posicao", novaPosicao), OrigemComando.CANVAS, versaoAtual, u.contaId(), Instant.now());
        rascunho.setVersao(versaoAtual + 1);
        diff.versaoNova = versaoAtual + 1;
        var vi = FormatadorVi.formatar(cmd.codigo(), cmd.args());
        repositorio.salvarAplicacao(obraId, atual.estado(), rascunho.estado(), diff, cmd, vi, relatorio);
        return new ResultadoComando(cmd.id(), vi, versaoAtual + 1, diff, relatorio);
    }

    /** RF02: texto natural → Claude (structured outputs, rota comando-vi) → comandos VI validados → lote com origem IA */
    @Transactional
    public List<ResultadoComando> textoNatural(UUID obraId, UUID pavimentoId, String texto) {
        exigirEdicao(obraId);
        var carregado = carregarOuCriar(obraId);
        var m = new ModeloBim(carregado.estado());
        var contextoModelo = Map.of(
                "pavimentos", String.valueOf(m.pavimentosOrdenados().stream().map(p -> p.nome() + "@" + p.elevacao()).toList()),
                "eixos", String.valueOf(m.eixos().stream().map(e -> e.nome() + "=" + e.posicao()).toList()),
                "materiais", String.valueOf(br.com.civilconstructorai.notacao.TabelaA.MATERIAIS.stream().map(x -> x.materialId()).toList()));
        var r = llm.gerarEstruturado(RotaIa.COMANDO_VI, PROMPT_COMANDO_VI, Sanitizador.texto(texto), AuditorService.SCHEMA_COMANDO, contextoModelo);
        if (!r.completa()) {
            throw new ExcecaoDominio.RegraDeNegocio("ia_recusou", "O modelo de IA não devolveu comandos (" + r.stopReason() + "). Tente reformular.");
        }
        var pedidos = new ArrayList<PedidoComando>();
        try {
            var comandos = mapper.readTree(r.conteudoJson()).get("comandos");
            for (var c : comandos) {
                Map<String, Object> args = mapper.convertValue(c.get("args"), new tools.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
                pedidos.add(new PedidoComando(pavimentoId, c.get("codigo").asString().toUpperCase(), ModeloBim.resolverCoordenadas(args), null, OrigemComando.IA, null));
            }
        } catch (RuntimeException e) {
            throw new ExcecaoDominio.RegraDeNegocio("ia_resposta_invalida", "A resposta da IA não segue o esquema de comandos VI.");
        }
        if (pedidos.isEmpty()) {
            throw new ExcecaoDominio.RegraDeNegocio("ia_sem_comandos", "A IA não identificou nenhum comando no texto.");
        }
        return aplicarLote(obraId, pedidos);
    }

    @Transactional
    public void liberarCliente(UUID obraId, boolean liberar) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.DONO, PapelObra.ADMIN, PapelObra.ENGENHEIRO);
        var u = contexto.exigir();
        var carregado = carregarOuCriar(obraId);
        repositorio.liberarCliente(obraId, liberar, carregado.versao());
        registro.registrar(RegistroOperacoes.Operacao.de(liberar ? "MODELO_LIBERADO_CLIENTE" : "MODELO_RECOLHIDO_CLIENTE", u.contaId(), u.empresaAtivaId(), obraId, "modelo_bim", carregado.modeloId()));
    }

    /** verificação de conclusão (análise, prancha, exportação, EM_ANDAMENTO): envoltória aberta reprova */
    @Transactional(readOnly = true)
    public List<RelatorioAuditoria.Violacao> verificarConclusao(UUID obraId, UUID pavimentoId) {
        acesso.exigirMembro(obraId);
        var m = new ModeloBim(carregarOuCriarSomenteLeitura(obraId).estado());
        var pav = pavimentoId != null ? pavimentoId : m.pavimentoBase().id();
        return auditor.verificarConclusao(m, pav);
    }

    public void exigirConclusao(UUID obraId, UUID pavimentoId) {
        var violacoes = verificarConclusao(obraId, pavimentoId).stream().filter(v -> v.severidade() == Enums.Severidade.VIOLACAO).toList();
        if (!violacoes.isEmpty()) {
            throw new ComandoReprovadoException(new RelatorioAuditoria(new RelatorioAuditoria.Laco(true, List.of()), new RelatorioAuditoria.Laco2(false, violacoes, List.of()), List.of(), false, 0));
        }
    }

    /** RF17: alertas de transição (pilar sem apoio inferior) por pavimento, com confirmação do engenheiro */
    @Transactional
    public List<AlertaTransicao> alertasTransicao(UUID obraId) {
        acesso.exigirMembro(obraId);
        var m = new ModeloBim(carregarOuCriar(obraId).estado());
        var existentes = repositorio.alertas(obraId);
        var out = new ArrayList<>(existentes);
        for (var pav : m.pavimentosOrdenados()) {
            for (var v : auditor.verificarConclusao(m, pav.id())) {
                if (!v.codigo().equals("Z-SEM_APOIO_INFERIOR") || v.elementoRef() == null) {
                    continue;
                }
                boolean ja = existentes.stream().anyMatch(a -> a.elementoId().equals(v.elementoRef()));
                if (!ja) {
                    var novo = new AlertaTransicao(Ids.novo(), obraId, v.elementoRef(), "SEM_APOIO_INFERIOR", "ABERTO", v.mensagem(), null, Instant.now());
                    repositorio.salvarAlerta(novo);
                    out.add(novo);
                }
            }
        }
        return out;
    }

    @Transactional
    public AlertaTransicao decidirAlerta(UUID obraId, UUID alertaId, String decisao, String justificativa) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var a = repositorio.alerta(obraId, alertaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Alerta"));
        if (!a.estado().equals("ABERTO")) {
            throw new ExcecaoDominio.Conflito("Alerta já decidido (" + a.estado() + ").");
        }
        var novoEstado = switch (decisao.toUpperCase()) {
            case "CONFIRMAR" -> "CONFIRMADO";
            case "REJEITAR" -> "REJEITADO";
            case "RESOLVER" -> "RESOLVIDO";
            default -> throw new ExcecaoDominio.Validacao("decisao", "Use CONFIRMAR, REJEITAR ou RESOLVER.");
        };
        if (novoEstado.equals("CONFIRMADO") && (justificativa == null || justificativa.isBlank())) {
            throw new ExcecaoDominio.Validacao("justificativa", "Confirmar uma transição exige justificativa técnica.");
        }
        var decidido = new AlertaTransicao(a.id(), a.obraId(), a.elementoId(), a.tipo(), novoEstado, a.mensagem(), justificativa, a.criadoEm());
        repositorio.salvarAlerta(decidido);
        var u = contexto.exigir();
        registro.registrar(RegistroOperacoes.Operacao.de("ALERTA_TRANSICAO_" + novoEstado, u.contaId(), u.empresaAtivaId(), obraId, "alerta_transicao", alertaId));
        return decidido;
    }

    // ------------------------------------------------------------------ auxiliares
    private void exigirEdicao(UUID obraId) {
        acesso.exigirMembro(obraId);
        if (!acesso.editaModelo(obraId)) {
            throw new ExcecaoDominio.AcessoNegado("Só arquiteto(a), engenheiro(a) ou administrador(a) da obra pode editar o modelo.");
        }
    }

    private ModeloCarregado carregarOuCriar(UUID obraId) {
        return repositorio.carregar(obraId).orElseGet(() -> repositorio.criar(obraId));
    }

    private ModeloCarregado carregarOuCriarSomenteLeitura(UUID obraId) {
        return repositorio.carregar(obraId).orElseGet(() -> new ModeloCarregado(null, obraId, 0, false, null, new ModeloBim().estado()));
    }

    private ComandoVi montarComando(PedidoComando p, UsuarioAtual u, long base) {
        String codigo = p.codigo();
        Map<String, Object> args = p.args() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(p.args());
        if (p.vi() != null && !p.vi().isBlank()) {
            var prog = ParserVi.parse(p.vi());
            if (!prog.valido()) {
                throw new ExcecaoDominio.Validacao(prog.erros().stream().map(e -> new ExcecaoDominio.Validacao.Erro(e.codigo() + "@" + e.linha() + ":" + e.coluna(), e.mensagem())).toList());
            }
            if (prog.comandos().size() != 1) {
                throw new ExcecaoDominio.Validacao("vi", "Envie um comando por requisição (use /comandos/lote para vários).");
            }
            codigo = prog.comandos().get(0).codigo();
            args = new LinkedHashMap<>(prog.comandos().get(0).args());
        }
        if (codigo == null || codigo.isBlank()) {
            throw new ExcecaoDominio.Validacao("codigo", "Informe o código da função VI ou o texto VI.");
        }
        codigo = Sanitizador.identificador(codigo).toUpperCase();
        if (args.get("mat") instanceof String s) {
            args.put("mat", Sanitizador.identificador(s).toUpperCase());
        }
        if (args.get("materialId") instanceof String s) {
            args.put("materialId", Sanitizador.identificador(s).toUpperCase());
        }
        var origem = p.origem() == null ? OrigemComando.CANVAS : p.origem();
        return new ComandoVi(Ids.novo(), codigo, ModeloBim.resolverCoordenadas(args), origem, base, u.contaId(), Instant.now());
    }

    static DiffModelo diferenca(ModeloBim de, ModeloBim para) {
        var diff = new DiffModelo();
        var antes = de.idsElementos();
        var depois = para.idsElementos();
        for (var e : para.elementos()) {
            (antes.contains(e.getId()) ? diff.alterados : diff.criados).add(e);
        }
        antes.stream().filter(id -> !depois.contains(id)).forEach(diff.removidos::add);
        diff.pavimentosCriados.addAll(para.pavimentos());
        diff.eixosCriados.addAll(para.eixos());
        diff.versaoNova = para.versao();
        return diff;
    }

    static final String PROMPT_COMANDO_VI = """
            Você traduz pedidos em português de projetistas de edificações para comandos da Notação VI
            (Tabela B): PAREDE(p1,p2,esp,h,mat), PILAR(centro,b,h,mat), VIGA(p1,p2,b,h,mat), LAJE(contorno,h),
            PORTA(pos,larg,alt), JANELA(pos,larg,alt,peitoril), EIXO(p1,p2,nome), MALHA(nx,dx,ny,dy),
            PAVIMENTO(nome,z), LINHA, POLILINHA(pontos,fechar), CIRCULO, RETANGULO, MOVER(sel,base,destino),
            COPIAR, APAGAR(sel). Coordenadas em metros, pontos como [x,y]. Use os eixos e materiais do contexto.
            Devolva SOMENTE o JSON {"comandos":[{"codigo":..., "args":{...}}]}. Nunca inclua dados pessoais.""";
}
