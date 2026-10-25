package br.com.civilconstructorai.arquivo;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Porta do armazenamento de objetos (S3 em produção, Garage em dev/it). Envio sempre direto por URL
 * pré-assinada com tipo e tamanho na assinatura; {@code uploads/} expira em 1 dia; conteúdo definitivo é movido
 * para a chave final depois de validado (ex.: foto limpa de EXIF).
 */
public interface ArmazenamentoArquivos {

    Duration VALIDADE_PADRAO = Duration.ofMinutes(15);

    URI presignUpload(String chave, String contentType, long tamanhoMaxBytes, Duration validade);

    URI presignDownload(String chave, Duration validade, String nomeArquivo);

    void gravar(String chave, byte[] conteudo, String contentType);

    Optional<byte[]> ler(String chave);

    void mover(String chaveOrigem, String chaveDestino);

    void remover(String chave);

    boolean existe(String chave);
}
