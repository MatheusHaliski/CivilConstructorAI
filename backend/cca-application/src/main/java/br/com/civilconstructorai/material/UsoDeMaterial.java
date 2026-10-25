package br.com.civilconstructorai.material;

import java.util.List;

/** Porta: onde um material está aplicado (elementos dos modelos da empresa ativa, sob RLS). */
public interface UsoDeMaterial {

    record Uso(String obraNome, String elementoId, String tipo, String rotulo) {
    }

    List<Uso> elementosCom(String codigoMaterial, int limite);
}
