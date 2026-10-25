package br.com.civilconstructorai.arquivo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Armazenamento em memória para testes de integração (perfil {@code it}); S3/Garage nos demais perfis. */
@Component
@Profile("it")
public class ArmazenamentoEmMemoria implements ArmazenamentoArquivos {

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

    @Override
    public URI presignUpload(String chave, String contentType, long tamanhoMaxBytes, Duration validade) {
        return URI.create("memoria://upload/" + chave + "?tipo=" + contentType + "&max=" + tamanhoMaxBytes + "&ttl=" + validade.toSeconds());
    }

    @Override
    public URI presignDownload(String chave, Duration validade, String nomeArquivo) {
        return URI.create("memoria://download/" + chave + "?ttl=" + validade.toSeconds());
    }

    @Override
    public void gravar(String chave, byte[] conteudo, String contentType) {
        objetos.put(chave, conteudo.clone());
    }

    @Override
    public Optional<byte[]> ler(String chave) {
        return Optional.ofNullable(objetos.get(chave)).map(byte[]::clone);
    }

    @Override
    public void mover(String chaveOrigem, String chaveDestino) {
        var b = objetos.remove(chaveOrigem);
        if (b != null) {
            objetos.put(chaveDestino, b);
        }
    }

    @Override
    public void remover(String chave) {
        objetos.remove(chave);
    }

    @Override
    public boolean existe(String chave) {
        return objetos.containsKey(chave);
    }
}
