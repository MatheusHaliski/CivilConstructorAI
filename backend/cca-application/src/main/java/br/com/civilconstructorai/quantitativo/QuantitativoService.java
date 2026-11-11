package br.com.civilconstructorai.quantitativo;

import br.com.civilconstructorai.acesso.AcessoObra;
import br.com.civilconstructorai.acesso.PapelObra;
import br.com.civilconstructorai.comum.ContextoUsuario;
import br.com.civilconstructorai.comum.ExcecaoDominio;
import br.com.civilconstructorai.estrutura.DimensionamentoService;
import br.com.civilconstructorai.estrutura.Json;
import br.com.civilconstructorai.estrutura.ModeloEstruturalFonte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** RF11 · levantamento quantitativo determinístico, exportação CSV/XLSX e liberação ao cliente (DN-02). */
@Service
@Transactional
public class QuantitativoService {

    @Repository("quantitativoLevantamentos")

    public interface Levantamentos extends JpaRepository<LevantamentoQuantitativo, UUID> {
        List<LevantamentoQuantitativo> findByObraIdOrderByCriadoEmDesc(UUID obraId);

        Optional<LevantamentoQuantitativo> findByIdAndObraId(UUID id, UUID obraId);
    }

    public record Visao(UUID id, long versaoModelo, String hash, String estado, boolean liberadoCliente,
                        List<Quantificador.Linha> linhas, Map<String, Quantificador.Totais> totaisPorPavimento, Quantificador.Totais totalGeral, List<br.com.civilconstructorai.acabamento.MedidorAcabamentos.Linha> acabamentos) {
    }

    private final Levantamentos levantamentos;
    private final ModeloEstruturalFonte modelo;
    private final DimensionamentoService dimensionamentos;
    private final AcessoObra acesso;
    private final ContextoUsuario contexto;
    private final Json json;

    public QuantitativoService(Levantamentos levantamentos, ModeloEstruturalFonte modelo, DimensionamentoService dimensionamentos, AcessoObra acesso, ContextoUsuario contexto, Json json) {
        this.levantamentos = levantamentos;
        this.modelo = modelo;
        this.dimensionamentos = dimensionamentos;
        this.acesso = acesso;
        this.contexto = contexto;
        this.json = json;
    }

    /** calcula (ou reaproveita) o levantamento da versão atual do modelo */
    public Visao atual(UUID obraId) {
        acesso.exigirMembro(obraId);
        var u = contexto.exigir();
        var m = modelo.carregar(obraId);
        var aco = dimensionamentos.acoAprovadoPorElemento(obraId, m.versao());
        var r = Quantificador.quantificar(m, aco);
        LevantamentoQuantitativo lev = null;
        for (var l : levantamentos.findByObraIdOrderByCriadoEmDesc(obraId)) {
            if (l.getVersaoModelo() == m.versao() && l.getHash().equals(r.hash()) && lev == null) {
                lev = l;
            } else if (l.getVersaoModelo() != m.versao()) {
                l.tornarObsoleto();
            }
        }
        if (lev == null) {
            lev = levantamentos.save(new LevantamentoQuantitativo(u.empresaAtivaId(), obraId, m.versao(), r.hash(), json.escrever(r.linhas()), json.escrever(Map.of("totaisPorPavimento", r.totaisPorPavimento(), "acabamentos", r.acabamentos()))));
        }
        boolean cliente = u.papeisNaObra(obraId).stream().allMatch(p -> p == PapelObra.CLIENTE) && !u.papeisNaObra(obraId).isEmpty();
        if (cliente) {
            if (!lev.isLiberadoCliente()) {
                throw new ExcecaoDominio.RecursoNaoEncontrado("Quantitativo");
            }
            return new Visao(lev.getId(), lev.getVersaoModelo(), lev.getHash(), lev.getEstado().name(), true, List.of(), r.totaisPorPavimento(), r.totalGeral(), List.of());
        }
        return new Visao(lev.getId(), lev.getVersaoModelo(), lev.getHash(), lev.getEstado().name(), lev.isLiberadoCliente(), r.linhas(), r.totaisPorPavimento(), r.totalGeral(), r.acabamentos());
    }

    public void liberarParaCliente(UUID obraId, UUID levantamentoId, boolean liberar) {
        acesso.exigirMembro(obraId);
        acesso.exigirPapel(obraId, PapelObra.ENGENHEIRO, PapelObra.DONO, PapelObra.ADMIN);
        var l = levantamentos.findByIdAndObraId(levantamentoId, obraId).orElseThrow(() -> new ExcecaoDominio.RecursoNaoEncontrado("Quantitativo"));
        l.liberarParaCliente(liberar);
    }

    @Transactional(readOnly = true)
    public byte[] exportarCsv(UUID obraId) {
        var v = atual(obraId);
        var sb = new StringBuilder("pavimento;elemento;tipo;rotulo;material;concreto_m3;forma_m2;aco_kg;alvenaria_m2;origem_aco\n");
        for (var l : v.linhas()) {
            sb.append(String.format(Locale.ROOT, "%s;%s;%s;%s;%s;%.3f;%.3f;%.3f;%.3f;%s%n", l.pavimentoId(), l.elementoId(), l.tipo(), l.rotulo(),
                    l.materialId() == null ? "" : l.materialId(), l.concretoM3(), l.formaM2(), l.acoKg(), l.alvenariaM2(), l.origemAco()));
        }
        if (!v.acabamentos().isEmpty()) {
            sb.append("\npavimento;elemento;camada;material;face;espessura_m;area_bruta_m2;desconto_vaos_m2;area_liquida_m2;volume_m3;peso_instalado_kN;compra_m2;pecas_compra;especificacao;largura_peca_m;altura_peca_m;junta_m;perda_percentual\n");
            for(var a:v.acabamentos()) sb.append(String.format(Locale.ROOT,"%s;%s;%s;%s;%s;%.6f;%.6f;%.6f;%.6f;%.6f;%s;%.6f;%s;%s;%s;%s;%.6f;%.6f%n",csv(a.pavimentoId()),csv(a.elementoId()),csv(a.id()),csv(a.materialId()),a.face(),a.espessura_m(),a.areaBrutaM2(),a.descontoVaosM2(),a.areaLiquidaM2(),a.volumeM3(),a.pesoInstaladoKN()==null?"":a.pesoInstaladoKN(),a.quantidadeCompraM2(),a.pecasCompra()==null?"":a.pecasCompra(),csv(a.especificacao()),a.larguraPeca_m()==null?"":a.larguraPeca_m(),a.alturaPeca_m()==null?"":a.alturaPeca_m(),a.junta_m(),a.perda_percentual()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** XLSX mínimo (OOXML) gerado com java.util.zip: uma planilha, números tabulares, sem dependência externa */
    @Transactional(readOnly = true)
    public byte[] exportarXlsx(UUID obraId) {
        var v = atual(obraId);
        var cabecalho = List.of("Pavimento", "Elemento", "Tipo", "Rótulo", "Material", "Concreto (m³)", "Fôrma (m²)", "Aço (kg)", "Alvenaria (m²)", "Origem do aço");
        var sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        sheet.append("<row r=\"1\">");
        for (int c = 0; c < cabecalho.size(); c++) {
            sheet.append("<c t=\"inlineStr\"><is><t>").append(esc(cabecalho.get(c))).append("</t></is></c>");
        }
        sheet.append("</row>");
        int r = 2;
        for (var l : v.linhas()) {
            sheet.append("<row r=\"").append(r++).append("\">");
            for (var s : List.of(l.pavimentoId(), l.elementoId(), l.tipo().name(), l.rotulo(), l.materialId() == null ? "" : l.materialId())) {
                sheet.append("<c t=\"inlineStr\"><is><t>").append(esc(s)).append("</t></is></c>");
            }
            for (double n : new double[]{l.concretoM3(), l.formaM2(), l.acoKg(), l.alvenariaM2()}) {
                sheet.append("<c><v>").append(n).append("</v></c>");
            }
            sheet.append("<c t=\"inlineStr\"><is><t>").append(esc(l.origemAco())).append("</t></is></c></row>");
        }
        if (!v.acabamentos().isEmpty()) {
            sheet.append("<row/>");
            sheet.append("<row>");
            for(String h:List.of("Pavimento","Elemento","Camada","Material","Face","Espessura (m)","Área bruta (m²)","Vãos (m²)","Área líquida (m²)","Volume (m³)","Peso instalado (kN)","Compra (m²)","Peças para compra","Especificação","Largura peça (m)","Altura peça (m)","Junta (m)","Perda (%)")) celulaTexto(sheet,h);
            sheet.append("</row>");
            for(var a:v.acabamentos()) {
                sheet.append("<row>");
                for(String t:List.of(a.pavimentoId(),a.elementoId(),a.id(),a.materialId(),a.face())) celulaTexto(sheet,t);
                for(Double n:new Double[]{a.espessura_m(),a.areaBrutaM2(),a.descontoVaosM2(),a.areaLiquidaM2(),a.volumeM3(),a.pesoInstaladoKN(),a.quantidadeCompraM2(),a.pecasCompra()}) { if(n==null) sheet.append("<c/>"); else sheet.append("<c><v>").append(n).append("</v></c>"); }
                celulaTexto(sheet,a.especificacao());
                for(Double n:new Double[]{a.larguraPeca_m(),a.alturaPeca_m(),a.junta_m(),a.perda_percentual()}) { if(n==null) sheet.append("<c/>"); else sheet.append("<c><v>").append(n).append("</v></c>"); }
                sheet.append("</row>");
            }
        }
        sheet.append("</sheetData></worksheet>");
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            entrada(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            entrada(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entrada(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Quantitativos\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entrada(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entrada(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static void entrada(ZipOutputStream zip, String nome, String conteudo) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void celulaTexto(StringBuilder s,String t) { s.append("<c t=\"inlineStr\"><is><t>").append(esc(t)).append("</t></is></c>"); }
    private static String csv(String s) { if(s==null)return ""; return "\"" + s.replace("\"","\"\"") + "\""; }
    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Transactional(readOnly = true)
    public List<Quantificador.Linha> linhasDe(LevantamentoQuantitativo l) {
        return json.ler(l.getLinhasJson(), new TypeReference<List<Quantificador.Linha>>() { });
    }
}
