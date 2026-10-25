package br.com.civilconstructorai.comum;

import java.util.List;
import java.util.Map;

/**
 * Exceções do domínio. A camada web converte cada uma em Problem Details (RFC 9457) com título e detalhe em
 * pt-BR. O {@code codigo} vai no campo {@code type} para o cliente tratar sem depender do texto.
 */
public abstract sealed class ExcecaoDominio extends RuntimeException
        permits ExcecaoDominio.RecursoNaoEncontrado, ExcecaoDominio.NaoAutenticado, ExcecaoDominio.AcessoNegado, ExcecaoDominio.TransicaoInvalida,
        ExcecaoDominio.Validacao, ExcecaoDominio.Conflito, ExcecaoDominio.LimiteExcedido, ExcecaoDominio.SudoNecessario,
        ExcecaoDominio.ReaceiteNecessario, ExcecaoDominio.RegraDeNegocio, ExcecaoDominio.Indisponivel {

    private final String codigo;

    protected ExcecaoDominio(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    /** 404: recurso de outra empresa/obra ou inexistente — nunca revela se existe. */
    public static final class RecursoNaoEncontrado extends ExcecaoDominio {
        public RecursoNaoEncontrado(String oQue) {
            super("recurso_nao_encontrado", oQue + " não encontrado(a).");
        }
    }

    /** 401: credencial inválida ou etapa de login incompleta; a mensagem nunca diz qual dado falhou. */
    public static final class NaoAutenticado extends ExcecaoDominio {
        public NaoAutenticado(String motivo) {
            super("nao_autenticado", motivo);
        }
    }

    /** 403: papel insuficiente na obra certa; o motivo vai em pt-BR. */
    public static final class AcessoNegado extends ExcecaoDominio {
        public AcessoNegado(String motivo) {
            super("acesso_negado", motivo);
        }

        /** 403 com tipo específico no Problem Details (ex.: convite_acima_do_convidante) */
        public AcessoNegado(String codigo, String motivo) {
            super(codigo, motivo);
        }
    }

    /** 409: transição de estado não permitida pela máquina de estados do agregado. */
    public static final class TransicaoInvalida extends ExcecaoDominio {
        public TransicaoInvalida(String entidade, Enum<?> de, Enum<?> para) {
            super("transicao_invalida", entidade + ": transição inválida de " + de + " para " + para + ".");
        }
    }

    /** 422: todos os problemas de uma vez em {@code errors[]}. */
    public static final class Validacao extends ExcecaoDominio {
        private final List<Erro> erros;

        public Validacao(List<Erro> erros) {
            super("validacao", "Há " + erros.size() + " problema(s) nos dados enviados.");
            this.erros = List.copyOf(erros);
        }

        public Validacao(String campo, String mensagem) {
            this(List.of(new Erro(campo, mensagem)));
        }

        public List<Erro> erros() {
            return erros;
        }

        public record Erro(String campo, String mensagem) {
        }
    }

    /** 409: versão desatualizada, duplicidade, estado já alterado por outra sessão. */
    public static final class Conflito extends ExcecaoDominio {
        public Conflito(String mensagem) {
            super("conflito", mensagem);
        }
    }

    /** 503: dependência externa (ex.: provedor de IA) não respondeu; nada foi alterado */
    public static final class Indisponivel extends ExcecaoDominio {
        public Indisponivel(String codigo, String mensagem) {
            super(codigo, mensagem);
        }
    }

    /** 429 + Retry-After. */
    public static final class LimiteExcedido extends ExcecaoDominio {
        private final long retryAfterSegundos;

        public LimiteExcedido(long retryAfterSegundos) {
            super("limite_excedido", "Muitas tentativas. Tente novamente em " + retryAfterSegundos + " s.");
            this.retryAfterSegundos = retryAfterSegundos;
        }

        public long retryAfterSegundos() {
            return retryAfterSegundos;
        }
    }

    /** 403 {@code sudo_required}: ação sensível exige senha + código recentes (RF23). */
    public static final class SudoNecessario extends ExcecaoDominio {
        public SudoNecessario() {
            super("sudo_required", "Confirme sua senha e o código de verificação para continuar.");
        }
    }

    /** 403 {@code reaceite_pendente}: nova versão dos Termos ou da Política (RF24). */
    public static final class ReaceiteNecessario extends ExcecaoDominio {
        private final Map<String, String> documentos;

        public ReaceiteNecessario(Map<String, String> documentos) {
            super("reaceite_pendente", "Há uma nova versão dos documentos legais para aceitar.");
            this.documentos = Map.copyOf(documentos);
        }

        public Map<String, String> documentos() {
            return documentos;
        }
    }

    /** 422 com código próprio: regra de negócio violada (ex.: convite promovendo acima do convidante). */
    public static final class RegraDeNegocio extends ExcecaoDominio {
        public RegraDeNegocio(String codigo, String mensagem) {
            super(codigo, mensagem);
        }
    }
}
