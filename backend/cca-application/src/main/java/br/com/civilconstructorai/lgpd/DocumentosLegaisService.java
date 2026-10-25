package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.lgpd.Portas.VersoesLegais;
import br.com.civilconstructorai.lgpd.Repositorios.AceiteTermosRepository;
import br.com.civilconstructorai.lgpd.Repositorios.DocumentoLegalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RF24 · documentos legais vigentes, aceite inicial (cadastro) e reaceite quando a versão muda. */
@Service
public class DocumentosLegaisService {

    public record Documento(TipoDocumentoLegal tipo, String versao, String url) {
    }

    private final VersoesLegais versoes;
    private final DocumentoLegalRepository documentos;
    private final AceiteTermosRepository aceites;
    private final Relogio relogio;

    public DocumentosLegaisService(VersoesLegais versoes, DocumentoLegalRepository documentos, AceiteTermosRepository aceites, Relogio relogio) {
        this.versoes = versoes;
        this.documentos = documentos;
        this.aceites = aceites;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<Documento> vigentes() {
        return List.of(documento(TipoDocumentoLegal.TERMOS), documento(TipoDocumentoLegal.POLITICA));
    }

    private Documento documento(TipoDocumentoLegal tipo) {
        var versao = versoes.vigente(tipo);
        var url = documentos.findByTipoAndVersao(tipo, versao).map(DocumentoLegal::getUrl)
                .orElse("https://civilconstructorai.com.br/legal/" + tipo.name().toLowerCase() + "/" + versao);
        return new Documento(tipo, versao, url);
    }

    /** {tipo → versão vigente} dos documentos cuja última versão aceita é anterior à vigente */
    @Transactional(readOnly = true)
    public Map<String, String> pendencias(UUID contaId) {
        var out = new LinkedHashMap<String, String>();
        for (var tipo : TipoDocumentoLegal.values()) {
            var vigente = versoes.vigente(tipo);
            var aceita = aceites.findFirstByContaIdAndTipoOrderByAceitoEmDesc(contaId, tipo).map(AceiteTermos::getVersao).orElse(null);
            if (VersaoDocumento.desatualizada(aceita, vigente)) {
                out.put(tipo.name(), vigente);
            }
        }
        return out;
    }

    /** aceite no cadastro (RF18) — chamado pelo módulo de identidade; grava versão, data e IP (hash) */
    @Transactional
    public void registrarAceite(UUID contaId, TipoDocumentoLegal tipo, String versao, String ipHash, String userAgent) {
        if (!versao.equals(versoes.vigente(tipo))) {
            throw new ExcecaoDominio.Conflito("A versão " + versao + " de " + tipo + " não é a vigente (" + versoes.vigente(tipo) + ").");
        }
        aceites.save(new AceiteTermos(contaId, tipo, versao, relogio.agora(), ipHash, userAgent));
    }

    /** POST /me/legal/reaceite */
    @Transactional
    public void reaceitar(UUID contaId, TipoDocumentoLegal tipo, String versao, String ipHash, String userAgent) {
        registrarAceite(contaId, tipo, versao, ipHash, userAgent);
    }

    @Transactional(readOnly = true)
    public Map<TipoDocumentoLegal, String> versoesAceitas(UUID contaId) {
        var m = new EnumMap<TipoDocumentoLegal, String>(TipoDocumentoLegal.class);
        for (var tipo : TipoDocumentoLegal.values()) {
            aceites.findFirstByContaIdAndTipoOrderByAceitoEmDesc(contaId, tipo).ifPresent(a -> m.put(tipo, a.getVersao()));
        }
        return m;
    }
}
