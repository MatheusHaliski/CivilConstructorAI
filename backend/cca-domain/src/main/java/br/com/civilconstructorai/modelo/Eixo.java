package br.com.civilconstructorai.modelo;

import java.util.UUID;

/**
 * Eixo estrutural (RF03). Eixo de direção X é vertical na planta (x fixo = posicao); direção Y é horizontal
 * (y fixo). {@code recuoBolha} evita sobreposição nas pranchas (RF14).
 */
public record Eixo(UUID id, String nome, Direcao direcao, double posicao, double inicio, double fim, double recuoBolha) {

    public enum Direcao { X, Y }

    public Eixo comPosicao(double novaPosicao) {
        return new Eixo(id, nome, direcao, novaPosicao, inicio, fim, recuoBolha);
    }
}
