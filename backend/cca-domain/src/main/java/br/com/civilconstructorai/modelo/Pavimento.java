package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.TipoPavimento;

import java.util.UUID;

/** Nível do modelo (Level): elevação do piso acabado em metros; térreo = 0 (Disciplina Z, RF17). */
public record Pavimento(UUID id, String nome, double elevacao, TipoPavimento tipo) {

    public static TipoPavimento tipoPara(double z) {
        return z < 0 ? TipoPavimento.FUNDACAO : z == 0 ? TipoPavimento.TERREO : TipoPavimento.TIPO;
    }
}
