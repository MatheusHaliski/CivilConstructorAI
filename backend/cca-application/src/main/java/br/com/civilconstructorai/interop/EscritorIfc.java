package br.com.civilconstructorai.interop;

import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.Pavimento;
import br.com.civilconstructorai.notacao.TabelaA;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Escritor IFC4X3 em STEP/ISO-10303-21 (RF15): IfcProject → IfcSite → IfcBuilding → IfcBuildingStorey →
 * IfcWall/IfcColumn/IfcBeam/IfcSlab/IfcDoor/IfcWindow, com IfcPropertySet da Tabela A. GlobalId (22 caracteres
 * base64 IFC) derivado de forma estável do UUID v7 de cada elemento. Sem dados pessoais além do responsável
 * técnico autorizado.
 */
public final class EscritorIfc {

    private static final String B64 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$";

    private EscritorIfc() {
    }

    /** compressão IFC padrão de UUID em 22 caracteres (buildingSMART) */
    public static String globalId(UUID uuid) {
        var bb = ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
        var sb = new StringBuilder();
        // primeiro caractere codifica 2 bits, os demais 20 blocos de 6 bits (128 = 2 + 21*6)
        int bits = 0, acc = 0;
        var out = new StringBuilder();
        for (byte b : bb) {
            acc = (acc << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 6) {
                int idx = (acc >> (bits - 6)) & 0x3F;
                out.append(B64.charAt(idx));
                bits -= 6;
            }
        }
        if (bits > 0) {
            out.append(B64.charAt((acc << (6 - bits)) & 0x3F));
        }
        var s = out.toString();
        // 128 bits → 22 símbolos: usa os 22 primeiros com o primeiro limitado a [0,3]
        sb.append(B64.charAt((bb[0] & 0xC0) >> 6)).append(s, 1, 22);
        return sb.toString();
    }

    private static final class Buf {
        final List<String> linhas = new ArrayList<>();
        int n = 0;

        int add(String entidade) {
            n++;
            linhas.add("#" + n + "=" + entidade + ";");
            return n;
        }
    }

    private static String s(String t) {
        return "'" + (t == null ? "" : t.replace("'", "''")) + "'";
    }

    private static String d(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    public static String escrever(ModeloBim m, String nomeObra, String responsavelTecnico, Instant quando) {
        var b = new Buf();
        var ts = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(quando.atOffset(ZoneOffset.UTC)).substring(0, 19);
        int pessoa = b.add("IFCPERSON($,$," + s(responsavelTecnico) + ",$,$,$,$,$)");
        int org = b.add("IFCORGANIZATION($,'CivilConstructorAI',$,$,$)");
        int po = b.add("IFCPERSONANDORGANIZATION(#" + pessoa + ",#" + org + ",$)");
        int app = b.add("IFCAPPLICATION(#" + org + ",'0.1','CivilConstructorAI','CCA')");
        int hist = b.add("IFCOWNERHISTORY(#" + po + ",#" + app + ",$,.ADDED.,$,$,$," + quando.getEpochSecond() + ")");
        int origem = b.add("IFCCARTESIANPOINT((0.,0.,0.))");
        int dirZ = b.add("IFCDIRECTION((0.,0.,1.))");
        int dirX = b.add("IFCDIRECTION((1.,0.,0.))");
        int placementWorld = b.add("IFCAXIS2PLACEMENT3D(#" + origem + ",#" + dirZ + ",#" + dirX + ")");
        int ctx = b.add("IFCGEOMETRICREPRESENTATIONCONTEXT($,'Model',3,1.0E-5,#" + placementWorld + ",$)");
        int un1 = b.add("IFCSIUNIT(*,.LENGTHUNIT.,$,.METRE.)");
        int un2 = b.add("IFCSIUNIT(*,.AREAUNIT.,$,.SQUARE_METRE.)");
        int un3 = b.add("IFCSIUNIT(*,.VOLUMEUNIT.,$,.CUBIC_METRE.)");
        int units = b.add("IFCUNITASSIGNMENT((#" + un1 + ",#" + un2 + ",#" + un3 + "))");
        int projeto = b.add("IFCPROJECT(" + s(globalId(UUID.nameUUIDFromBytes(("projeto:" + nomeObra).getBytes()))) + ",#" + hist + "," + s(nomeObra) + ",$,$,$,$,(#" + ctx + "),#" + units + ")");
        int lpSite = b.add("IFCLOCALPLACEMENT($,#" + placementWorld + ")");
        int site = b.add("IFCSITE(" + s(globalId(UUID.nameUUIDFromBytes(("site:" + nomeObra).getBytes()))) + ",#" + hist + ",'Terreno',$,$,#" + lpSite + ",$,$,.ELEMENT.,$,$,$,$,$)");
        int lpBld = b.add("IFCLOCALPLACEMENT(#" + lpSite + ",#" + placementWorld + ")");
        int bld = b.add("IFCBUILDING(" + s(globalId(UUID.nameUUIDFromBytes(("building:" + nomeObra).getBytes()))) + ",#" + hist + "," + s(nomeObra) + ",$,$,#" + lpBld + ",$,$,.ELEMENT.,$,$,$)");
        b.add("IFCRELAGGREGATES(" + s(globalId(UUID.nameUUIDFromBytes("agg-proj".getBytes()))) + ",#" + hist + ",$,$,#" + projeto + ",(#" + site + "))");
        b.add("IFCRELAGGREGATES(" + s(globalId(UUID.nameUUIDFromBytes("agg-site".getBytes()))) + ",#" + hist + ",$,$,#" + site + ",(#" + bld + "))");

        var storeys = new ArrayList<Integer>();
        for (Pavimento pav : m.pavimentosOrdenados()) {
            int pt = b.add("IFCCARTESIANPOINT((0.,0.," + d(pav.elevacao()) + "))");
            int pl = b.add("IFCAXIS2PLACEMENT3D(#" + pt + ",#" + dirZ + ",#" + dirX + ")");
            int lp = b.add("IFCLOCALPLACEMENT(#" + lpBld + ",#" + pl + ")");
            int storey = b.add("IFCBUILDINGSTOREY(" + s(globalId(pav.id())) + ",#" + hist + "," + s(pav.nome()) + ",$,$,#" + lp + ",$,$,.ELEMENT.," + d(pav.elevacao()) + ")");
            storeys.add(storey);
            var produtos = new ArrayList<Integer>();
            for (Elemento e : m.elementosDo(pav.id())) {
                if (e.getRascunhoIaId() != null || e.getCamada() == br.com.civilconstructorai.modelo.Enums.Camada.ia_rascunho) {
                    continue; // rascunho de IA não exporta (nem o elemento na camada ia-rascunho sem vínculo)
                }
                Integer id = produto(b, e, m, hist, lp, dirZ, dirX, pav);
                if (id != null) {
                    produtos.add(id);
                    pset(b, e, id, hist);
                }
            }
            if (!produtos.isEmpty()) {
                var lista = String.join(",", produtos.stream().map(i -> "#" + i).toList());
                b.add("IFCRELCONTAINEDINSPATIALSTRUCTURE(" + s(globalId(UUID.nameUUIDFromBytes(("cont:" + pav.id()).getBytes()))) + ",#" + hist + ",$,$,(" + lista + "),#" + storey + ")");
            }
        }
        var ls = String.join(",", storeys.stream().map(i -> "#" + i).toList());
        b.add("IFCRELAGGREGATES(" + s(globalId(UUID.nameUUIDFromBytes("agg-bld".getBytes()))) + ",#" + hist + ",$,$,#" + bld + ",(" + ls + "))");

        var sb = new StringBuilder();
        sb.append("ISO-10303-21;\nHEADER;\nFILE_DESCRIPTION(('ViewDefinition [ReferenceView_V1.2]'),'2;1');\n");
        sb.append("FILE_NAME('").append(nomeObra.replace("'", "")).append(".ifc','").append(ts).append("',('CivilConstructorAI'),('CivilConstructorAI'),'CivilConstructorAI 0.1','CivilConstructorAI','');\n");
        sb.append("FILE_SCHEMA(('IFC4X3_ADD2'));\nENDSEC;\nDATA;\n");
        b.linhas.forEach(l -> sb.append(l).append('\n'));
        sb.append("ENDSEC;\nEND-ISO-10303-21;\n");
        return sb.toString();
    }

    private static Integer produto(Buf b, Elemento e, ModeloBim m, int hist, int lpStorey, int dirZ, int dirX, Pavimento pav) {
        var gid = s(globalId(e.getId()));
        var nome = s(e.getRotulo() != null ? e.getRotulo() : m.rotuloAutomatico(e));
        switch (e.getTipo()) {
            case PAREDE -> {
                var p1 = e.ponto("p1");
                var p2 = e.ponto("p2");
                double l = p1.distancia(p2);
                int rep = extrusaoRetangulo(b, p1.x(), p1.y(), Math.atan2(p2.y() - p1.y(), p2.x() - p1.x()), l, e.num("espessura", 0.15), e.num("altura", 2.8), lpStorey, dirZ, dirX);
                return b.add("IFCWALL(" + gid + ",#" + hist + "," + nome + ",$,$,#" + rep + ",#" + (rep + 1) + ",$,.STANDARD.)");
            }
            case PILAR -> {
                var c = e.ponto("centro");
                double altura = m.pavimento(e.uuid("nivelTopoId")).map(t -> t.elevacao() - pav.elevacao()).filter(h -> h > 0).orElse(3.0);
                int rep = extrusaoRetangulo(b, c.x() - e.num("b", 0.19) / 2, c.y() - e.num("h", 0.4) / 2, 0, e.num("b", 0.19), e.num("h", 0.4), altura, lpStorey, dirZ, dirX);
                return b.add("IFCCOLUMN(" + gid + ",#" + hist + "," + nome + ",$,$,#" + rep + ",#" + (rep + 1) + ",$,.COLUMN.)");
            }
            case VIGA -> {
                var p1 = e.ponto("p1");
                var p2 = e.ponto("p2");
                int rep = extrusaoRetangulo(b, p1.x(), p1.y(), Math.atan2(p2.y() - p1.y(), p2.x() - p1.x()), p1.distancia(p2), e.num("b", 0.12), e.num("h", 0.4), lpStorey, dirZ, dirX);
                return b.add("IFCBEAM(" + gid + ",#" + hist + "," + nome + ",$,$,#" + rep + ",#" + (rep + 1) + ",$,.BEAM.)");
            }
            case LAJE -> {
                var pts = e.pontos("contorno");
                var coords = new ArrayList<String>();
                for (var p : pts) {
                    coords.add("#" + b.add("IFCCARTESIANPOINT((" + d(p.x()) + "," + d(p.y()) + "))"));
                }
                int poly = b.add("IFCPOLYLINE((" + String.join(",", coords) + "," + coords.get(0) + "))");
                int prof = b.add("IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#" + poly + ")");
                int pos = b.add("IFCAXIS2PLACEMENT3D(#" + b.add("IFCCARTESIANPOINT((0.,0.,0.))") + ",#" + dirZ + ",#" + dirX + ")");
                int solid = b.add("IFCEXTRUDEDAREASOLID(#" + prof + ",#" + pos + ",#" + dirZ + "," + d(e.num("h", 0.12)) + ")");
                int shape = b.add("IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#" + solid + "))");
                int pds = b.add("IFCPRODUCTDEFINITIONSHAPE($,$,(#" + shape + "))");
                int lp = b.add("IFCLOCALPLACEMENT(#" + lpStorey + ",#" + pos + ")");
                return b.add("IFCSLAB(" + gid + ",#" + hist + "," + nome + ",$,$,#" + lp + ",#" + pds + ",$,.FLOOR.)");
            }
            case PORTA, JANELA -> {
                var p = m.pontoRepresentativo(e);
                int pt = b.add("IFCCARTESIANPOINT((" + d(p.x()) + "," + d(p.y()) + "," + d(e.num("peitoril", 0)) + "))");
                int pl = b.add("IFCAXIS2PLACEMENT3D(#" + pt + ",#" + dirZ + ",#" + dirX + ")");
                int lp = b.add("IFCLOCALPLACEMENT(#" + lpStorey + ",#" + pl + ")");
                var tipo = e.getTipo() == TipoElemento.PORTA ? "IFCDOOR" : "IFCWINDOW";
                var pre = e.getTipo() == TipoElemento.PORTA ? ".DOOR." : ".WINDOW.";
                return b.add(tipo + "(" + gid + ",#" + hist + "," + nome + ",$,$,#" + lp + ",$,$," + d(e.num("altura", 2.1)) + "," + d(e.num("largura", 0.8)) + "," + pre + ",$,$)");
            }
            case SAPATA, BLOCO, RADIER -> {
                var c = e.ponto("centro");
                int rep = extrusaoRetangulo(b, c.x() - e.num("bx", 1.5) / 2, c.y() - e.num("by", 1.5) / 2, 0, e.num("bx", 1.5), e.num("by", 1.5), e.num("h", 0.5), lpStorey, dirZ, dirX);
                var pre = e.getTipo() == TipoElemento.SAPATA ? ".PAD_FOOTING." : e.getTipo() == TipoElemento.BLOCO ? ".PILE_CAP." : ".FOOTING_BEAM.";
                return b.add("IFCFOOTING(" + gid + ",#" + hist + "," + nome + ",$,$,#" + rep + ",#" + (rep + 1) + ",$," + pre + ")");
            }
            default -> {
                return null;
            }
        }
    }

    /** sólido extrudado de um retângulo (comprimento × largura) girado de `ang` a partir de (x,y); devolve o id do placement (o PDS é id+1) */
    private static int extrusaoRetangulo(Buf b, double x, double y, double ang, double comprimento, double largura, double altura, int lpStorey, int dirZ, int dirX) {
        int pt = b.add("IFCCARTESIANPOINT((" + d(x) + "," + d(y) + ",0.))");
        int dx = b.add("IFCDIRECTION((" + d(Math.cos(ang)) + "," + d(Math.sin(ang)) + ",0.))");
        int pl = b.add("IFCAXIS2PLACEMENT3D(#" + pt + ",#" + dirZ + ",#" + dx + ")");
        int centro2d = b.add("IFCCARTESIANPOINT((" + d(comprimento / 2) + "," + d(largura / 2) + "))");
        int pl2d = b.add("IFCAXIS2PLACEMENT2D(#" + centro2d + ",$)");
        int prof = b.add("IFCRECTANGLEPROFILEDEF(.AREA.,$,#" + pl2d + "," + d(comprimento) + "," + d(largura) + ")");
        int pos0 = b.add("IFCAXIS2PLACEMENT3D(#" + b.add("IFCCARTESIANPOINT((0.,0.,0.))") + ",#" + dirZ + ",#" + dirX + ")");
        int solid = b.add("IFCEXTRUDEDAREASOLID(#" + prof + ",#" + pos0 + ",#" + dirZ + "," + d(altura) + ")");
        int shape = b.add("IFCSHAPEREPRESENTATION($,'Body','SweptSolid',(#" + solid + "))");
        int lp = b.add("IFCLOCALPLACEMENT(#" + lpStorey + ",#" + pl + ")");
        b.add("IFCPRODUCTDEFINITIONSHAPE($,$,(#" + shape + "))");
        return lp;
    }

    private static void pset(Buf b, Elemento e, int produto, int hist) {
        var mat = e.getMaterialId() == null ? null : TabelaA.material(e.getMaterialId()).orElse(null);
        var props = new ArrayList<String>();
        props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Camada',$,IFCLABEL(" + s(e.getCamada().rotulo()) + "),$)"));
        props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Origem',$,IFCLABEL(" + s(e.getOrigem().name()) + "),$)"));
        if (mat != null) {
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('MaterialId',$,IFCLABEL(" + s(mat.materialId()) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Material',$,IFCLABEL(" + s(mat.name()) + "),$)"));
            if (mat.physicalProperties().fck_MPa() != null) {
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('fck_MPa',$,IFCREAL(" + d(mat.physicalProperties().fck_MPa()) + "),$)"));
            }
            if (mat.physicalProperties().fireRating() != null) {
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('FireRating',$,IFCLABEL(" + s(mat.physicalProperties().fireRating()) + "),$)"));
            }
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('LoadBearing',$,IFCBOOLEAN(" + (mat.physicalProperties().loadBearing() ? ".T." : ".F.") + "),$)"));
            if (mat.baseQuantities().sinapi() != null) {
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('SINAPI',$,IFCLABEL(" + s(mat.baseQuantities().sinapi()) + "),$)"));
            }
        }
        int ps = b.add("IFCPROPERTYSET(" + s(globalId(UUID.nameUUIDFromBytes(("pset:" + e.getId()).getBytes()))) + ",#" + hist + ",'Pset_TabelaA',$,(" + String.join(",", props) + "))");
        b.add("IFCRELDEFINESBYPROPERTIES(" + s(globalId(UUID.nameUUIDFromBytes(("rel:" + e.getId()).getBytes()))) + ",#" + hist + ",$,$,(#" + produto + "),#" + ps + ")");
    }
}
