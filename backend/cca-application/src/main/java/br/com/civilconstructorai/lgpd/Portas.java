package br.com.civilconstructorai.lgpd;

import java.util.List;
import java.util.UUID;

/** Portas do módulo LGPD implementadas pela infraestrutura ou pela configuração. */
public final class Portas {

    private Portas() {
    }

    /** versões vigentes dos documentos legais (config {@code app.legal.*}) */
    public interface VersoesLegais {
        String termos();
        String politica();

        default String vigente(TipoDocumentoLegal tipo) {
            return tipo == TipoDocumentoLegal.TERMOS ? termos() : politica();
        }
    }

    /** DN-01: controladora, encarregado e carência — nomes em configuração, nunca no código */
    public interface ConfigLgpd {
        String controladoraRazaoSocial();
        String controladoraCnpj();
        String dpoNome();
        String dpoEmail();
        int carenciaDias();
        int prazoRespostaDias();
    }

    /**
     * Cofre de chaves: KEK por usuário fora do ciclo de backup do banco principal. Em produção, KMS sa-east-1 +
     * armazenamento separado com snapshots de 7 dias; em dev/it, {@code CofreChavesLocal}.
     */
    public interface CofreChaves {
        record KekGerada(UUID kekId, byte[] kek) {
        }

        KekGerada gerarKek(UUID usuarioId);

        /** lança {@code IllegalStateException} se a KEK não existe ou está revogada (tombstone) */
        byte[] abrir(UUID kekId);

        /** apaga a KEK e grava o tombstone — irreversível */
        void destruir(UUID kekId);

        List<UUID> tombstones();

        boolean revogada(UUID kekId);
    }

    /** DEK por usuário: cifra/decifra dados pessoais; destruir = crypto-shredding */
    public interface ChavesUsuario {
        void criarParaConta(UUID contaId);

        boolean existe(UUID contaId);

        byte[] cifrar(UUID contaId, byte[] claro);

        byte[] decifrar(UUID contaId, byte[] cifrado);

        void destruir(UUID contaId);
    }

    /** registro de atividades e notificações (Cassandra no profile full; no-op quando ausente) */
    public interface RegistroAtividades {
        void limparUsuario(UUID contaId);
    }

    /** sessões e contadores do usuário no Valkey */
    public interface SessoesDoUsuario {
        void encerrarTodas(UUID contaId);
    }

    /** HMAC com subchave derivada: hash de IP e de ids em recibos (não reversível, não reidentificável sem a chave) */
    public interface HashProtegido {
        String hash(String finalidade, String valor);
    }
}
