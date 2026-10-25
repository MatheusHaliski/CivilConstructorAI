package br.com.civilconstructorai.modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** O que mudou numa aplicação: a cena (RF13) aplica só isto, sem recarregar tudo. */
public final class DiffModelo {

    public long versaoNova;
    public final List<Elemento> criados = new ArrayList<>();
    public final List<Elemento> alterados = new ArrayList<>();
    public final List<UUID> removidos = new ArrayList<>();
    public final List<Pavimento> pavimentosCriados = new ArrayList<>();
    public final List<Eixo> eixosCriados = new ArrayList<>();
    public final List<Eixo> eixosAlterados = new ArrayList<>();
    /** elementos gerados por IA ainda sem aprovação (camada ia-rascunho) */
    public boolean geradoPorIa;

    public List<Elemento> novosOuAlterados() {
        var l = new ArrayList<>(criados);
        l.addAll(alterados);
        return l;
    }
}
