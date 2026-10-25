package br.com.civilconstructorai.config;

import br.com.civilconstructorai.identidade.PrincipalSessao;
import br.com.civilconstructorai.erro.ProblemDetailsEscritor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * RF18 · A sessão aberta no cadastro pertence a uma conta {@code PENDING_VERIFICATION}: até o código do e-mail ser
 * confirmado, só as rotas de verificação, leitura do próprio perfil, documentos legais e saída respondem. Qualquer
 * outra rota autenticada devolve 403 {@code verificacao_pendente}.
 */
public class VerificacaoPendenteFilter extends OncePerRequestFilter {

    private static final List<String> LIBERADAS = List.of(
            "/api/v1/auth/", "/api/v1/legal/", "/api/v1/me/verify-email", "/api/v1/me/legal/", "/error");

    private final ProblemDetailsEscritor problem;

    public VerificacaoPendenteFilter(ProblemDetailsEscritor problem) {
        this.problem = problem;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PrincipalSessao p && !p.emailVerificado() && !liberada(req)) {
            problem.escrever(res, HttpStatus.FORBIDDEN, "verificacao_pendente", "Confirme o código enviado ao seu e-mail para continuar.");
            return;
        }
        chain.doFilter(req, res);
    }

    static boolean liberada(HttpServletRequest req) {
        var uri = req.getRequestURI().substring(req.getContextPath().length());
        if ("GET".equals(req.getMethod()) && uri.equals("/api/v1/me/profile")) {
            return true;
        }
        return LIBERADAS.stream().anyMatch(uri::startsWith);
    }
}
