package br.com.civilconstructorai.notacao;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParserViTest {

    @Test
    void lePontoNumeroRelativoEPolar() {
        var p = ParserVi.parse("VI(PAREDE, p1=[0,0], p2=@5<0, esp=0.15, h=2.8, mat=MAT_ALV_BLOCO14)");
        assertThat(p.erros()).isEmpty();
        var c = p.comandos().get(0);
        assertThat(c.codigo()).isEqualTo("PAREDE");
        assertThat(c.args().get("p1")).isEqualTo(new Ponto2D(0, 0));
        assertThat(c.args().get("p2")).isEqualTo(new ParserVi.Polar(5, 0));
        assertThat(c.args().get("esp")).isEqualTo(0.15);
        assertThat(c.args().get("mat")).isEqualTo("MAT_ALV_BLOCO14");
    }

    @Test
    void serializaDeVolta() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("p1", new Ponto2D(0, 0));
        args.put("p2", new Ponto2D(5, 0));
        args.put("b", 0.12);
        args.put("h", 0.4);
        args.put("mat", "MAT_CONC_FCK30");
        assertThat(FormatadorVi.formatar("VIGA", args)).isEqualTo("VI(VIGA, p1=[0,0], p2=[5,0], b=0.12, h=0.4, mat=MAT_CONC_FCK30)");
    }

    @Test
    void devolveTodosOsErrosComLinhaEColuna() {
        var p = ParserVi.parse("PAREDE p1=[0,0]\nVI(PILAR, 1x=[0,0])\nVI(VIGA, p1=[0,0], p2=[5,0])");
        assertThat(p.erros()).hasSize(2);
        assertThat(p.erros().get(0).codigo()).isEqualTo("VI-000");
        assertThat(p.erros().get(0).linha()).isEqualTo(1);
        assertThat(p.erros().get(1).codigo()).isEqualTo("VI-003");
        assertThat(p.erros().get(1).linha()).isEqualTo(2);
        assertThat(p.comandos()).hasSize(2);
    }

    @Test
    void sanitizaIdentificadoresComOrdinaisAcentosEEmojis() {
        assertThat(Sanitizador.identificador("1º Pavimento ☀️ Ático")).isEqualTo("1o_Pavimento_Atico");
        assertThat(Sanitizador.texto("Sala ª 2 😀")).isEqualTo("Sala a 2");
    }
}
