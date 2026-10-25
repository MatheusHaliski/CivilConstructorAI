package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.lgpd.Portas.ChavesUsuario;
import br.com.civilconstructorai.lgpd.Portas.HashProtegido;
import br.com.civilconstructorai.lgpd.Portas.RegistroAtividades;
import br.com.civilconstructorai.lgpd.Portas.SessoesDoUsuario;
import br.com.civilconstructorai.lgpd.Repositorios.ExportacaoRepository;
import br.com.civilconstructorai.lgpd.Repositorios.ReciboEliminacaoRepository;
import br.com.civilconstructorai.lgpd.Repositorios.RetencaoLegalRepository;
import br.com.civilconstructorai.lgpd.Repositorios.SagaEliminacaoRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Passos da saga de eliminação, cada um em transação própria (REQUIRES_NEW) — bean separado para a
 * orquestração em {@link SagaEliminacaoService} passar pelo proxy transacional.
 */
@Component
public class PassosEliminacao {

    private final SagaEliminacaoRepository sagas;
    private final RetencaoLegalRepository retencoes;
    private final ReciboEliminacaoRepository recibos;
    private final ExportacaoRepository exportacoes;
    private final ContaLgpdDao contas;
    private final ConsentimentoService consentimentos;
    private final ArmazenamentoArquivos arquivos;
    private final RegistroAtividades atividades;
    private final SessoesDoUsuario sessoes;
    private final ChavesUsuario chaves;
    private final HashProtegido hash;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final ObjectMapper mapper;

    public PassosEliminacao(SagaEliminacaoRepository sagas, RetencaoLegalRepository retencoes, ReciboEliminacaoRepository recibos, ExportacaoRepository exportacoes,
                            ContaLgpdDao contas, ConsentimentoService consentimentos, ArmazenamentoArquivos arquivos, RegistroAtividades atividades,
                            SessoesDoUsuario sessoes, ChavesUsuario chaves, HashProtegido hash, RegistroOperacoes registro, Relogio relogio, ObjectMapper mapper) {
        this.sagas = sagas;
        this.retencoes = retencoes;
        this.recibos = recibos;
        this.exportacoes = exportacoes;
        this.contas = contas;
        this.consentimentos = consentimentos;
        this.arquivos = arquivos;
        this.atividades = atividades;
        this.sessoes = sessoes;
        this.chaves = chaves;
        this.hash = hash;
        this.registro = registro;
        this.relogio = relogio;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaEliminacao iniciarOuRetomar(UUID contaId) {
        var saga = sagas.findByContaId(contaId).orElseGet(() -> sagas.save(new SagaEliminacao(contaId, hash.hash("eliminacao", contaId.toString()))));
        if (saga.getEstado() != SagaEliminacao.Estado.CONCLUIDA) {
            saga.executando();
            saga = sagas.save(saga);
        }
        return saga;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SagaEliminacao marcarFalha(UUID sagaId, int passo, RuntimeException e) {
        var saga = sagas.findById(sagaId).orElseThrow();
        saga.falhou("passo " + passo + ": " + e.getClass().getSimpleName());
        return sagas.save(saga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executarPasso(UUID sagaId, int passo) {
        var saga = sagas.findById(sagaId).orElseThrow();
        var contaId = saga.getContaId();
        switch (passo) {
            case SagaEliminacao.PASSO_ANONIMIZAR -> {
                // o que a lei obriga a guardar fica registrado ANTES de anonimizar
                for (var rt : contas.responsabilidadesTecnicas(contaId)) {
                    var dados = Map.of("nome", String.valueOf(rt.get("nome")), "registro", String.valueOf(rt.get("registro_profissional")),
                            "uf", String.valueOf(rt.get("registro_uf")), "artRrt", String.valueOf(rt.get("art_rrt")));
                    retencoes.save(new RetencaoLegal(saga.getId(), "obra.responsavel_tecnico", (UUID) rt.get("obra_id"),
                            "Lei 5.194/66 e Lei 6.496/77 (ART) · LGPD Art. 16, I", null, mapper.writeValueAsString(dados)));
                }
                retencoes.save(new RetencaoLegal(saga.getId(), "consent_event", contaId, "LGPD Art. 8º §5º (prova do consentimento)",
                        relogio.agora().plusSeconds(5L * 365 * 24 * 3600), null));
                // chaves S3 a remover só depois deste commit (passo 2)
                var chavesS3 = new ArrayList<String>();
                chavesS3.add("perfil/" + contaId + "/foto.jpg");
                var exps = exportacoes.findByTitularIdOrderByCriadoEmDesc(contaId);
                exps.forEach(e -> { if (e.getChaveS3() != null) chavesS3.add(e.getChaveS3()); });
                exportacoes.deleteAll(exps);
                saga.registrarChavesS3(mapper.writeValueAsString(chavesS3));
                consentimentos.encerrarTodos(contaId, hash.hash("ip", "sistema"));
                contas.anonimizar(contaId);
            }
            case SagaEliminacao.PASSO_S3 -> {
                List<String> chavesS3 = mapper.readValue(saga.getChavesS3(), mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                chavesS3.forEach(arquivos::remover);
            }
            case SagaEliminacao.PASSO_CASSANDRA -> atividades.limparUsuario(contaId);
            case SagaEliminacao.PASSO_VALKEY -> sessoes.encerrarTodas(contaId);
            case SagaEliminacao.PASSO_CHAVE -> chaves.destruir(contaId);
            case SagaEliminacao.PASSO_RECIBO -> {
                var bases = new LinkedHashMap<String, String>();
                retencoes.findBySagaId(saga.getId()).forEach(r -> bases.put(r.getEntidade(), r.getBaseLegal()));
                recibos.save(new ReciboEliminacao(saga.getId(), saga.getContaHash(), "[1,2,3,4,5,6]", mapper.writeValueAsString(bases)));
                contas.marcarEliminada(contaId);
                saga.concluir(relogio.agora());
                registro.registrar(new RegistroOperacoes.Operacao("CONTA_ELIMINADA", null, null, null, "saga_eliminacao", saga.getId(), Map.of("contaHash", saga.getContaHash())));
            }
            default -> throw new IllegalStateException("passo inválido " + passo);
        }
        if (passo != SagaEliminacao.PASSO_RECIBO) {
            saga.passoConcluido(passo);
        }
        sagas.save(saga);
    }
}
