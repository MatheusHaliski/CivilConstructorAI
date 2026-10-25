package br.com.civilconstructorai.identidade;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Portas do módulo de identidade: implementadas por cca-infrastructure (Valkey, HKDF, TOTP) e cca-web (sessão HTTP). */
public final class Portas {

    private Portas() {
    }

    /** Sessão opaca do servidor (RF19): estado intermediário do login, sudo, principal. Nunca no cliente. */
    public interface ArmazemSessao {
        <T extends Serializable> Optional<T> ler(String chave, Class<T> tipo);

        void gravar(String chave, Serializable valor);

        void remover(String chave);

        /** troca o id da sessão (fixação de sessão) */
        void trocarId();

        void invalidar();

        /** id da sessão corrente (só para comparar com a lista do usuário, nunca exposto) */
        String idAtual();

        /** autentica: coloca o principal no SecurityContext e o persiste na sessão */
        void autenticar(PrincipalSessao principal);
    }

    /** HMAC-SHA256 com subchave HKDF por finalidade (códigos, tokens de convite, apelidos de sessão). */
    public interface AssinadorHmac {
        String hmacHex(String finalidade, String dado);
    }

    /** Limite de tentativas por chave (Valkey): estourou → 429 + Retry-After. */
    public interface LimitadorTentativas {
        void registrar(String chave, int maximo, Duration janela);

        void limpar(String chave);

        /** true só na primeira vez que a chave é vista dentro da janela (uso único, ex.: TOTP contra replay) */
        boolean primeiroUso(String chave, Duration janela);
    }

    /** TOTP RFC 6238. */
    public interface Totp {
        String gerarSegredo();

        boolean verificar(String segredo, String codigo);

        String uriOtpauth(String segredo, String email);
    }

    /** Grava o aceite versionado dos Termos/Política com data e hash do IP (Art. 7º, V e Art. 8º). */
    public interface RegistroAceite {
        void registrar(UUID contaId, String tipo, String versao, String ipHash, String userAgent);

        record Versoes(String termos, String politica) {
        }

        Versoes vigentes();
    }


    /** Sessões do Spring Session (repositório indexado por principal). */
    public interface RepositorioSessoes {
        record Sessao(String id, java.time.Instant criadaEm, java.time.Instant ultimoAcessoEm, String userAgent, String ipHash) {
        }

        List<Sessao> doUsuario(UUID contaId);

        void encerrar(String id);
    }
}
