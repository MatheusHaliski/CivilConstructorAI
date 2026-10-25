package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.EstadoJob;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.email.EnviadorEmail;
import br.com.civilconstructorai.lgpd.Portas.ChavesUsuario;
import br.com.civilconstructorai.lgpd.Repositorios.ExportacaoRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF25 · exportação em job assíncrono. Fluxo: {@code POST /me/exportacoes} (sudo) → PENDENTE → job gera a senha
 * na hora de cifrar, grava-a cifrada com a DEK do titular → CONCLUIDO (PRONTA) → primeira consulta devolve
 * {@code senhaZip} + URL pré-assinada (15 min) e apaga a senha (fica só o hash).
 */
@Service
public class ExportacaoService {

    private static final Logger log = LoggerFactory.getLogger(ExportacaoService.class);
    public static final int LIMITE_DIARIO = 3;
    public static final Duration VALIDADE_OBJETO = Duration.ofHours(24);

    public record Estado(UUID id, EstadoJob estado, String urlDownload, String senhaZip, Long tamanhoBytes, java.time.Instant expiraEm, String erro) {
    }

    private final ExportacaoRepository exportacoes;
    private final ExportadorTitular exportador;
    private final ChavesUsuario chaves;
    private final ArmazenamentoArquivos arquivos;
    private final EnviadorEmail email;
    private final ContaLgpdDao contas;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;

    public ExportacaoService(ExportacaoRepository exportacoes, ExportadorTitular exportador, ChavesUsuario chaves, ArmazenamentoArquivos arquivos,
                             EnviadorEmail email, ContaLgpdDao contas, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio) {
        this.exportacoes = exportacoes;
        this.exportador = exportador;
        this.chaves = chaves;
        this.arquivos = arquivos;
        this.email = email;
        this.contas = contas;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
    }

    @Transactional
    public UUID solicitar() {
        var u = contexto.exigir();
        if (!u.sudoAtivo()) {
            throw new ExcecaoDominio.SudoNecessario();
        }
        var agora = relogio.agora();
        if (exportacoes.existsByTitularIdAndEstadoIn(u.contaId(), List.of(EstadoJob.PENDENTE, EstadoJob.EM_EXECUCAO))) {
            throw new ExcecaoDominio.LimiteExcedido(60);
        }
        if (exportacoes.countByTitularIdAndCriadoEmAfter(u.contaId(), agora.minus(1, ChronoUnit.DAYS)) >= LIMITE_DIARIO) {
            throw new ExcecaoDominio.LimiteExcedido(Duration.ofHours(24).toSeconds());
        }
        if (!chaves.existe(u.contaId())) {
            chaves.criarParaConta(u.contaId());
        }
        var e = exportacoes.save(new Exportacao(u.contaId()));
        registro.registrar(RegistroOperacoes.Operacao.de("EXPORTACAO_SOLICITADA", u.contaId(), u.empresaAtivaId(), null, "exportacao", e.getId()));
        return e.getId();
    }

    /** consulta pelo titular; a primeira consulta após PRONTA entrega a senha uma única vez */
    @Transactional
    public Estado consultar(UUID id) {
        var u = contexto.exigir();
        var e = exportacoes.findByIdAndTitularId(id, u.contaId()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Exportação"));
        if (!e.pronta()) {
            return new Estado(e.getId(), e.getEstado(), null, null, null, null, e.getErro());
        }
        String senha = null;
        if (e.senhaDisponivel()) {
            senha = new String(chaves.decifrar(u.contaId(), e.consumirSenha(relogio.agora())), StandardCharsets.UTF_8);
            exportacoes.save(e);
        }
        URI url = arquivos.presignDownload(e.getChaveS3(), ArmazenamentoArquivos.VALIDADE_PADRAO, "civilconstructorai-export.zip.cca");
        registro.registrar(RegistroOperacoes.Operacao.de("EXPORTACAO_DOWNLOAD", u.contaId(), u.empresaAtivaId(), null, "exportacao", e.getId()));
        return new Estado(e.getId(), e.getEstado(), url.toString(), senha, e.getTamanhoBytes(), e.getExpiraEm(), null);
    }

    @Transactional(readOnly = true)
    public List<Estado> minhas() {
        var u = contexto.exigir();
        return exportacoes.findByTitularIdOrderByCriadoEmDesc(u.contaId()).stream()
                .map(e -> new Estado(e.getId(), e.getEstado(), null, null, e.getTamanhoBytes(), e.getExpiraEm(), e.getErro())).toList();
    }

    /** chamado pelo job; cada exportação em transação própria */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processar(UUID id) {
        var e = exportacoes.findById(id).orElse(null);
        if (e == null || e.getEstado() != EstadoJob.PENDENTE) {
            return;
        }
        e.iniciar();
        exportacoes.saveAndFlush(e);
        try {
            var agora = relogio.agora();
            var export = exportador.coletar(e.getTitularId(), agora);
            if (!exportador.valido(export)) {
                throw new IllegalStateException("export.json não passou na validação do schema " + ExportadorTitular.SCHEMA);
            }
            var senha = CifraExportacao.gerarSenha();
            var pacote = CifraExportacao.cifrar(exportador.zip(export), senha);
            var chave = "exports/" + e.getTitularId() + "/" + e.getId() + ".zip.cca";
            arquivos.gravar(chave, pacote, "application/octet-stream");
            e.concluir(chave, chaves.cifrar(e.getTitularId(), senha.getBytes(StandardCharsets.UTF_8)), CifraExportacao.hash(senha), pacote.length, agora.plus(VALIDADE_OBJETO));
            exportacoes.save(e);
            registro.registrar(new RegistroOperacoes.Operacao("EXPORTACAO_GERADA", e.getTitularId(), null, null, "exportacao", e.getId(), Map.of("bytes", String.valueOf(pacote.length))));
            contas.buscar(e.getTitularId()).ifPresent(c -> email.enviar(c.email(), "Sua cópia de dados está pronta",
                    "A exportação dos seus dados ficou pronta. Entre em Seus dados › Exportações para ver a senha (exibida uma única vez) e baixar o arquivo. O link vale por 24 h."));
        } catch (RuntimeException ex) {
            log.warn("exportação {} falhou: {}", e.getId(), ex.getClass().getSimpleName());
            e.falhar(ex.getMessage());
            exportacoes.save(e);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> pendentes() {
        return exportacoes.findByEstadoOrderByCriadoEmAsc(EstadoJob.PENDENTE).stream().map(Exportacao::getId).toList();
    }

    /** objetos vencidos (lifecycle de 24 h também cobre no bucket) */
    @Transactional
    public void expirarVencidas() {
        var agora = relogio.agora();
        for (var e : exportacoes.findByEstadoOrderByCriadoEmAsc(EstadoJob.CONCLUIDO)) {
            if (e.getExpiraEm() != null && e.getExpiraEm().isBefore(agora)) {
                arquivos.remover(e.getChaveS3());
                try {
                    EstadoJob.MAQUINA.transicionar(e.getEstado(), EstadoJob.OBSOLETO);
                } catch (RuntimeException ignored) {
                    // já obsoleta
                }
                exportacoes.delete(e);
            }
        }
    }
}
