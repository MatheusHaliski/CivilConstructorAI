package br.com.civilconstructorai.prancha;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.ModeloRepositorio;
import br.com.civilconstructorai.modelo.ModeloService;
import br.com.civilconstructorai.prancha.PranchaRepositorio.Estado;
import br.com.civilconstructorai.prancha.PranchaRepositorio.Prancha;
import br.com.civilconstructorai.prancha.PranchaRepositorio.Tipo;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import br.com.civilconstructorai.comum.ContextoTenant;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF14 · pranchas: solicitação cria {@code Prancha} EM_GERACAO vinculada à versão do modelo; o job gera SVG e
 * DXF determinísticos, grava no S3 e passa a GERADA. Emitir (EMITIDA) exige profissional aprovador com selo e 2FA
 * e tira a marca d'água. Nova versão do modelo marca pranchas anteriores como OBSOLETA.
 */
@Service
public class PranchaService {

    private final PranchaRepositorio pranchas;
    private final ModeloRepositorio modelos;
    private final ModeloService modeloService;
    private final ArmazenamentoArquivos arquivos;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final TransactionTemplate tx;

    public PranchaService(PranchaRepositorio pranchas, ModeloRepositorio modelos, ModeloService modeloService, ArmazenamentoArquivos arquivos,
                          AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, PlatformTransactionManager tm) {
        this.tx = new TransactionTemplate(tm);
        this.pranchas = pranchas;
        this.modelos = modelos;
        this.modeloService = modeloService;
        this.arquivos = arquivos;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
    }

    @Transactional
    public Prancha solicitar(UUID obraId, UUID pavimentoId, Tipo tipo, String escala) {
        acesso.exigirMembro(obraId);
        if (tipo == Tipo.PLANTA_FORMAS) {
            acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        } else {
            acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        }
        var u = contexto.exigir();
        var carregado = modelos.carregar(obraId).orElseThrow(() -> new ExcecaoDominio.RegraDeNegocio("modelo_vazio", "A obra ainda não tem modelo para gerar prancha."));
        var m = new ModeloBim(carregado.estado());
        var pav = pavimentoId != null ? pavimentoId : m.pavimentoBase().id();
        m.pavimento(pav).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Pavimento"));
        // verificação de conclusão: envoltória aberta reprova a emissão de prancha (RF16 CA-09)
        modeloService.exigirConclusao(obraId, pav);
        var p = new Prancha(Ids.novo(), obraId, pav, tipo, Estado.EM_GERACAO, "A1", escala == null ? "1:50" : escala, carregado.versao(), null, null, null, u.contaId(), null, null, Instant.now(), null);
        pranchas.salvar(p);
        return p;
    }

    /** job: processa pranchas EM_GERACAO como sistema (RLS liberado) numa transação própria */
    @Scheduled(fixedDelayString = "${app.pranchas.intervalo-ms:3000}")
    public void processarPendentes() {
        try {
            ContextoTenant.comoSistema(() -> tx.execute(st -> {
                for (var p : pranchas.pendentes(10)) {
                    gerar(p);
                }
                return null;
            }));
        } catch (Exception e) {
            throw new ExcecaoDominio.Conflito("Falha ao processar pranchas: " + e.getMessage());
        }
    }

    @Transactional
    public Prancha gerar(Prancha p) {
        try {
            var carregado = modelos.carregar(p.obraId()).orElseThrow();
            var estado = carregado.versao() == p.versaoModelo() ? carregado.estado() : modelos.estadoNaVersao(p.obraId(), p.versaoModelo()).orElse(carregado.estado());
            var m = new ModeloBim(estado);
            var pavNome = m.pavimento(p.pavimentoId()).map(x -> x.nome()).orElse("?");
            var carimbo = new GeradorPrancha.Carimbo("Obra " + p.obraId().toString().substring(0, 8), pavNome, "Responsável técnico", p.artRrt(), p.escala(), p.formato(), true, p.versaoModelo());
            boolean formas = p.tipo() == Tipo.PLANTA_FORMAS;
            var svg = GeradorPrancha.svg(m, p.pavimentoId(), formas, carimbo);
            var dxf = GeradorPrancha.dxf(m, p.pavimentoId(), formas);
            var base = "obras/" + p.obraId() + "/pranchas/" + p.id();
            arquivos.gravar(base + ".svg", svg.getBytes(StandardCharsets.UTF_8), "image/svg+xml");
            arquivos.gravar(base + ".dxf", dxf.getBytes(StandardCharsets.UTF_8), "application/dxf");
            var gerada = p.com(Estado.GERADA, base + ".svg", base + ".dxf", null);
            pranchas.salvar(gerada);
            return gerada;
        } catch (RuntimeException e) {
            // a causa vai para o log (antes só a mensagem curta ficava na prancha, sem pista de onde quebrou)
            org.slf4j.LoggerFactory.getLogger(PranchaService.class).warn("Falha ao gerar a prancha {}", p.id(), e);
            var falhou = p.com(Estado.FALHOU, null, null, "Não foi possível gerar a prancha (" + e.getClass().getSimpleName() + "). Tente de novo; se persistir, informe o suporte.");
            pranchas.salvar(falhou);
            return falhou;
        }
    }

    @Transactional(readOnly = true)
    public List<Prancha> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var todas = pranchas.listar(obraId);
        boolean cliente = !u.papeisNaObra(obraId).isEmpty() && u.papeisNaObra(obraId).stream().allMatch(x -> x == PapelObra.CLIENTE || x == PapelObra.FORNECEDOR);
        return cliente ? todas.stream().filter(x -> x.estado() == Estado.EMITIDA).toList() : todas;
    }

    @Transactional(readOnly = true)
    public Prancha buscar(UUID obraId, UUID id) {
        acesso.exigirMembro(obraId);
        var p = pranchas.buscar(obraId, id).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Prancha"));
        var u = contexto.exigir();
        boolean cliente = !u.papeisNaObra(obraId).isEmpty() && u.papeisNaObra(obraId).stream().allMatch(x -> x == PapelObra.CLIENTE || x == PapelObra.FORNECEDOR);
        if (cliente && p.estado() != Estado.EMITIDA) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Prancha");
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Map<String, URI> download(UUID obraId, UUID id) {
        var p = buscar(obraId, id);
        if (p.chaveSvg() == null) {
            throw new ExcecaoDominio.Conflito("Prancha ainda não gerada (" + p.estado() + ").");
        }
        var d = Duration.ofMinutes(15);
        return Map.of("svg", arquivos.presignDownload(p.chaveSvg(), d, "prancha-" + id + ".svg"), "dxf", arquivos.presignDownload(p.chaveDxf(), d, "prancha-" + id + ".dxf"));
    }

    /** emitir: só profissional aprovador com selo verificado e 2FA; regenera sem marca d'água e registra */
    @Transactional
    public Prancha emitir(UUID obraId, UUID id, String artRrt) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        if (!u.podeAprovar(obraId)) {
            throw new ExcecaoDominio.AcessoNegado("Emitir prancha exige profissional habilitado com selo verificado e verificação em duas etapas.");
        }
        var p = pranchas.buscar(obraId, id).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Prancha"));
        if (p.estado() != Estado.GERADA) {
            throw new ExcecaoDominio.TransicaoInvalida("Prancha", p.estado(), Estado.EMITIDA);
        }
        if (artRrt == null || artRrt.isBlank()) {
            throw new ExcecaoDominio.Validacao("artRrt", "Informe o número da ART/RRT.");
        }
        var carregado = modelos.carregar(obraId).orElseThrow();
        var estado = carregado.versao() == p.versaoModelo() ? carregado.estado() : modelos.estadoNaVersao(obraId, p.versaoModelo()).orElse(carregado.estado());
        var m = new ModeloBim(estado);
        var carimbo = new GeradorPrancha.Carimbo("Obra " + obraId.toString().substring(0, 8), m.pavimento(p.pavimentoId()).map(x -> x.nome()).orElse("?"), "Responsável técnico", artRrt, p.escala(), p.formato(), false, p.versaoModelo());
        arquivos.gravar(p.chaveSvg(), GeradorPrancha.svg(m, p.pavimentoId(), p.tipo() == Tipo.PLANTA_FORMAS, carimbo).getBytes(StandardCharsets.UTF_8), "image/svg+xml");
        var emitida = p.emitida(u.contaId(), artRrt, Instant.now());
        pranchas.salvar(emitida);
        registro.registrar(RegistroOperacoes.Operacao.de("PRANCHA_EMITIDA", u.contaId(), u.empresaAtivaId(), obraId, "prancha", id));
        return emitida;
    }
}
