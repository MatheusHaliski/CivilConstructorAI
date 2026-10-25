package br.com.civilconstructorai.config;

import br.com.civilconstructorai.sessao.SessaoConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/** Limite absoluto de 24 h por sessão (além do ocioso de 60 min do Spring Session). */
@Component
public class SessaoAbsolutaFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        var sessao = req.getSession(false);
        if (sessao != null) {
            var criada = (Long) sessao.getAttribute(SessaoConfig.ATRIBUTO_CRIADA_EM);
            if (criada == null) {
                sessao.setAttribute(SessaoConfig.ATRIBUTO_CRIADA_EM, Instant.now().getEpochSecond());
            } else if (Instant.now().getEpochSecond() - criada > SessaoConfig.ABSOLUTO_SEGUNDOS) {
                sessao.invalidate();
            }
        }
        chain.doFilter(req, res);
    }
}
