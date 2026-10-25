package br.com.civilconstructorai.erro;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Converte exceções em Problem Details (RFC 9457) com título e detalhe em pt-BR; 422 traz {@code errors[]}. */
@RestControllerAdvice
public class ProblemDetailsHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    static String titulo(HttpStatus s) {
        return switch (s) {
            case BAD_REQUEST -> "Requisição inválida";
            case UNAUTHORIZED -> "Não autenticado";
            case FORBIDDEN -> "Sem permissão";
            case NOT_FOUND -> "Não encontrado";
            case CONFLICT -> "Conflito";
            case UNPROCESSABLE_CONTENT -> "Dados inválidos";
            case TOO_MANY_REQUESTS -> "Muitas tentativas";
            case SERVICE_UNAVAILABLE -> "Serviço indisponível";
            default -> s.getReasonPhrase();
        };
    }

    private static ProblemDetail problema(HttpStatus status, String codigo, String detalhe) {
        var p = ProblemDetail.forStatusAndDetail(status, detalhe);
        p.setTitle(titulo(status));
        p.setType(URI.create(ProblemDetailsEscritor.BASE_TIPO + codigo));
        return p;
    }

    @ExceptionHandler(ExcecaoDominio.RecursoNaoEncontrado.class)
    ProblemDetail naoEncontrado(ExcecaoDominio.RecursoNaoEncontrado e) {
        return problema(HttpStatus.NOT_FOUND, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(ExcecaoDominio.NaoAutenticado.class)
    ProblemDetail naoAutenticado(ExcecaoDominio.NaoAutenticado e) {
        return problema(HttpStatus.UNAUTHORIZED, e.codigo(), e.getMessage());
    }

    @ExceptionHandler({ExcecaoDominio.AcessoNegado.class, AccessDeniedException.class})
    ProblemDetail acessoNegado(RuntimeException e) {
        var codigo = e instanceof ExcecaoDominio d ? d.codigo() : "acesso_negado";
        var msg = e instanceof ExcecaoDominio ? e.getMessage() : "Você não tem permissão para esta ação.";
        return problema(HttpStatus.FORBIDDEN, codigo, msg);
    }

    @ExceptionHandler(ExcecaoDominio.SudoNecessario.class)
    ProblemDetail sudo(ExcecaoDominio.SudoNecessario e) {
        return problema(HttpStatus.FORBIDDEN, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(ExcecaoDominio.ReaceiteNecessario.class)
    ProblemDetail reaceite(ExcecaoDominio.ReaceiteNecessario e) {
        var p = problema(HttpStatus.FORBIDDEN, e.codigo(), e.getMessage());
        p.setProperty("reaceitePendente", e.documentos());
        return p;
    }

    @ExceptionHandler({ExcecaoDominio.TransicaoInvalida.class, ExcecaoDominio.Conflito.class})
    ProblemDetail conflito(ExcecaoDominio e) {
        return problema(HttpStatus.CONFLICT, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(ExcecaoDominio.Validacao.class)
    ProblemDetail validacao(ExcecaoDominio.Validacao e) {
        var p = problema(HttpStatus.UNPROCESSABLE_CONTENT, e.codigo(), e.getMessage());
        p.setProperty("errors", e.erros().stream().map(x -> Map.of("campo", x.campo(), "mensagem", x.mensagem())).toList());
        return p;
    }

    @ExceptionHandler(ExcecaoDominio.RegraDeNegocio.class)
    ProblemDetail regra(ExcecaoDominio.RegraDeNegocio e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(ExcecaoDominio.Indisponivel.class)
    public ProblemDetail indisponivel(ExcecaoDominio.Indisponivel e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, e.codigo(), e.getMessage());
    }

    @ExceptionHandler(ExcecaoDominio.LimiteExcedido.class)
    ResponseEntity<ProblemDetail> limite(ExcecaoDominio.LimiteExcedido e) {
        var p = problema(HttpStatus.TOO_MANY_REQUESTS, e.codigo(), e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSegundos())).body(p);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail beanValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> erros = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("campo", f.getField(), "mensagem", f.getDefaultMessage() == null ? "inválido" : f.getDefaultMessage()))
                .toList();
        var p = problema(HttpStatus.UNPROCESSABLE_CONTENT, "validacao", "Há " + erros.size() + " problema(s) nos dados enviados.");
        p.setProperty("errors", erros);
        return p;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException e) {
        var erros = e.getConstraintViolations().stream()
                .map(v -> Map.of("campo", v.getPropertyPath().toString(), "mensagem", v.getMessage()))
                .toList();
        var p = problema(HttpStatus.UNPROCESSABLE_CONTENT, "validacao", "Há " + erros.size() + " problema(s) nos dados enviados.");
        p.setProperty("errors", erros);
        return p;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException e) {
        var s = HttpStatus.valueOf(e.getStatusCode().value());
        return problema(s, s.name().toLowerCase(), e.getReason() == null ? titulo(s) : e.getReason());
    }

    /** requisição malformada (parâmetro ausente, tipo errado, JSON ilegível): 400 em pt-BR, não 500 (auditoria 2026-10) */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ProblemDetail parametroAusente(org.springframework.web.bind.MissingServletRequestParameterException e) {
        var p = problema(HttpStatus.BAD_REQUEST, "parametro_ausente", "Falta o parâmetro \"" + e.getParameterName() + "\".");
        p.setProperty("errors", List.of(Map.of("campo", e.getParameterName(), "mensagem", "obrigatório")));
        return p;
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ProblemDetail tipoInvalido(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        var p = problema(HttpStatus.BAD_REQUEST, "parametro_invalido", "Valor inválido para \"" + e.getName() + "\".");
        p.setProperty("errors", List.of(Map.of("campo", e.getName(), "mensagem", "valor inválido")));
        return p;
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ProblemDetail corpoIlegivel(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "corpo_invalido", "O corpo da requisição não pôde ser lido (JSON malformado ou valor fora do esperado).");
    }

    /** restrição do banco (único, FK) que escapou da validação de domínio: conflito explicável, não erro interno */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ProblemDetail integridade(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Violação de integridade não prevista pela validação: {}", e.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "conflito_dados", "Os dados entram em conflito com informações já gravadas. Recarregue e tente de novo.");
    }

    /** Última linha de defesa: nunca vaza stack trace nem cai no /error anônimo (que viraria 401). */
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("Erro não tratado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "erro_interno", "Ocorreu um erro inesperado. Tente novamente em instantes.");
    }
}
