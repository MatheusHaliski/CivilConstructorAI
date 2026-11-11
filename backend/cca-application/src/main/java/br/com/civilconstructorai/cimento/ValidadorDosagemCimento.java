package br.com.civilconstructorai.cimento;

import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.comum.Relogio;
import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.Pavimento;
import br.com.civilconstructorai.modelo.Eixo;
import br.com.civilconstructorai.notacao.TabelaA;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/** Guarda central de escrita, independente do serviço do modelo para evitar dependência circular. */
@Service
public class ValidadorDosagemCimento {
    private final DosagemCimentoRepository repository;
    private final ObjectMapper mapper;
    private final Relogio relogio;
    public ValidadorDosagemCimento(DosagemCimentoRepository r, ObjectMapper m, Relogio clock) { repository=r; mapper=m; relogio=clock; }
    public String contextoHash(List<Elemento> elementos) {
        return contextoHash(elementos,List.of(),List.of());
    }
    public String contextoHash(List<Elemento> elementos,List<Pavimento> pavimentos,List<Eixo> eixos) {
        var itens=elementos.stream().filter(e->TabelaA.material(e.getMaterialId()).map(m->m.physicalProperties().fck_MPa()!=null).orElse(false))
            .sorted(Comparator.comparing(e->e.getId().toString())).map(e->{
                var props=new TreeMap<>(e.getProps());
                for(var k:List.of("cimentoId","dosagemCimentoId","acabamentos")) props.remove(k);
                return Map.of("id",e.getId(),"tipo",e.getTipo(),"materialId",e.getMaterialId(),"pavimentoId",e.getPavimentoId(),"props",ordenar(props));
            }).toList();
        var contexto=Map.of("elementos",itens,"pavimentos",pavimentos,"eixos",eixos);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(ordenar(contexto)).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Object ordenar(Object v) {
        if (v instanceof Map<?,?> m) { var t=new TreeMap<String,Object>(); m.forEach((k,x)->t.put(k.toString(),ordenar(x))); return t; }
        if (v instanceof List<?> l) return l.stream().map(this::ordenar).toList();
        return v;
    }
    public void validarModelo(UUID obraId, ModeloBim modelo, long versao) {
        var hash=contextoHash(modelo.elementos(),modelo.pavimentosOrdenados(),modelo.eixos());
        for(var e:modelo.elementos()) {
            String cimento=e.texto("cimentoId"); String dose=e.texto("dosagemCimentoId");
            if ((cimento==null || cimento.isBlank()) && (dose==null || dose.isBlank())) continue;
            var fck=TabelaA.material(e.getMaterialId()).map(m->m.physicalProperties().fck_MPa()).orElse(null);
            if(fck==null) falhar("Cimento estrutural exige material com fck especificado.");
            UUID id;
            try { id=UUID.fromString(dose); } catch(RuntimeException ex) { throw erro("Informe dosagemCimentoId válida junto com cimentoId."); }
            validar(obraId,id,cimento,fck,versao,hash);
        }
    }
    public DosagemCimento validar(UUID obraId, UUID id, String cimentoId, double fck, long versao, String contextoHash) {
        var d=repository.findByIdAndObraId(id,obraId).orElseThrow(()->erro("Dosagem não encontrada nesta obra."));
        var r=mapper.readValue(d.getDadosJson(),DosagemCimentoService.Registro.class);
        var hoje=LocalDate.ofInstant(relogio.agora(),ZoneOffset.UTC);
        if (!"VALIDADO".equals(d.getEstado()) || d.getConfirmadoPor()==null || d.getConfirmadoEm()==null || d.getReferenciaValidacao()==null
            || !d.getContextoHash().equals(contextoHash) || d.getVersaoModelo()>versao
            || !r.cimentoId().equals(cimentoId) || r.fckProjetoMPa()!=fck || !Double.isFinite(r.fckComprovadoMPa()) || r.fckComprovadoMPa()<fck
            || r.validoAte()==null || r.validoAte().isBefore(hoje)) falhar("Dosagem sem confirmação, expirada ou incompatível com cimento, fck e contexto estrutural atuais.");
        return d;
    }
    private void falhar(String m) { throw erro(m); }
    private ExcecaoDominio.RegraDeNegocio erro(String m) { return new ExcecaoDominio.RegraDeNegocio("cimento_dosagem_nao_verificada",m); }
}
