package br.com.civilconstructorai.cimento;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.modelo.ModeloService;
import br.com.civilconstructorai.notacao.TabelaA;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DosagemCimentoService {
    public record Composicao(@Positive double aguaLigante, @NotBlank @Size(max=1000) String agregado,
                             @Positive double consumoCimentoKgM3, @NotBlank @Size(max=1000) String procedimentoCura) { }
    public record Registro(@NotBlank String cimentoId, @NotBlank @Size(max=500) String produto,
                           @Positive double fckProjetoMPa, @Positive double fckComprovadoMPa,
                           @NotBlank @Size(max=2000) String referenciaEnsaio, @NotBlank @Size(max=2000) String origemMemorial,
                           @NotNull LocalDate dataEnsaio, @NotNull LocalDate validoAte,
                           @NotBlank @Size(max=2000) String metodoEstatistico,
                           @PositiveOrZero long versaoModelo, @NotNull @Valid Composicao composicao) { }
    public record Visao(UUID id, String estado, Registro dados, UUID confirmadoPor, String referenciaValidacao, boolean contextoCompativel, long versaoModeloAplicavel) { }
    private final DosagemCimentoRepository repository;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final ModeloService modelos;
    private final Relogio relogio;
    private final ObjectMapper mapper;
    private final ValidadorDosagemCimento validador;
    public DosagemCimentoService(DosagemCimentoRepository r, AcessoObra a, ContextoUsuario c, ModeloService m, Relogio clock, ObjectMapper j, ValidadorDosagemCimento v) {
        repository=r; acesso=a; contexto=c; modelos=m; relogio=clock; mapper=j; validador=v;
    }
    public List<Visao> listar(UUID obraId, long versao) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        var modelo=modelos.obter(obraId,null);
        if(modelo.versao()!=versao) throw new ExcecaoDominio.Conflito("Recarregue a versão atual para consultar dosagens.");
        var hash=validador.contextoHash(modelo.elementos(),modelo.pavimentos(),modelo.eixos());
        return repository.findByObraIdOrderByCriadoEmDesc(obraId).stream().map(d->visao(d,hash,versao)).toList();
    }
    public Visao registrar(UUID obraId, Registro r) {
        exigirEngenheiro(obraId);
        validar(r);
        var modelo=modelos.obter(obraId,null);
        if (modelo.versao() != r.versaoModelo()) throw new ExcecaoDominio.Conflito("Registre a dosagem na versão atual do modelo.");
        var u=contexto.exigir();
        var hash=validador.contextoHash(modelo.elementos(),modelo.pavimentos(),modelo.eixos());
        return visao(repository.save(new DosagemCimento(u.empresaAtivaId(), obraId, r.versaoModelo(), mapper.writeValueAsString(r), u.contaId(),hash)),hash,modelo.versao());
    }
    public Visao confirmar(UUID obraId, UUID id, String referenciaValidacao) {
        exigirEngenheiro(obraId);
        var u=contexto.exigir();
        if (!u.seloVerificado() || !u.mfaVerificado()) throw new ExcecaoDominio.AcessoNegado("Confirmação exige profissional verificado e autenticação em dois fatores.");
        if (referenciaValidacao == null || referenciaValidacao.isBlank() || referenciaValidacao.length()>2000) throw new ExcecaoDominio.Validacao("referenciaValidacao", "Informe referência da validação profissional (ART e relatório de dosagem/ensaio).");
        var d=obter(obraId,id); var r=dados(d); validar(r);
        var modelo=modelos.obter(obraId,null); var hash=validador.contextoHash(modelo.elementos(),modelo.pavimentos(),modelo.eixos());
        if (!hash.equals(d.getContextoHash())) throw new ExcecaoDominio.Conflito("Contexto estrutural mudou desde o registro; cadastre nova evidência.");
        d.confirmar(u.contaId(),relogio.agora(),referenciaValidacao);
        return visao(repository.save(d),hash,modelo.versao());
    }
    /** Confere dados do banco; qualquer fck/estado/documento extra enviado pelo cliente é irrelevante. */
    public Visao verificarAssociacao(UUID obraId, UUID id, String cimentoId, double fckProjetoMPa, long versaoModelo) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        var modelo=modelos.obter(obraId,null); var hash=validador.contextoHash(modelo.elementos(),modelo.pavimentos(),modelo.eixos());
        if(modelo.versao()!=versaoModelo) throw new ExcecaoDominio.Conflito("Recarregue a versão atual.");
        var d=validador.validar(obraId,id,cimentoId,fckProjetoMPa,versaoModelo,hash);
        return visao(d,hash,versaoModelo);
    }
    public Visao verificarElemento(UUID obraId, UUID dosagemId, UUID elementoId, String cimentoId, long versaoModelo) {
        acesso.exigirMembro(obraId);
        var modelo=modelos.obter(obraId,null);
        var elemento=modelo.elementos().stream().filter(e->e.getId().equals(elementoId)).findFirst().orElseThrow(()->new ExcecaoDominio.RecursoNaoEncontrado("Elemento"));
        var fck=TabelaA.material(elemento.getMaterialId()).map(m->m.physicalProperties().fck_MPa()).orElse(null);
        if (fck==null) throw new ExcecaoDominio.Validacao("elementoId","Elemento sem fck de concreto especificado.");
        return verificarAssociacao(obraId,dosagemId,cimentoId,fck,versaoModelo);
    }
    public record ContextoElegivel(String elementoTipo, double fckProjetoMPa, List<String> cimentoIds) { }
    public ContextoElegivel elegiveis(UUID obraId,UUID elementoId) {
        acesso.exigirMembro(obraId);
        var modelo=modelos.obter(obraId,null);
        var elemento=modelo.elementos().stream().filter(e->e.getId().equals(elementoId)).findFirst().orElseThrow(()->new ExcecaoDominio.RecursoNaoEncontrado("Elemento"));
        var fck=TabelaA.material(elemento.getMaterialId()).map(m->m.physicalProperties().fck_MPa()).orElse(null);
        if(fck==null) throw new ExcecaoDominio.Validacao("elementoId","Elemento sem fck de concreto especificado.");
        var hash=validador.contextoHash(modelo.elementos(),modelo.pavimentos(),modelo.eixos());
        var ids=new java.util.ArrayList<String>();
        for(var d:repository.findByObraIdOrderByCriadoEmDesc(obraId)) {
            var r=dados(d);
            try { validador.validar(obraId,d.getId(),r.cimentoId(),fck,modelo.versao(),hash); ids.add(r.cimentoId()); }
            catch(ExcecaoDominio.RegraDeNegocio ex) { /* Não elegível: não enviar ao modelo. */ }
        }
        return new ContextoElegivel(elemento.getTipo().name(),fck,ids.stream().distinct().toList());
    }
    private void exigirEngenheiro(UUID obraId) { acesso.exigirMembro(obraId); acesso.exigirPapel(obraId,PapelObra.ENGENHEIRO); }
    private DosagemCimento obter(UUID obra, UUID id) { return repository.findByIdAndObraId(id,obra).orElseThrow(()->new ExcecaoDominio.RecursoNaoEncontrado("Dosagem")); }
    private Registro dados(DosagemCimento d) { return mapper.readValue(d.getDadosJson(),Registro.class); }
    private Visao visao(DosagemCimento d,String hash,long versao) { return new Visao(d.getId(),d.getEstado(),dados(d),d.getConfirmadoPor(),d.getReferenciaValidacao(),d.getContextoHash().equals(hash),versao); }
    private void validar(Registro r) {
        if (!CimentoService.IDS.contains(r.cimentoId())) throw new ExcecaoDominio.Validacao("cimentoId","Selecione ID do catálogo.");
        var hoje=LocalDate.ofInstant(relogio.agora(),java.time.ZoneOffset.UTC);
        if (r.dataEnsaio()==null || r.validoAte()==null || r.dataEnsaio().isAfter(hoje) || r.validoAte().isBefore(hoje) || r.validoAte().isBefore(r.dataEnsaio()))
            throw new ExcecaoDominio.Validacao("dataEnsaio","Ensaio e vigência devem ser válidos na data atual.");
        if (!Double.isFinite(r.fckProjetoMPa()) || !Double.isFinite(r.fckComprovadoMPa()) || r.fckProjetoMPa()<=0 || r.fckComprovadoMPa()<r.fckProjetoMPa()
            || r.composicao()==null || !Double.isFinite(r.composicao().aguaLigante()) || !Double.isFinite(r.composicao().consumoCimentoKgM3())
            || r.composicao().aguaLigante()<=0 || r.composicao().consumoCimentoKgM3()<=0)
            throw new ExcecaoDominio.Validacao("dosagem","Informe desempenho comprovado e composição identificada válidos.");
    }
}
