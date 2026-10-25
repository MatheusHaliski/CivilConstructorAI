package br.com.civilconstructorai.modelo;

import br.com.civilconstructorai.modelo.Enums.Severidade;

import java.util.List;
import java.util.UUID;

/** Resultado do loop duplo (RF16): laço 1 (código), laço 2 (geometria), correções automáticas aplicadas. */
public record RelatorioAuditoria(Laco laco1, Laco2 laco2, List<Correcao> correcoes, boolean aprovado, int iteracoesLlm) {

    public record Violacao(String codigo, Severidade severidade, String regra, UUID elementoRef, String mensagem, String esperado, String obtido) {
        public static Violacao de(String codigo, String regra, String mensagem, Elemento e) {
            return new Violacao(codigo, Severidade.VIOLACAO, regra, e == null ? null : e.getId(), mensagem, null, null);
        }

        public Violacao comValores(String esperado, String obtido) {
            return new Violacao(codigo, severidade, regra, elementoRef, mensagem, esperado, obtido);
        }

        public Violacao aviso() {
            return new Violacao(codigo, Severidade.AVISO, regra, elementoRef, mensagem, esperado, obtido);
        }
    }

    public record Correcao(String regra, UUID elementoRef, String campo, Object antes, Object depois) {
    }

    public record Laco(boolean aprovado, List<Violacao> violacoes) {
    }

    public record Laco2(boolean aprovado, List<Violacao> violacoes, List<Violacao> avisos) {
    }

    public List<Violacao> todasAsViolacoes() {
        var l = new java.util.ArrayList<>(laco1.violacoes());
        l.addAll(laco2.violacoes());
        return l;
    }

    public RelatorioAuditoria comIteracoes(int n) {
        return new RelatorioAuditoria(laco1, laco2, correcoes, aprovado, n);
    }
}
