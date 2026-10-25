package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.estrutura.modelo.ModeloEstrutural;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Perfil {@code it}: os testes registram o recorte estrutural de cada obra aqui. */
@Component
@Profile("it")
public class ModeloEstruturalEmMemoria implements ModeloEstruturalFonte {

    private final Map<UUID, ModeloEstrutural> modelos = new ConcurrentHashMap<>();

    public void registrar(ModeloEstrutural m) {
        modelos.put(m.obraId(), m);
    }

    public void limpar() {
        modelos.clear();
    }

    @Override
    public ModeloEstrutural carregar(UUID obraId) {
        return modelos.getOrDefault(obraId, ModeloEstrutural.vazio(obraId));
    }
}
