package br.com.civilconstructorai.sessao;

import br.com.civilconstructorai.identidade.Portas.RepositorioSessoes;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Lista e encerra sessões por usuário pelo índice de principal do Spring Session (RF23). */
@Component
public class RepositorioSessoesRedis implements RepositorioSessoes {

    public static final String ATRIBUTO_USER_AGENT = "cca.userAgent";
    public static final String ATRIBUTO_IP_HASH = "cca.ipHash";

    private final FindByIndexNameSessionRepository<? extends Session> sessoes;

    public RepositorioSessoesRedis(FindByIndexNameSessionRepository<? extends Session> sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public List<Sessao> doUsuario(UUID contaId) {
        return sessoes.findByPrincipalName(contaId.toString()).values().stream()
                .map(s -> new Sessao(s.getId(), s.getCreationTime(), s.getLastAccessedTime(),
                        s.getAttributeOrDefault(ATRIBUTO_USER_AGENT, "(desconhecido)"), s.getAttributeOrDefault(ATRIBUTO_IP_HASH, "")))
                .sorted((a, b) -> b.ultimoAcessoEm().compareTo(a.ultimoAcessoEm()))
                .toList();
    }

    @Override
    public void encerrar(String id) {
        sessoes.deleteById(id);
    }
}
