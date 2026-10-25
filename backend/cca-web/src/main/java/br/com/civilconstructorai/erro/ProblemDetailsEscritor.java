package br.com.civilconstructorai.erro;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;

/** Escreve Problem Details (RFC 9457) direto na resposta — usado pelos handlers do Spring Security. */
@Component
public class ProblemDetailsEscritor {

    public static final String BASE_TIPO = "https://civilconstructorai.com.br/problemas/";
    private final ObjectMapper mapper;

    public ProblemDetailsEscritor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void escrever(HttpServletResponse res, HttpStatus status, String codigo, String detalhe) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        var corpo = new LinkedHashMap<String, Object>();
        corpo.put("type", URI.create(BASE_TIPO + codigo).toString());
        corpo.put("title", ProblemDetailsHandler.titulo(status));
        corpo.put("status", status.value());
        corpo.put("detail", detalhe);
        mapper.writeValue(res.getOutputStream(), corpo);
    }
}
