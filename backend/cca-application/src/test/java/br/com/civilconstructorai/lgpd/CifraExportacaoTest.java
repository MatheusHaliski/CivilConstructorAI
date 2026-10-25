package br.com.civilconstructorai.lgpd;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CifraExportacaoTest {

    @Test
    void cifraEDecifraComASenhaCerta() {
        var senha = CifraExportacao.gerarSenha();
        assertThat(senha).hasSize(23).matches("[A-Za-z0-9]{5}(-[A-Za-z0-9]{5}){3}");
        var claro = "zip-de-mentira".getBytes(StandardCharsets.UTF_8);
        var pacote = CifraExportacao.cifrar(claro, senha);
        assertThat(new String(pacote, 0, 7, StandardCharsets.US_ASCII)).isEqualTo("CCAEXP1");
        assertThat(CifraExportacao.decifrar(pacote, senha)).isEqualTo(claro);
        assertThatThrownBy(() -> CifraExportacao.decifrar(pacote, senha + "x")).isInstanceOf(IllegalStateException.class);
        assertThat(CifraExportacao.hash(senha)).hasSize(64).isNotEqualTo(CifraExportacao.hash(senha + "x"));
    }
}
