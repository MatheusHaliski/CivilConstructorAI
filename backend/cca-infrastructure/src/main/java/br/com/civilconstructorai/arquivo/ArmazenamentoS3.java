package br.com.civilconstructorai.arquivo;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Armazenamento de objetos em S3 (produção) ou Garage (dev, API compatível com S3, endereçamento por caminho).
 * Envio sempre por URL pré-assinada com o {@code Content-Type} na assinatura; download com
 * {@code Content-Disposition} para o nome do arquivo. O perfil {@code it} usa {@link ArmazenamentoEmMemoria}.
 */
@Component
@Profile("!it")
public class ArmazenamentoS3 implements ArmazenamentoArquivos, DisposableBean {

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;

    public ArmazenamentoS3(@Value("${app.arquivos.bucket:cca}") String bucket,
                           @Value("${app.arquivos.endpoint:}") String endpoint,
                           @Value("${app.arquivos.regiao:sa-east-1}") String regiao,
                           @Value("${app.arquivos.acesso:}") String acesso,
                           @Value("${app.arquivos.segredo:}") String segredo) {
        this.bucket = bucket;
        AwsCredentialsProvider credenciais = acesso.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(acesso, segredo));
        var caminho = S3Configuration.builder().pathStyleAccessEnabled(!endpoint.isBlank()).build();
        var cliente = S3Client.builder().httpClient(UrlConnectionHttpClient.create()).region(Region.of(regiao))
                .credentialsProvider(credenciais).serviceConfiguration(caminho);
        var assinador = S3Presigner.builder().region(Region.of(regiao)).credentialsProvider(credenciais).serviceConfiguration(caminho);
        if (!endpoint.isBlank()) {
            cliente.endpointOverride(URI.create(endpoint));
            assinador.endpointOverride(URI.create(endpoint));
        }
        this.s3 = cliente.build();
        this.presigner = assinador.build();
    }

    @Override
    public URI presignUpload(String chave, String contentType, long tamanhoMaxBytes, Duration validade) {
        // o tamanho máximo é conferido de novo ao confirmar o upload (existe/ler); a assinatura fixa o tipo
        var req = PutObjectPresignRequest.builder().signatureDuration(validade)
                .putObjectRequest(p -> p.bucket(bucket).key(chave).contentType(contentType)).build();
        return URI.create(presigner.presignPutObject(req).url().toString());
    }

    @Override
    public URI presignDownload(String chave, Duration validade, String nomeArquivo) {
        var req = GetObjectPresignRequest.builder().signatureDuration(validade)
                .getObjectRequest(g -> {
                    g.bucket(bucket).key(chave);
                    if (nomeArquivo != null && !nomeArquivo.isBlank()) {
                        g.responseContentDisposition("attachment; filename=\"" + nomeArquivo.replace("\"", "") + "\"");
                    }
                }).build();
        return URI.create(presigner.presignGetObject(req).url().toString());
    }

    @Override
    public void gravar(String chave, byte[] conteudo, String contentType) {
        s3.putObject(p -> p.bucket(bucket).key(chave).contentType(contentType), RequestBody.fromBytes(conteudo));
    }

    @Override
    public Optional<byte[]> ler(String chave) {
        try {
            return Optional.of(s3.getObjectAsBytes(g -> g.bucket(bucket).key(chave)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void mover(String chaveOrigem, String chaveDestino) {
        s3.copyObject(c -> c.sourceBucket(bucket).sourceKey(chaveOrigem).destinationBucket(bucket).destinationKey(chaveDestino));
        s3.deleteObject(d -> d.bucket(bucket).key(chaveOrigem));
    }

    @Override
    public void remover(String chave) {
        s3.deleteObject(d -> d.bucket(bucket).key(chave));
    }

    @Override
    public boolean existe(String chave) {
        try {
            s3.headObject(h -> h.bucket(bucket).key(chave));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void destroy() {
        presigner.close();
        s3.close();
    }
}
