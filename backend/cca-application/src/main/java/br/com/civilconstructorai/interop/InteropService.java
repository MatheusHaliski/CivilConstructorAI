package br.com.civilconstructorai.interop;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.EstadoJob;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Ids;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.interop.InteropRepositorio.EstadoPareamento;
import br.com.civilconstructorai.interop.InteropRepositorio.Exportacao;
import br.com.civilconstructorai.interop.InteropRepositorio.Formato;
import br.com.civilconstructorai.interop.InteropRepositorio.Pareamento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.ModeloRepositorio;
import br.com.civilconstructorai.modelo.ModeloService;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import br.com.civilconstructorai.comum.ContextoTenant;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF15 · exportação IFC 4.3 (escritor próprio) e STEP AP242 (kernel do serviço Python, stub local), importação
 * IFC de referência e pareamento com o pyRevit (fase 2, DN-06): código de uso único de 6 dígitos, 10 min, troca
 * por token de dispositivo guardado só como hash.
 */
@Service
public class InteropService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InteropRepositorio repo;
    private final ModeloRepositorio modelos;
    private final ModeloService modeloService;
    private final ArmazenamentoArquivos arquivos;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final TransactionTemplate tx;

    public InteropService(InteropRepositorio repo, ModeloRepositorio modelos, ModeloService modeloService, ArmazenamentoArquivos arquivos,
                          AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, java.util.Optional<Relogio> relogio, PlatformTransactionManager tm) {
        this.tx = new TransactionTemplate(tm);
        this.repo = repo;
        this.modelos = modelos;
        this.modeloService = modeloService;
        this.arquivos = arquivos;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio.orElse(Relogio.sistema());
    }

    // ------------------------------------------------------------------ exportação
    @Transactional
    public Exportacao solicitarExportacao(UUID obraId, Formato formato) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var carregado = modelos.carregar(obraId).orElseThrow(() -> new ExcecaoDominio.RegraDeNegocio("modelo_vazio", "A obra ainda não tem modelo para exportar."));
        var m = new ModeloBim(carregado.estado());
        for (var pav : m.pavimentosOrdenados()) {
            if (!m.elementosDo(pav.id()).isEmpty()) {
                modeloService.exigirConclusao(obraId, pav.id());
            }
        }
        var e = new Exportacao(Ids.novo(), obraId, formato, EstadoJob.PENDENTE, carregado.versao(), null, null, u.contaId(), relogio.agora(), null);
        repo.salvar(e);
        return e;
    }

    @Scheduled(fixedDelayString = "${app.interop.intervalo-ms:3000}")
    public void processarPendentes() {
        try {
            ContextoTenant.comoSistema(() -> tx.execute(st -> {
                for (var e : repo.exportacoesPendentes(10)) {
                    executar(e);
                }
                return null;
            }));
        } catch (Exception e) {
            throw new ExcecaoDominio.Conflito("Falha ao processar exportações: " + e.getMessage());
        }
    }

    @Transactional
    public Exportacao executar(Exportacao e) {
        repo.salvar(e.com(EstadoJob.EM_EXECUCAO, null, null));
        try {
            var carregado = modelos.carregar(e.obraId()).orElseThrow();
            var estado = carregado.versao() == e.versaoModelo() ? carregado.estado() : modelos.estadoNaVersao(e.obraId(), e.versaoModelo()).orElse(carregado.estado());
            var m = new ModeloBim(estado);
            String conteudo;
            String ext;
            String tipo;
            if (e.formato() == Formato.IFC4X3) {
                conteudo = EscritorIfc.escrever(m, "Obra " + e.obraId().toString().substring(0, 8), "Responsável técnico", relogio.agora());
                ext = ".ifc";
                tipo = "application/x-step";
            } else {
                // STEP AP242 depende do kernel CadQuery/OpenCASCADE (serviço Python); aqui vai um cabeçalho válido com os sólidos listados
                conteudo = stepStub(m);
                ext = ".step";
                tipo = "application/step";
            }
            var chave = "obras/" + e.obraId() + "/exportacoes/" + e.id() + ext;
            arquivos.gravar(chave, conteudo.getBytes(StandardCharsets.UTF_8), tipo);
            var ok = e.com(EstadoJob.CONCLUIDO, chave, null);
            repo.salvar(ok);
            return ok;
        } catch (RuntimeException ex) {
            var falhou = e.com(EstadoJob.FALHOU, null, ex.getMessage());
            repo.salvar(falhou);
            return falhou;
        }
    }

    private static String stepStub(ModeloBim m) {
        var sb = new StringBuilder("ISO-10303-21;\nHEADER;\nFILE_DESCRIPTION(('CivilConstructorAI STEP AP242 — solidos a gerar pelo kernel'),'2;1');\nFILE_SCHEMA(('AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF'));\nENDSEC;\nDATA;\n");
        int n = 0;
        for (var e : m.elementos()) {
            if (e.getTipo().construtivo()) {
                sb.append('#').append(++n).append("=PRODUCT('").append(e.getRotulo() != null ? e.getRotulo() : m.rotuloAutomatico(e)).append("','").append(e.getTipo()).append("','',());\n");
            }
        }
        return sb.append("ENDSEC;\nEND-ISO-10303-21;\n").toString();
    }

    @Transactional(readOnly = true)
    public List<Exportacao> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return repo.exportacoes(obraId);
    }

    @Transactional(readOnly = true)
    public Exportacao buscar(UUID obraId, UUID id) {
        acesso.exigirMembro(obraId);
        return repo.exportacao(obraId, id).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Exportação"));
    }

    @Transactional(readOnly = true)
    public URI download(UUID obraId, UUID id) {
        var e = buscar(obraId, id);
        if (e.estado() != EstadoJob.CONCLUIDO) {
            throw new ExcecaoDominio.Conflito("Exportação ainda não concluída (" + e.estado() + ").");
        }
        return arquivos.presignDownload(e.chave(), Duration.ofMinutes(15), "modelo-" + id + (e.formato() == Formato.IFC4X3 ? ".ifc" : ".step"));
    }

    @Transactional(readOnly = true)
    public LeitorIfc.Resumo importarReferencia(UUID obraId, byte[] conteudo) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        if (conteudo == null || conteudo.length == 0) {
            throw new ExcecaoDominio.Validacao("arquivo", "Envie um arquivo IFC.");
        }
        if (conteudo.length > 32L * 1024 * 1024) {
            throw new ExcecaoDominio.Validacao("arquivo", "IFC de referência limitado a 32 MB.");
        }
        return LeitorIfc.ler(conteudo);
    }

    // ------------------------------------------------------------------ pareamento Revit (fase 2)
    public record CodigoPareamento(UUID pareamentoId, String codigo, Instant expiraEm) {
    }

    @Transactional
    public CodigoPareamento gerarCodigo(UUID obraId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        var p = new Pareamento(Ids.novo(), obraId, u.contaId(), EstadoPareamento.AGUARDANDO_CODIGO, hash(codigo), null, null, relogio.agora().plus(Duration.ofMinutes(10)), relogio.agora(), null);
        repo.salvar(p);
        return new CodigoPareamento(p.id(), codigo, p.expiraEm());
    }

    public record TokenDispositivo(UUID pareamentoId, String token) {
    }

    /** o pyRevit troca o código de uso único por um token de dispositivo (guardado só como hash) */
    @Transactional
    public TokenDispositivo parear(String codigo, String dispositivo) {
        var p = repo.pareamentoPorCodigo(hash(codigo == null ? "" : codigo.trim())).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Código de pareamento inválido ou já usado."));
        if (p.estado() != EstadoPareamento.AGUARDANDO_CODIGO) {
            throw new ExcecaoDominio.AcessoNegado("Código de pareamento inválido ou já usado.");
        }
        if (relogio.agora().isAfter(p.expiraEm())) {
            repo.salvar(p.com(EstadoPareamento.EXPIRADO, null, null, null));
            throw new ExcecaoDominio.AcessoNegado("Código de pareamento expirado. Gere outro no app.");
        }
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repo.salvar(p.com(EstadoPareamento.PAREADO, hash(token), dispositivo == null ? "Revit" : dispositivo, relogio.agora()));
        registro.registrar(RegistroOperacoes.Operacao.de("REVIT_PAREADO", p.contaId(), null, p.obraId(), "pareamento_revit", p.id()));
        return new TokenDispositivo(p.id(), token);
    }

    /** comandos VI aplicados de uma versão para o pyRevit reexecutar na Revit API */
    @Transactional(readOnly = true)
    public Map<String, Object> comandosParaRevit(String token, long desdeVersao) {
        var p = repo.pareamentoPorToken(hash(token == null ? "" : token)).filter(x -> x.estado() == EstadoPareamento.PAREADO)
                .orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Token de dispositivo inválido ou revogado."));
        var cmds = modelos.comandos(p.obraId(), desdeVersao).stream().map(c -> Map.of("id", c.id(), "vi", c.vi(), "versao", c.versaoNova(), "origem", c.origem())).toList();
        var versaoAtual = modelos.carregar(p.obraId()).map(x -> x.versao()).orElse(0L);
        return Map.of("obraId", p.obraId(), "versaoAtual", versaoAtual, "comandos", cmds);
    }

    @Transactional
    public Pareamento revogar(UUID obraId, UUID pareamentoId) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        var p = repo.pareamento(obraId, pareamentoId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Pareamento"));
        var r = p.com(EstadoPareamento.REVOGADO, null, p.dispositivo(), p.pareadoEm());
        repo.salvar(r);
        return r;
    }

    @Transactional(readOnly = true)
    public List<Pareamento> pareamentos(UUID obraId) {
        acesso.exigirMembro(obraId);
        return repo.pareamentos(obraId);
    }

    static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
