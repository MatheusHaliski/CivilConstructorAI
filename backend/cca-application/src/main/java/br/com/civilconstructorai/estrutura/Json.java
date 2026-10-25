package br.com.civilconstructorai.estrutura;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Serialização JSON para as colunas de texto dos jobs e rascunhos. */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String escrever(Object o) {
        return mapper.writeValueAsString(o);
    }

    public <T> T ler(String json, Class<T> tipo) {
        return mapper.readValue(json, tipo);
    }

    public <T> T ler(String json, TypeReference<T> tipo) {
        return mapper.readValue(json, tipo);
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
