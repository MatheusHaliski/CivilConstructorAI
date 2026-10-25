package br.com.civilconstructorai.api.lgpd;

import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.erro.ProblemDetailsEscritor;
import br.com.civilconstructorai.lgpd.DocumentosLegaisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;

/**
 * RF24 CA: nova versão dos Termos ou da Política bloqueia o uso até o reaceite. Para usuário autenticado com
 * versão aceita anterior à vigente, toda rota fora de {@code /api/v1/auth/**}, {@code /api/v1/me/legal/**},
 * {@code /api/v1/legal/**} e {@code /api/v1/me/deletion} responde {@code 403 reaceite_pendente}.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class ReaceiteFilter extends OncePerRequestFilter {

    private final ContextoUsuario contexto;
    private final DocumentosLegaisService documentos;
    private final ObjectMapper mapper;

    public ReaceiteFilter(ContextoUsuario contexto, DocumentosLegaisService documentos, ObjectMapper mapper) {
        this.contexto = contexto;
        this.documentos = documentos;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        var p = req.getRequestURI();
        return !p.startsWith("/api/v1/") || p.startsWith("/api/v1/auth/") || p.startsWith("/api/v1/me/legal/")
                || p.startsWith("/api/v1/legal/") || p.equals("/api/v1/me/deletion") || p.startsWith("/api/v1/me/deletion/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        var usuario = contexto.atual();
        if (usuario.isPresent()) {
            var pendencias = documentos.pendencias(usuario.get().contaId());
            if (!pendencias.isEmpty()) {
                res.setStatus(HttpStatus.FORBIDDEN.value());
                res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                res.setCharacterEncoding("UTF-8");
                var corpo = new LinkedHashMap<String, Object>();
                corpo.put("type", URI.create(ProblemDetailsEscritor.BASE_TIPO + "reaceite_pendente").toString());
                corpo.put("title", "Sem permissão");
                corpo.put("status", 403);
                corpo.put("detail", "Há uma nova versão dos documentos legais para aceitar.");
                corpo.put("reaceitePendente", pendencias);
                mapper.writeValue(res.getOutputStream(), corpo);
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
