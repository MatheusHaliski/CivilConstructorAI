package br.com.civilconstructorai.lgpd;

import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.lgpd.Repositorios.RetencaoEmpresaRepository;
import br.com.civilconstructorai.registro.RegistroOperacoes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF25 · "Dados dos trabalhadores" (empresa controladora): inventário por categoria com retenção, contagens
 * (ASO só contagem), e configuração de retenção nunca abaixo do mínimo legal (DN-03).
 */
@Service
public class InventarioService {

    public record Linha(CategoriaDado categoria, String titulo, String finalidade, String baseLegal, int minimoMeses, int prazoMeses, String descricaoPrazo, boolean sensivel, long registros) {
    }

    private final RetencaoEmpresaRepository retencoes;
    private final ContextoUsuario contexto;
    private final JdbcTemplate jdbc;
    private final RegistroOperacoes registro;

    public InventarioService(RetencaoEmpresaRepository retencoes, ContextoUsuario contexto, JdbcTemplate jdbc, RegistroOperacoes registro) {
        this.retencoes = retencoes;
        this.contexto = contexto;
        this.jdbc = jdbc;
        this.registro = registro;
    }

    @Transactional(readOnly = true)
    public List<Linha> inventario(UUID empresaId) {
        exigirAdmin(empresaId);
        var configuradas = retencoes.findByTenantId(empresaId);
        var out = new ArrayList<Linha>();
        for (var c : CategoriaDado.values()) {
            if (!c.deTrabalhador()) {
                continue;
            }
            int prazo = configuradas.stream().filter(r -> r.getCategoria() == c).map(RetencaoEmpresa::getPrazoMeses).findFirst().orElse(c.minimoMeses());
            out.add(new Linha(c, c.titulo(), c.finalidade(), c.baseLegal(), c.minimoMeses(), prazo, c.descricaoPrazo(), c.sensivel(), contar(c, empresaId)));
        }
        return out;
    }

    /** contagens pelas tabelas existentes; categorias cujas tabelas ainda não existem (ASO, ponto, fotos) contam zero */
    private long contar(CategoriaDado c, UUID empresaId) {
        return switch (c) {
            case CONTATO_OPERACIONAL -> conta("select count(distinct conta_id) from membro_obra where tenant_id = ?", empresaId);
            case DOCUMENTOS_TRABALHISTAS -> conta("select count(*) from membro_obra where tenant_id = ? and papel = 'TRABALHADOR'", empresaId);
            default -> 0;
        };
    }

    private long conta(String sql, UUID empresaId) {
        Long n = jdbc.queryForObject(sql, Long.class, empresaId);
        return n == null ? 0 : n;
    }

    @Transactional
    public Linha configurarRetencao(UUID empresaId, CategoriaDado categoria, int prazoMeses) {
        var u = exigirAdmin(empresaId);
        if (!categoria.deTrabalhador()) {
            throw new ExcecaoDominio.Validacao("categoria", "Só categorias de dados de trabalhadores têm retenção configurável pela empresa.");
        }
        var r = retencoes.findByTenantIdAndCategoria(empresaId, categoria).orElse(null);
        if (r == null) {
            r = new RetencaoEmpresa(empresaId, categoria, prazoMeses);
        } else {
            r.definirPrazo(prazoMeses);
        }
        retencoes.save(r);
        registro.registrar(new RegistroOperacoes.Operacao("RETENCAO_CONFIGURADA", u.contaId(), empresaId, null, "retencao_empresa", r.getId(), Map.of("categoria", categoria.name(), "meses", String.valueOf(prazoMeses))));
        return new Linha(categoria, categoria.titulo(), categoria.finalidade(), categoria.baseLegal(), categoria.minimoMeses(), r.getPrazoMeses(), categoria.descricaoPrazo(), categoria.sensivel(), contar(categoria, empresaId));
    }

    private br.com.civilconstructorai.comum.UsuarioAtual exigirAdmin(UUID empresaId) {
        var u = contexto.exigir();
        if (!empresaId.equals(u.empresaAtivaId())) {
            throw new ExcecaoDominio.RecursoNaoEncontrado("Empresa");
        }
        if (u.papelEmpresa().filter(p -> p == PapelObra.DONO || p == PapelObra.ADMIN).isEmpty()) {
            throw new ExcecaoDominio.AcessoNegado("Só dono ou administrador da empresa vê o inventário de dados dos trabalhadores.");
        }
        return u;
    }
}
