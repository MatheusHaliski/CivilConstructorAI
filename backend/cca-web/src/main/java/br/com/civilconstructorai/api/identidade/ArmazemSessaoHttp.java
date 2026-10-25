package br.com.civilconstructorai.api.identidade;

import br.com.civilconstructorai.identidade.Portas.ArmazemSessao;
import br.com.civilconstructorai.identidade.PrincipalSessao;
import br.com.civilconstructorai.sessao.RepositorioSessoesRedis;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Sessão opaca HTTP (Spring Session no Valkey). {@link #autenticar} grava o SecurityContext na sessão pelo
 * repositório padrão do Spring Security, que o {@code SecurityContextHolderFilter} reidrata a cada requisição —
 * por isso o principal é um record serializável sem {@code Optional}.
 */
@Component
public class ArmazemSessaoHttp implements ArmazemSessao {

    private static ServletRequestAttributes atributos() {
        var a = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (a == null) {
            throw new IllegalStateException("Sem requisição HTTP corrente.");
        }
        return a;
    }

    private static HttpServletRequest req() {
        return atributos().getRequest();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> Optional<T> ler(String chave, Class<T> tipo) {
        var s = req().getSession(false);
        if (s == null) {
            return Optional.empty();
        }
        var v = s.getAttribute(chave);
        return tipo.isInstance(v) ? Optional.of((T) v) : Optional.empty();
    }

    @Override
    public void gravar(String chave, Serializable valor) {
        req().getSession(true).setAttribute(chave, valor);
    }

    @Override
    public void remover(String chave) {
        var s = req().getSession(false);
        if (s != null) {
            s.removeAttribute(chave);
        }
    }

    @Override
    public void trocarId() {
        if (req().getSession(false) != null) {
            req().changeSessionId();
        }
    }

    @Override
    public void invalidar() {
        var s = req().getSession(false);
        if (s != null) {
            s.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public String idAtual() {
        var s = req().getSession(false);
        return s == null ? "" : s.getId();
    }

    @Override
    public void autenticar(PrincipalSessao principal) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USUARIO"));
        if (principal.suportePlataforma()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PLATAFORMA"));
        }
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        var request = req();
        var sessao = request.getSession(true);
        sessao.setAttribute(RepositorioSessoesRedis.ATRIBUTO_USER_AGENT, Optional.ofNullable(request.getHeader("User-Agent")).map(h -> h.substring(0, Math.min(h.length(), 160))).orElse("(desconhecido)"));
        // mesma chave que o HttpSessionSecurityContextRepository lê no SecurityContextHolderFilter
        sessao.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        // índice de principal do Spring Session (listar/encerrar sessões por usuário, RF23)
        sessao.setAttribute(org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.getName());
    }
}
