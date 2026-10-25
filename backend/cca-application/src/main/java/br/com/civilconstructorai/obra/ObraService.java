package br.com.civilconstructorai.obra;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.identidade.Repositorios.ContaRepository;
import br.com.civilconstructorai.obra.Repositorios.MembroObraRepository;
import br.com.civilconstructorai.obra.Repositorios.ObraRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF22 · obras e membros. Camada 2 do RF21: {@code @PreAuthorize} com o bean {@code obraAccess} em toda escrita;
 * leitura exige ser membro (404 fora do escopo). O RLS (camada 3) filtra por tenant por baixo.
 */
@Service
@Transactional
public class ObraService {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), Obra.SRID);

    public record Resumo(UUID id, String nome, String endereco, String municipio, String uf, EstadoObra estado, String artRrt, UUID responsavelTecnicoId, List<double[]> perimetro, double[] localizacao, List<PapelObra> meusPapeis) {
    }

    public record Membro(UUID contaId, String nome, PapelObra papel) {
    }

    private final ObraRepository obras;
    private final MembroObraRepository membros;
    private final ContaRepository contas;
    private final ContextoUsuario contexto;
    private final AcessoObra acesso;
    private final RegistroOperacoes registro;

    public ObraService(ObraRepository obras, MembroObraRepository membros, ContaRepository contas, ContextoUsuario contexto, AcessoObra acesso, RegistroOperacoes registro) {
        this.obras = obras;
        this.membros = membros;
        this.contas = contas;
        this.contexto = contexto;
        this.acesso = acesso;
        this.registro = registro;
    }

    public List<Resumo> listar() {
        var u = contexto.exigir();
        if (u.empresaAtivaId() == null) {
            return List.of();
        }
        return obras.findByTenantIdOrderByNome(u.empresaAtivaId()).stream().filter(o -> u.membroDaObra(o.getId())).map(this::resumo).toList();
    }

    public Resumo obter(UUID obraId) {
        acesso.exigirMembro(obraId);
        return resumo(carregar(obraId));
    }

    public Resumo criar(String nome, String endereco, String municipio, String uf, List<double[]> perimetro, double[] localizacao) {
        var u = contexto.exigir();
        if (u.empresaAtivaId() == null || u.papelEmpresa().isEmpty()) {
            throw new ExcecaoDominio.AcessoNegado("Só DONO ou ADMIN da empresa criam obras.");
        }
        var o = new Obra(u.empresaAtivaId(), nome, endereco, municipio, uf);
        aplicarGeometria(o, perimetro, localizacao);
        o = obras.save(o);
        registro.registrar(RegistroOperacoes.Operacao.de("OBRA_CRIADA", u.contaId(), u.empresaAtivaId(), o.getId(), "Obra", o.getId()));
        return resumo(o);
    }

    @PreAuthorize("@obraAccess.hasObraRole(#obraId, 'DONO', 'ADMIN', 'ENGENHEIRO')")
    public Resumo atualizar(UUID obraId, String nome, String endereco, String municipio, String uf, String artRrt, List<double[]> perimetro, double[] localizacao) {
        var o = carregar(obraId);
        o.atualizar(nome, endereco, municipio, uf, artRrt);
        if (perimetro != null || localizacao != null) {
            aplicarGeometria(o, perimetro, localizacao);
        }
        return resumo(o);
    }

    @PreAuthorize("@obraAccess.hasObraRole(#obraId, 'DONO', 'ADMIN')")
    public Resumo transicionar(UUID obraId, EstadoObra para, String motivo) {
        var u = contexto.exigir();
        var o = carregar(obraId);
        if (o.getEstado() == EstadoObra.PLANEJADA && para == EstadoObra.EM_ANDAMENTO && !membros.existsByObraIdAndPapel(obraId, PapelObra.ENGENHEIRO)) {
            throw new ExcecaoDominio.RegraDeNegocio("obra_sem_responsavel", "A obra precisa de pelo menos um(a) engenheiro(a) responsável antes de iniciar.");
        }
        o.transicionar(para);
        registro.registrar(new RegistroOperacoes.Operacao("OBRA_TRANSICAO", u.contaId(), o.getTenantId(), obraId, "Obra", obraId, Map.of("para", para.name(), "motivo", motivo == null ? "" : motivo)));
        return resumo(o);
    }

    @PreAuthorize("@obraAccess.hasObraRole(#obraId, 'DONO', 'ADMIN', 'ENGENHEIRO')")
    public void definirResponsavelTecnico(UUID obraId, UUID contaId, String artRrt) {
        var o = carregar(obraId);
        if (membros.findByObraIdAndContaId(obraId, contaId).stream().noneMatch(m -> m.getPapel() == PapelObra.ENGENHEIRO)) {
            throw new ExcecaoDominio.Validacao("contaId", "O responsável técnico precisa ser ENGENHEIRO membro desta obra.");
        }
        o.definirResponsavelTecnico(contaId, artRrt);
    }

    public List<Membro> membros(UUID obraId) {
        acesso.exigirMembro(obraId);
        carregar(obraId); // fora da empresa ativa → 404 (DONO/ADMIN têm acesso implícito só às obras do próprio tenant)
        return membros.findByObraId(obraId).stream()
                .map(m -> new Membro(m.getContaId(), contas.findById(m.getContaId()).map(c -> c.getNome()).orElse("(removido)"), m.getPapel()))
                .toList();
    }

    @PreAuthorize("@obraAccess.hasObraRole(#obraId, 'DONO', 'ADMIN', 'ENGENHEIRO')")
    public void mudarPapel(UUID obraId, UUID contaId, PapelObra papel) {
        var u = contexto.exigir();
        var o = carregar(obraId);
        o.exigirEditavel();
        var papelDeQuemMuda = u.papelEmpresa().orElse(PapelObra.ENGENHEIRO);
        if (!papelDeQuemMuda.podeConvidar(papel)) {
            throw new ExcecaoDominio.RegraDeNegocio("papel_acima_do_convidante", "Você não pode atribuir um papel acima do seu.");
        }
        var atuais = membros.findByObraIdAndContaId(obraId, contaId);
        if (atuais.isEmpty()) {
            membros.save(new MembroObra(o.getTenantId(), obraId, contaId, papel));
        } else {
            atuais.getFirst().mudarPapel(papel);
            atuais.stream().skip(1).forEach(membros::delete);
        }
        registro.registrar(new RegistroOperacoes.Operacao("PAPEL_OBRA_ALTERADO", u.contaId(), o.getTenantId(), obraId, "MembroObra", contaId, Map.of("papel", papel.name())));
    }

    @PreAuthorize("@obraAccess.hasObraRole(#obraId, 'DONO', 'ADMIN', 'ENGENHEIRO')")
    public void removerMembro(UUID obraId, UUID contaId) {
        var u = contexto.exigir();
        var o = carregar(obraId);
        o.exigirEditavel();
        var atuais = membros.findByObraIdAndContaId(obraId, contaId);
        if (atuais.isEmpty()) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Membro");
        }
        boolean eraUnicoEngenheiro = atuais.stream().anyMatch(m -> m.getPapel() == PapelObra.ENGENHEIRO)
                && membros.findByObraId(obraId).stream().filter(m -> m.getPapel() == PapelObra.ENGENHEIRO).count() == 1
                && o.getEstado() != EstadoObra.PLANEJADA;
        if (eraUnicoEngenheiro) {
            throw new ExcecaoDominio.Conflito("A obra em andamento precisa manter um(a) engenheiro(a) responsável.");
        }
        membros.deleteAll(atuais);
        registro.registrar(RegistroOperacoes.Operacao.de("MEMBRO_OBRA_REMOVIDO", u.contaId(), o.getTenantId(), obraId, "MembroObra", contaId));
    }

    /** trabalhador sem login: só o vínculo (dados pessoais ficam no módulo da empresa/LGPD) */
    @PreAuthorize("@obraAccess.hasObraRole(#obraId, 'DONO', 'ADMIN', 'ENGENHEIRO', 'MESTRE_OBRAS')")
    public UUID cadastrarTrabalhador(UUID obraId, UUID contaIdOpcional) {
        var o = carregar(obraId);
        o.exigirEditavel();
        var contaId = contaIdOpcional == null ? br.com.civilconstructorai.comum.Ids.novo() : contaIdOpcional;
        membros.save(new MembroObra(o.getTenantId(), obraId, contaId, PapelObra.TRABALHADOR));
        return contaId;
    }

    // ---------------------------------------------------------------- util
    private Obra carregar(UUID obraId) {
        var u = contexto.exigir();
        return obras.findByIdAndTenantId(obraId, u.empresaAtivaId()).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Obra"));
    }

    private static void aplicarGeometria(Obra o, List<double[]> perimetro, double[] localizacao) {
        org.locationtech.jts.geom.Polygon poligono = null;
        if (perimetro != null && !perimetro.isEmpty()) {
            if (perimetro.size() < 3) {
                throw new ExcecaoDominio.Validacao("perimetro", "Perímetro precisa de 3+ vértices [lon, lat].");
            }
            var coords = new java.util.ArrayList<Coordinate>();
            for (var p : perimetro) {
                coords.add(new Coordinate(p[0], p[1]));
            }
            if (!coords.getFirst().equals2D(coords.getLast())) {
                coords.add(coords.getFirst());
            }
            poligono = GEO.createPolygon(coords.toArray(Coordinate[]::new));
        }
        var ponto = localizacao == null ? (poligono == null ? null : GEO.createPoint(poligono.getCentroid().getCoordinate())) : GEO.createPoint(new Coordinate(localizacao[0], localizacao[1]));
        if (poligono != null || ponto != null) {
            o.definirGeometria(poligono == null ? o.getPerimetro() : poligono, ponto);
        }
    }

    private Resumo resumo(Obra o) {
        var u = contexto.atual().orElse(null);
        List<double[]> per = o.getPerimetro() == null ? null : java.util.Arrays.stream(o.getPerimetro().getExteriorRing().getCoordinates()).map(c -> new double[]{c.x, c.y}).toList();
        double[] loc = o.getLocalizacao() == null ? null : new double[]{o.getLocalizacao().getX(), o.getLocalizacao().getY()};
        return new Resumo(o.getId(), o.getNome(), o.getEndereco(), o.getMunicipio(), o.getUf(), o.getEstado(), o.getArtRrt(), o.getResponsavelTecnicoId(), per, loc,
                u == null ? List.of() : u.papeisNaObra(o.getId()).stream().sorted().toList());
    }
}
