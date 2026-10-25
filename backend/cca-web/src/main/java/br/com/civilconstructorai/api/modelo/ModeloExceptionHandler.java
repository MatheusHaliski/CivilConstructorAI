package br.com.civilconstructorai.api.modelo;

import br.com.civilconstructorai.erro.ProblemDetailsEscritor;
import br.com.civilconstructorai.modelo.ComandoReprovadoException;
import br.com.civilconstructorai.modelo.EstadoJson;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;

/** 422 com o relatório completo do loop duplo: todas as violações de uma vez (RF16). */
@RestControllerAdvice
@Order(0)
public class ModeloExceptionHandler {

    private final EstadoJson json;

    public ModeloExceptionHandler(EstadoJson json) {
        this.json = json;
    }

    @ExceptionHandler(ComandoReprovadoException.class)
    ProblemDetail reprovado(ComandoReprovadoException e) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        p.setTitle("Comando reprovado pelo auditor");
        p.setType(URI.create(ProblemDetailsEscritor.BASE_TIPO + "auditoria_reprovada"));
        p.setProperty("relatorio", json.relatorio(e.relatorio()));
        p.setProperty("errors", e.relatorio().todasAsViolacoes().stream().map(v -> Map.of("campo", v.codigo(), "mensagem", v.mensagem())).toList());
        return p;
    }
}
