package br.com.civilconstructorai.coordenacao;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.ModeloRepositorio;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordenação (proposta P-COORD-01): roda o {@link DetectorConflitos} na versão atual do modelo e concilia com os
 * conflitos já gravados — novos entram ABERTOS, os que sumiram passam a RESOLVIDO (com a versão da reverificação),
 * os resolvidos que reaparecem são reabertos; ACEITO permanece enquanto o conflito existir.
 */
@Service
@Transactional
public class CoordenacaoService {

    @Repository("conflitoCoordenacaoRepository")
    public interface Conflitos extends JpaRepository<ConflitoCoordenacao, UUID> {
        List<ConflitoCoordenacao> findByObraIdOrderByCriadoEm(UUID obraId);

        Optional<ConflitoCoordenacao> findByIdAndObraId(UUID id, UUID obraId);
    }

    public record Visao(UUID id, String chave, String tipo, String regra, List<UUID> elementos, String mensagem, String evidencia, String estado,
                        String justificativa, long versaoDetectada, long versaoVerificada, UUID decididoPor, java.time.Instant decididoEm) {
    }

    public record Verificacao(long versaoModelo, int detectados, int novos, int resolvidos, int reabertos, List<String> naoVerificado, List<Visao> conflitos) {
    }

    /** o que o detector não cobre — devolvido junto de cada verificação para não ser lido como "sem conflitos" */
    public static final List<String> NAO_VERIFICADO = List.of(
            "FOLGA: exige instalações modeladas (eletrodutos, tubos, bandejas); o modelo atual não tem esses elementos.",
            "FUNCIONAL: acesso a registros, quadros e inspeções depende de instalações modeladas.",
            "Lajes com contorno não retangular: sobreposição avaliada pela caixa envolvente.");

    private final Conflitos conflitos;
    private final ModeloRepositorio modelos;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;
    private final ObjectMapper mapper;
    private final br.com.civilconstructorai.obra.Repositorios.ObraRepository obras;

    public CoordenacaoService(Conflitos conflitos, ModeloRepositorio modelos, AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio,
                              ObjectMapper mapper, br.com.civilconstructorai.obra.Repositorios.ObraRepository obras) {
        this.conflitos = conflitos;
        this.modelos = modelos;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
        this.mapper = mapper;
        this.obras = obras;
    }

    @Transactional(readOnly = true)
    public List<Visao> listar(UUID obraId) {
        acesso.exigirMembro(obraId);
        return conflitos.findByObraIdOrderByCriadoEm(obraId).stream().map(this::visao).toList();
    }

    public Verificacao verificar(UUID obraId) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var obra = obras.findById(obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Obra"));
        var carregado = modelos.carregar(obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Modelo"));
        long versao = carregado.versao();
        var detectados = DetectorConflitos.detectar(new ModeloBim(carregado.estado()));
        var gravados = conflitos.findByObraIdOrderByCriadoEm(obraId);
        var porChave = new java.util.HashMap<String, ConflitoCoordenacao>();
        gravados.forEach(c -> porChave.put(c.getChave(), c));
        var vistos = new HashSet<String>();
        int novos = 0, reabertos = 0, resolvidos = 0;
        for (var d : detectados) {
            vistos.add(d.chave());
            var g = porChave.get(d.chave());
            if (g == null) {
                conflitos.save(new ConflitoCoordenacao(obra.getTenantId(), obraId, d, mapper.writeValueAsString(d.elementos()), versao));
                novos++;
            } else {
                boolean estavaResolvido = g.getEstado() == ConflitoCoordenacao.Estado.RESOLVIDO;
                g.atualizarDeteccao(d, versao);
                if (estavaResolvido) {
                    reabertos++;
                }
            }
        }
        for (var g : gravados) {
            if (!vistos.contains(g.getChave())) {
                var antes = g.getEstado();
                g.naoDetectado(versao, relogio.agora());
                if (antes != g.getEstado()) {
                    resolvidos++;
                }
            }
        }
        registro.registrar(new RegistroOperacoes.Operacao("COORDENACAO_VERIFICADA", u.contaId(), obra.getTenantId(), obraId, "Modelo", carregado.modeloId(),
                Map.of("versao", String.valueOf(versao), "detectados", String.valueOf(detectados.size()), "novos", String.valueOf(novos),
                        "resolvidos", String.valueOf(resolvidos), "reabertos", String.valueOf(reabertos))));
        return new Verificacao(versao, detectados.size(), novos, resolvidos, reabertos, NAO_VERIFICADO,
                conflitos.findByObraIdOrderByCriadoEm(obraId).stream().map(this::visao).toList());
    }

    public Visao decidir(UUID obraId, UUID id, String estado, String justificativa) {
        acesso.exigirMembro(obraId);
        ConflitoCoordenacao.Estado novo;
        try {
            novo = ConflitoCoordenacao.Estado.valueOf(estado);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ExcecaoDominio.Validacao("estado", "Use ABERTO, EM_REVISAO ou ACEITO.");
        }
        if (novo == ConflitoCoordenacao.Estado.ACEITO) {
            acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        }
        var u = contexto.exigir();
        var c = conflitos.findByIdAndObraId(id, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conflito"));
        c.decidir(novo, justificativa, u.contaId(), relogio.agora());
        registro.registrar(new RegistroOperacoes.Operacao("CONFLITO_DECIDIDO", u.contaId(), c.getTenantId(), obraId, "ConflitoCoordenacao", c.getId(),
                Map.of("estado", novo.name(), "regra", c.getRegra())));
        return visao(c);
    }

    private Visao visao(ConflitoCoordenacao c) {
        List<UUID> ids = List.of(mapper.readValue(c.getElementosJson(), UUID[].class));
        return new Visao(c.getId(), c.getChave(), c.getTipo(), c.getRegra(), ids, c.getMensagem(), c.getEvidencia(), c.getEstado().name(), c.getJustificativa(),
                c.getVersaoDetectada(), c.getVersaoVerificada(), c.getDecididoPor(), c.getDecididoEm());
    }
}
