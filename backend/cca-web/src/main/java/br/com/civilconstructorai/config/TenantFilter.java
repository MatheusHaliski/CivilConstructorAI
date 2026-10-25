package br.com.civilconstructorai.config;

import br.com.civilconstructorai.comum.ContextoTenant;
import br.com.civilconstructorai.comum.ContextoUsuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Propaga a empresa ativa da sessão para o {@link ContextoTenant} (e daí para o RLS) durante a requisição. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TenantFilter extends OncePerRequestFilter {

    private final ContextoUsuario contexto;

    public TenantFilter(ContextoUsuario contexto) {
        this.contexto = contexto;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        try {
            contexto.atual().map(u -> u.empresaAtivaId()).ifPresent(ContextoTenant::definir);
            chain.doFilter(req, res);
        } finally {
            ContextoTenant.limpar();
        }
    }
}
