package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.lgpd.Repositorios.ConsentEventRepository;
import br.com.civilconstructorai.lgpd.Repositorios.ConsentimentoAtualRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF24 · um toggle por finalidade, todos desligados por padrão; revogar é o mesmo controle; histórico imutável. */
@Service
public class ConsentimentoService {

    public record TextoFinalidade(Finalidade finalidade, String versaoTexto, String titulo, String descricao, String baseLegal) {
    }

    public record Item(Finalidade finalidade, EstadoConsentimento estado, boolean ativo, String versaoTexto, String versaoVigente,
                       String titulo, String descricao, String baseLegal) {
    }

    private final ConsentimentoAtualRepository atuais;
    private final ConsentEventRepository eventos;
    private final JdbcTemplate jdbc;
    private final Relogio relogio;
    private final RegistroOperacoes registro;

    public ConsentimentoService(ConsentimentoAtualRepository atuais, ConsentEventRepository eventos, JdbcTemplate jdbc, Relogio relogio, RegistroOperacoes registro) {
        this.atuais = atuais;
        this.eventos = eventos;
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.registro = registro;
    }

    @Transactional(readOnly = true)
    public Map<Finalidade, TextoFinalidade> textos() {
        var m = new EnumMap<Finalidade, TextoFinalidade>(Finalidade.class);
        jdbc.query("select finalidade, versao_texto, titulo, descricao, base_legal from finalidade_texto", rs -> {
            var f = Finalidade.valueOf(rs.getString("finalidade"));
            m.put(f, new TextoFinalidade(f, rs.getString("versao_texto"), rs.getString("titulo"), rs.getString("descricao"), rs.getString("base_legal")));
        });
        return m;
    }

    @Transactional
    public List<Item> listar(UUID contaId) {
        var textos = textos();
        var porFinalidade = new EnumMap<Finalidade, ConsentimentoAtual>(Finalidade.class);
        atuais.findByChaveContaId(contaId).forEach(c -> porFinalidade.put(c.finalidade(), c));
        var out = new ArrayList<Item>();
        for (var f : Finalidade.values()) {
            var texto = textos.get(f);
            var vigente = texto == null ? "1.0" : texto.versaoTexto();
            var c = porFinalidade.get(f);
            // texto mudou de versão enquanto estava CONCEDIDO → A_CONFIRMAR (Art. 8º §5º)
            if (c != null && c.getEstado() == EstadoConsentimento.CONCEDIDO && VersaoDocumento.desatualizada(c.getVersaoTexto(), vigente)) {
                c.transicionar(EstadoConsentimento.A_CONFIRMAR, c.getVersaoTexto(), relogio.agora());
            }
            var estado = c == null ? EstadoConsentimento.NAO_CONCEDIDO : c.getEstado();
            out.add(new Item(f, estado, estado.ativo(), c == null ? null : c.getVersaoTexto(), vigente,
                    texto == null ? f.name() : texto.titulo(), texto == null ? "" : texto.descricao(), texto == null ? "Art. 7º, I" : texto.baseLegal()));
        }
        return out;
    }

    /** o mesmo controle concede e revoga; cada mudança vira um evento imutável */
    @Transactional
    public Item alterar(UUID contaId, Finalidade finalidade, boolean conceder, String ipHash) {
        var texto = textos().get(finalidade);
        var vigente = texto == null ? "1.0" : texto.versaoTexto();
        var agora = relogio.agora();
        var atual = atuais.findById(new ConsentimentoAtual.Chave(contaId, finalidade)).orElseGet(() -> new ConsentimentoAtual(contaId, finalidade, vigente));
        if (conceder) {
            var confirmando = atual.getEstado() == EstadoConsentimento.A_CONFIRMAR;
            atual.transicionar(EstadoConsentimento.CONCEDIDO, vigente, agora);
            eventos.save(new ConsentEvent(contaId, finalidade, confirmando ? ConsentEvent.Acao.CONFIRMAR : ConsentEvent.Acao.CONCEDER, vigente, agora, ipHash));
        } else {
            if (atual.getEstado() == EstadoConsentimento.NAO_CONCEDIDO || atual.getEstado() == EstadoConsentimento.REVOGADO) {
                // idempotente: já está desligado
            } else {
                atual.transicionar(EstadoConsentimento.REVOGADO, atual.getVersaoTexto(), agora);
                eventos.save(new ConsentEvent(contaId, finalidade, ConsentEvent.Acao.REVOGAR, atual.getVersaoTexto(), agora, ipHash));
            }
        }
        atuais.save(atual);
        registro.registrar(RegistroOperacoes.Operacao.de(conceder ? "CONSENTIMENTO_CONCEDIDO" : "CONSENTIMENTO_REVOGADO", contaId, null, null, "consentimento", contaId));
        return new Item(finalidade, atual.getEstado(), atual.getEstado().ativo(), atual.getVersaoTexto(), vigente,
                texto == null ? finalidade.name() : texto.titulo(), texto == null ? "" : texto.descricao(), texto == null ? "Art. 7º, I" : texto.baseLegal());
    }

    /** consulta rápida para outros módulos: a finalidade está autorizada para esta pessoa? */
    @Transactional(readOnly = true)
    public boolean autorizado(UUID contaId, Finalidade finalidade) {
        return atuais.findById(new ConsentimentoAtual.Chave(contaId, finalidade)).map(c -> c.getEstado().ativo()).orElse(false);
    }

    /** saga de eliminação: encerra tudo (histórico permanece, pseudonimizado pelo id) */
    @Transactional
    public void encerrarTodos(UUID contaId, String ipHash) {
        var agora = relogio.agora();
        for (var c : atuais.findByChaveContaId(contaId)) {
            if (c.getEstado() != EstadoConsentimento.ENCERRADO) {
                c.transicionar(EstadoConsentimento.ENCERRADO, c.getVersaoTexto(), agora);
                eventos.save(new ConsentEvent(contaId, c.finalidade(), ConsentEvent.Acao.ENCERRAR, c.getVersaoTexto(), agora, ipHash));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ConsentEvent> historico(UUID contaId) {
        return eventos.findByContaIdOrderByOcorridoEmDesc(contaId);
    }
}
