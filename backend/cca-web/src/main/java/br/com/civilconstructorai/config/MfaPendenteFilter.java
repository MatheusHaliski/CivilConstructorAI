package br.com.civilconstructorai.config;

import br.com.civilconstructorai.erro.ProblemDetailsEscritor;
import br.com.civilconstructorai.identidade.PrincipalSessao;
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
 * RF19 CA-06 · Dono, administrador ou aprovador sem 2FA configurado entra, mas só usa o autoatendimento da própria
 * conta ({@code /me/**}, sessão, documentos legais) até configurar o segundo fator; dados da empresa e das obras
 * respondem 403 {@code mfa_obrigatorio}. Auditoria 2026-10: antes o backend só bloqueava aprovações e o dono operava
 * a empresa inteira sem 2FA.
 */
public class MfaPendenteFilter extends OncePerRequestFilter {

    private static final List<String> LIBERADAS = List.of(
            "/api/v1/auth/", "/api/v1/legal/", "/api/v1/me/", "/api/v1/ia/uso", "/api/v1/convites/", "/api/v1/invites/", "/error");

    private final ProblemDetailsEscritor problem;

    public MfaPendenteFilter(ProblemDetailsEscritor problem) {
        this.problem = problem;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PrincipalSessao p && p.mfaPendente() && !liberada(req)) {
            problem.escrever(res, HttpStatus.FORBIDDEN, "mfa_obrigatorio",
                    "Seu papel exige verificação em duas etapas. Configure o aplicativo autenticador em Minha conta › Segurança para continuar.");
            return;
        }
        chain.doFilter(req, res);
    }

    static boolean liberada(HttpServletRequest req) {
        var uri = req.getRequestURI().substring(req.getContextPath().length());
        return LIBERADAS.stream().anyMatch(uri::startsWith);
    }
}
