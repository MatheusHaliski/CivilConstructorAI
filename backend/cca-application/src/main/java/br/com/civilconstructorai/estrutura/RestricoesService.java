package br.com.civilconstructorai.estrutura;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.estrutura.restricoes.ExcecaoNormativa;
import br.com.civilconstructorai.estrutura.restricoes.RestricoesProjeto;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** RF08 · tolerâncias/seções mínimas por obra e exceções normativas. */
@Service
@Transactional
public class RestricoesService {

    public record Restricoes(double shortCurveTolerance, double pilarLadoMin, double pilarAreaMin, double vigaBMin, double lajeHMin, double paredeEspMin) {
        public static Restricoes de(RestricoesProjeto r) {
            return new Restricoes(r.getShortCurveTolerance(), r.getPilarLadoMin(), r.getPilarAreaMin(), r.getVigaBMin(), r.getLajeHMin(), r.getParedeEspMin());
        }

        public static Restricoes padrao() {
            return new Restricoes(RestricoesProjeto.PADRAO_SHORT_CURVE, RestricoesProjeto.PADRAO_PILAR_LADO, RestricoesProjeto.PADRAO_PILAR_AREA,
                    RestricoesProjeto.PADRAO_VIGA_B, RestricoesProjeto.PADRAO_LAJE_H, RestricoesProjeto.PADRAO_PAREDE_ESP);
        }
    }

    public record NovaExcecao(String regra, String elementoId, double valorSolicitado, String justificativa) {
    }

    private final Repositorios.Restricoes restricoes;
    private final Repositorios.ExcecoesNormativas excecoes;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final RegistroOperacoes registro;
    private final Relogio relogio;

    public RestricoesService(Repositorios.Restricoes restricoes, Repositorios.ExcecoesNormativas excecoes, AcessoObra acesso, ContextoUsuario contexto, RegistroOperacoes registro, Relogio relogio) {
        this.restricoes = restricoes;
        this.excecoes = excecoes;
        this.acesso = acesso;
        this.contexto = contexto;
        this.registro = registro;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Restricoes obter(UUID obraId) {
        acesso.exigirMembro(obraId);
        return restricoes.findByObraId(obraId).map(Restricoes::de).orElse(Restricoes.padrao());
    }

    public Restricoes atualizar(UUID obraId, Restricoes r) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var ent = restricoes.findByObraId(obraId).orElseGet(() -> restricoes.save(new RestricoesProjeto(u.empresaAtivaId(), obraId)));
        ent.atualizar(r.shortCurveTolerance(), r.pilarLadoMin(), r.pilarAreaMin(), r.vigaBMin(), r.lajeHMin(), r.paredeEspMin());
        registro.registrar(RegistroOperacoes.Operacao.de("RESTRICOES_ATUALIZADAS", u.contaId(), u.empresaAtivaId(), obraId, "RestricoesProjeto", ent.getId()));
        return Restricoes.de(ent);
    }

    @Transactional(readOnly = true)
    public List<ExcecaoNormativa> listarExcecoes(UUID obraId) {
        acesso.exigirMembro(obraId);
        return excecoes.findByObraIdOrderByCriadoEmDesc(obraId);
    }

    public ExcecaoNormativa solicitarExcecao(UUID obraId, NovaExcecao n) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.ARQUITETO, PapelObra.DONO, PapelObra.ADMIN);
        var u = contexto.exigir();
        var e = excecoes.save(new ExcecaoNormativa(u.empresaAtivaId(), obraId, n.regra(), n.elementoId(), n.valorSolicitado(), n.justificativa(), u.contaId()));
        registro.registrar(RegistroOperacoes.Operacao.de("EXCECAO_NORMATIVA_SOLICITADA", u.contaId(), u.empresaAtivaId(), obraId, "ExcecaoNormativa", e.getId()));
        return e;
    }

    public ExcecaoNormativa decidirExcecao(UUID obraId, UUID id, boolean aprovar) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        if (!u.podeAprovar(obraId)) {
            throw new ExcecaoDominio.AcessoNegado("Decidir uma exceção normativa exige papel aprovador, selo profissional verificado e 2FA.");
        }
        var e = excecoes.findByIdAndObraId(id, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Exceção normativa"));
        e.decidir(aprovar, u.contaId(), relogio.agora());
        registro.registrar(RegistroOperacoes.Operacao.de(aprovar ? "EXCECAO_NORMATIVA_ATIVA" : "EXCECAO_NORMATIVA_RECUSADA", u.contaId(), u.empresaAtivaId(), obraId, "ExcecaoNormativa", e.getId()));
        return e;
    }

    public ExcecaoNormativa revogarExcecao(UUID obraId, UUID id) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        if (!u.podeAprovar(obraId)) {
            throw new ExcecaoDominio.AcessoNegado("Revogar uma exceção normativa exige papel aprovador, selo profissional verificado e 2FA.");
        }
        var e = excecoes.findByIdAndObraId(id, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Exceção normativa"));
        e.revogar(u.contaId(), relogio.agora());
        return e;
    }
}
