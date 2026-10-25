package br.com.civilconstructorai.identidade;

import br.com.civilconstructorai.arquivo.ArmazenamentoArquivos;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.identidade.Portas.ArmazemSessao;
import br.com.civilconstructorai.identidade.Portas.AssinadorHmac;
import br.com.civilconstructorai.identidade.Portas.RepositorioSessoes;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import br.com.civilconstructorai.identidade.Repositorios.PreferenciaRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF23 · perfil, foto (URL pré-assinada), preferências, troca de senha (sudo, derruba as outras sessões), sessões, selo (RF21). */
@Service
@Transactional
public class PerfilService {

    public record Perfil(UUID id, String nome, String email, TipoConta tipo, EstadoConta estado, String cnpj, String registroProfissional,
                         String registroUf, TipoSelo seloTipo, EstadoSelo seloEstado, boolean mfaAtivo, boolean mfaObrigatorio, String fotoUrl) {
    }

    public record SessaoResumo(String apelido, java.time.Instant criadaEm, java.time.Instant ultimoAcessoEm, String userAgent, boolean atual) {
    }

    private final ContaRepository contas;
    private final PreferenciaRepository preferencias;
    private final PasswordEncoder senhas;
    private final SudoService sudo;
    private final ContextoUsuario contexto;
    private final RepositorioSessoes sessoes;
    private final ArmazemSessao sessao;
    private final AssinadorHmac hmac;
    private final ArmazenamentoArquivos arquivos;
    private final RegistroOperacoes registro;
    private final Relogio relogio;

    public PerfilService(ContaRepository contas, PreferenciaRepository preferencias, PasswordEncoder senhas, SudoService sudo, ContextoUsuario contexto,
                         RepositorioSessoes sessoes, ArmazemSessao sessao, AssinadorHmac hmac, ArmazenamentoArquivos arquivos, RegistroOperacoes registro, Relogio relogio) {
        this.contas = contas;
        this.preferencias = preferencias;
        this.senhas = senhas;
        this.sudo = sudo;
        this.contexto = contexto;
        this.sessoes = sessoes;
        this.sessao = sessao;
        this.hmac = hmac;
        this.arquivos = arquivos;
        this.registro = registro;
        this.relogio = relogio;
    }

    public Perfil perfil() {
        var c = contaAtual();
        var chaveFoto = "perfil/" + c.getId() + "/foto.jpg";
        var foto = arquivos.existe(chaveFoto) ? arquivos.presignDownload(chaveFoto, Duration.ofMinutes(15), "foto.jpg").toString() : null;
        return new Perfil(c.getId(), c.getNome(), c.getEmail(), c.getTipo(), c.getEstado(), c.getCnpj(), c.getRegistroProfissional(), c.getRegistroUf(),
                c.getSeloTipo(), c.getSeloEstado(), c.isMfaAtivo(), c.isMfaObrigatorio(), foto);
    }

    public Perfil atualizar(String nome, String registroProfissional, String registroUf, String cnpj) {
        var c = contaAtual();
        if (nome != null) {
            c.alterarNome(nome);
        }
        if (registroProfissional != null || registroUf != null) {
            c.alterarRegistro(registroProfissional == null ? c.getRegistroProfissional() : registroProfissional, registroUf == null ? c.getRegistroUf() : registroUf);
        }
        if (cnpj != null) {
            c.alterarCnpj(br.com.civilconstructorai.empresa.Cnpj.normalizar(cnpj));
        }
        return perfil();
    }

    /** foto: envio direto por URL pré-assinada (15 min, JPEG/PNG até 5 MB); o processamento remove EXIF e recorta 512 px */
    public URI urlEnvioFoto(String contentType) {
        if (!contentType.equals("image/jpeg") && !contentType.equals("image/png")) {
            throw new ExcecaoDominio.Validacao("contentType", "Envie JPEG ou PNG.");
        }
        var c = contaAtual();
        return arquivos.presignUpload("uploads/perfil/" + c.getId() + "/foto", contentType, 5L * 1024 * 1024, Duration.ofMinutes(15));
    }

    public void confirmarFoto() {
        var c = contaAtual();
        var origem = "uploads/perfil/" + c.getId() + "/foto";
        if (!arquivos.existe(origem)) {
            throw new ExcecaoDominio.Validacao("foto", "Nenhuma foto enviada.");
        }
        // o recorte quadrado de 512 px e a remoção de EXIF ficam no processador de imagem (javax.imageio) da infraestrutura;
        // aqui só movemos o objeto validado para a chave definitiva
        arquivos.mover(origem, "perfil/" + c.getId() + "/foto.jpg");
    }

    public Preferencia preferencias() {
        var u = contexto.exigir();
        return preferencias.findById(u.contaId()).orElseGet(() -> preferencias.save(new Preferencia(u.contaId())));
    }

    public Preferencia atualizarPreferencias(String idioma, String tema, Integer fontePct) {
        var p = preferencias();
        p.atualizar(idioma, tema, fontePct);
        return p;
    }

    /** troca de senha em modo sudo: encerra todas as outras sessões */
    public void trocarSenha(String senhaAtual, String novaSenha) {
        sudo.exigir();
        var c = contaAtual();
        if (!senhas.matches(senhaAtual == null ? "" : senhaAtual, c.getSenhaHash())) {
            throw new ExcecaoDominio.Validacao("senhaAtual", "Senha atual incorreta.");
        }
        PoliticaSenha.validar(novaSenha, c.getEmail());
        c.trocarSenha(senhas.encode(novaSenha));
        encerrarOutrasSessoes();
        registro.registrar(RegistroOperacoes.Operacao.de("SENHA_TROCADA", c.getId(), null, null, "Conta", c.getId()));
    }

    // ---------------------------------------------------------------- sessões (sem expor ids reais)
    public List<SessaoResumo> sessoes() {
        var u = contexto.exigir();
        var atual = sessao.idAtual();
        return sessoes.doUsuario(u.contaId()).stream()
                .map(s -> new SessaoResumo(apelido(s.id()), s.criadaEm(), s.ultimoAcessoEm(), s.userAgent(), s.id().equals(atual)))
                .toList();
    }

    public void encerrarSessao(String apelido) {
        var u = contexto.exigir();
        var alvo = sessoes.doUsuario(u.contaId()).stream().filter(s -> apelido(s.id()).equals(apelido)).findFirst()
                .orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Sessão"));
        sessoes.encerrar(alvo.id());
    }

    public void encerrarOutrasSessoes() {
        var u = contexto.exigir();
        var atual = sessao.idAtual();
        sessoes.doUsuario(u.contaId()).stream().filter(s -> !s.id().equals(atual)).forEach(s -> sessoes.encerrar(s.id()));
    }

    private String apelido(String idReal) {
        return hmac.hmacHex("sessao-apelido", idReal).substring(0, 16);
    }

    // ---------------------------------------------------------------- selo profissional (RF21)
    public Map<String, Object> selo() {
        var c = contaAtual();
        return Map.of("tipo", c.getSeloTipo() == null ? "" : c.getSeloTipo().name(), "estado", c.getSeloEstado().name(),
                "registro", c.getRegistroProfissional() == null ? "" : c.getRegistroProfissional(), "uf", c.getRegistroUf() == null ? "" : c.getRegistroUf());
    }

    public URI solicitarSelo(TipoSelo tipo, String registro, String uf, String contentTypeDocumento) {
        var c = contaAtual();
        if (tipo != TipoSelo.CNPJ && (registro == null || registro.isBlank())) {
            throw new ExcecaoDominio.Validacao("registro", "Informe o número do registro profissional.");
        }
        c.solicitarSelo(tipo, registro, uf);
        registro_(c.getId(), "SELO_SOLICITADO");
        return arquivos.presignUpload("uploads/selo/" + c.getId() + "/documento", contentTypeDocumento == null ? "application/pdf" : contentTypeDocumento, 10L * 1024 * 1024, Duration.ofMinutes(15));
    }

    /** fila de verificação (suporte da plataforma): só ids e estados */
    public List<Map<String, Object>> filaSelos(EstadoSelo estado) {
        return contas.findBySeloEstado(estado).stream().map(c -> Map.<String, Object>of("contaId", c.getId(), "tipo", c.getSeloTipo() == null ? "" : c.getSeloTipo().name(), "uf", c.getRegistroUf() == null ? "" : c.getRegistroUf())).toList();
    }

    public void decidirSelo(UUID contaId, boolean aprovado) {
        var c = contas.findById(contaId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Conta"));
        c.decidirSelo(aprovado, relogio.agora());
        registro.registrar(new RegistroOperacoes.Operacao("SELO_DECIDIDO", contexto.exigir().contaId(), null, null, "Conta", contaId, Map.of("resultado", aprovado ? "VERIFIED" : "REJECTED")));
    }

    private void registro_(UUID contaId, String tipo) {
        registro.registrar(RegistroOperacoes.Operacao.de(tipo, contaId, null, null, "Conta", contaId));
    }

    private Conta contaAtual() {
        var u = contexto.exigir();
        return contas.findById(u.contaId()).orElseThrow(() -> new ExcecaoDominio.AcessoNegado("Entre novamente."));
    }
}
