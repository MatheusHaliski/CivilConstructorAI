package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.comum.ExcecaoDominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Política de senha (RF18): mínimo 12 caracteres (frase), máximo 128, fora da lista de senhas comuns e sem
 * conter o e-mail nem o @usuário. Devolve <strong>todos</strong> os problemas de uma vez.
 */
public final class PoliticaSenha {

    public static final int MINIMO = 12;
    public static final int MAXIMO = 128;

    /** amostra das senhas mais usadas (a lista completa entra por configuração) */
    static final Set<String> COMUNS = Set.of(
            "123456789012", "senha1234567", "password1234", "qwertyuiop12", "construcao123", "engenharia123",
            "obra12345678", "civilconstructor", "abcdefghijkl", "111111111111", "123123123123", "admin1234567",
            "mudar1234567", "brasil123456", "minhasenha12", "senhasegura1", "iloveyou1234", "futebol12345");

    private PoliticaSenha() {
    }

    public static List<ExcecaoDominio.Validacao.Erro> problemas(String senha, String email) {
        var erros = new ArrayList<ExcecaoDominio.Validacao.Erro>();
        if (senha == null || senha.isEmpty()) {
            erros.add(new ExcecaoDominio.Validacao.Erro("senha", "Informe uma senha."));
            return erros;
        }
        if (senha.length() < MINIMO) {
            erros.add(new ExcecaoDominio.Validacao.Erro("senha", "Use pelo menos " + MINIMO + " caracteres (uma frase funciona bem)."));
        }
        if (senha.length() > MAXIMO) {
            erros.add(new ExcecaoDominio.Validacao.Erro("senha", "Use no máximo " + MAXIMO + " caracteres."));
        }
        var baixa = senha.toLowerCase(Locale.ROOT);
        if (COMUNS.contains(baixa)) {
            erros.add(new ExcecaoDominio.Validacao.Erro("senha", "Essa senha é muito comum."));
        }
        if (email != null && !email.isBlank()) {
            var e = email.trim().toLowerCase(Locale.ROOT);
            var usuario = e.contains("@") ? e.substring(0, e.indexOf('@')) : e;
            if (baixa.contains(e) || (usuario.length() >= 4 && baixa.contains(usuario))) {
                erros.add(new ExcecaoDominio.Validacao.Erro("senha", "A senha não pode conter o seu e-mail ou usuário."));
            }
        }
        return erros;
    }

    public static void validar(String senha, String email) {
        var erros = problemas(senha, email);
        if (!erros.isEmpty()) {
            throw new ExcecaoDominio.Validacao(erros);
        }
    }
}
