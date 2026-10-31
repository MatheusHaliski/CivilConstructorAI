package br.com.civilconstructorai.interop;

import br.com.civilconstructorai.modelo.Elemento;
import br.com.civilconstructorai.modelo.Enums.TipoElemento;
import br.com.civilconstructorai.modelo.ModeloBim;
import br.com.civilconstructorai.modelo.ObjetosPrediais;
import br.com.civilconstructorai.modelo.Pavimento;
import br.com.civilconstructorai.notacao.CatalogoMateriais;
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
 * <p>
 * Objetos da biblioteca (AUD-02/03/04) saem com a entidade indicada no catálogo (IfcFurniture, IfcElectricAppliance,
 * IfcSanitaryTerminal, IfcOutlet...), caixa envolvente como corpo e Pset_CivilConstructorObjeto; cada conector vira
 * IfcDistributionPort (SOURCE = saída, SINK = entrada) aninhado ao objeto (IfcRelNests) e cada CONEXAO vira
 * IfcRelConnectsPorts entre as duas portas (conexão física). O circuito elétrico e o sistema hidrossanitário (ligação
 * lógica) saem como IfcDistributionCircuit/IfcDistributionSystem com IfcRelAssignsToGroup. O percurso esquemático
 * (representação gráfica) não é exportado como tubo ou cabo. Pessoas não são exportadas: não são elementos da
 * edificação. Ambiente vira IfcSpace (só posição e nome; o contorno não é modelado).
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
        /** contexto geométrico 'Model': obrigatório em toda IfcShapeRepresentation (ContextOfItems) */
        int ctx;

        int add(String entidade) {
            n++;
            linhas.add("#" + n + "=" + entidade + ";");
            return n;
        }
    }

    /** literal STEP: apóstrofo e barra invertida escapados; fora do ASCII imprimível vai como \\X2\\HHHH\\X0\\ (UTF-16), exigido pela ISO 10303-21 */
    static String s(String t) {
        var sb = new StringBuilder("'");
        if (t != null) {
            int k = 0;
            while (k < t.length()) {
                char ch = t.charAt(k);
                if (ch >= 0x20 && ch <= 0x7E) {
                    sb.append(ch == '\'' ? "''" : ch == '\\' ? "\\\\" : String.valueOf(ch));
                    k++;
                } else {
                    sb.append("\\X2\\");
                    while (k < t.length() && (t.charAt(k) < 0x20 || t.charAt(k) > 0x7E)) {
                        sb.append(String.format("%04X", (int) t.charAt(k)));
                        k++;
                    }
                    sb.append("\\X0\\");
                }
            }
        }
        return sb.append("'").toString();
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
        b.ctx = ctx;
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
        // IFC: cada porta liga-se a uma única outra; conector que aceita várias ligações (quadro, entrada, caixa) vira uma porta por ligação
        var portas = new java.util.HashMap<String, java.util.ArrayDeque<Integer>>();
        var usos = new java.util.HashMap<String, Integer>();
        for (var c : m.elementos()) {
            if (c.getTipo() == TipoElemento.CONEXAO && c.getRascunhoIaId() == null) {
                for (var lado : List.of("de", "para")) {
                    var pt = ObjetosPrediais.ponta(c, lado);
                    usos.merge(pt.get("id") + ":" + pt.get("conector"), 1, Integer::sum);
                }
            }
        }
        var produtosPorId = new java.util.HashMap<UUID, Integer>();
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
                if (e.getTipo() == TipoElemento.AMBIENTE) {
                    var p = e.ponto("ponto");
                    int spl = b.add("IFCLOCALPLACEMENT(#" + lp + ",#" + b.add("IFCAXIS2PLACEMENT3D(#" + b.add("IFCCARTESIANPOINT((" + d(p.x()) + "," + d(p.y()) + ",0.))") + ",#" + dirZ + ",#" + dirX + ")") + ")");
                    int sp = b.add("IFCSPACE(" + s(globalId(e.getId())) + ",#" + hist + "," + s(e.texto("nome") == null ? "Ambiente" : e.texto("nome")) + ",$,$,#" + spl + ",$,$,.ELEMENT.,.SPACE.,$)");
                    b.add("IFCRELAGGREGATES(" + s(globalId(UUID.nameUUIDFromBytes(("agg-esp:" + e.getId()).getBytes()))) + ",#" + hist + ",$,$,#" + storey + ",(#" + sp + "))");
                    continue;
                }
                Integer id = e.getTipo() == TipoElemento.OBJETO ? objeto(b, e, m, hist, lp, dirZ, dirX, pav, portas, usos) : produto(b, e, m, hist, lp, dirZ, dirX, pav);
                if (id != null) {
                    produtos.add(id);
                    produtosPorId.put(e.getId(), id);
                    pset(b, e, id, hist);
                }
            }
            if (!produtos.isEmpty()) {
                var lista = String.join(",", produtos.stream().map(i -> "#" + i).toList());
                b.add("IFCRELCONTAINEDINSPATIALSTRUCTURE(" + s(globalId(UUID.nameUUIDFromBytes(("cont:" + pav.id()).getBytes()))) + ",#" + hist + ",$,$,(" + lista + "),#" + storey + ")");
            }
        }
        conexoes(b, m, hist, portas, produtosPorId);
        var ls = String.join(",", storeys.stream().map(i -> "#" + i).toList());
        b.add("IFCRELAGGREGATES(" + s(globalId(UUID.nameUUIDFromBytes("agg-bld".getBytes()))) + ",#" + hist + ",$,$,#" + bld + ",(" + ls + "))");

        var sb = new StringBuilder();
        sb.append("ISO-10303-21;\nHEADER;\nFILE_DESCRIPTION(('ViewDefinition [ReferenceView_V1.2]'),'2;1');\n");
        sb.append("FILE_NAME(").append(s(nomeObra + ".ifc")).append(",'").append(ts).append("',('CivilConstructorAI'),('CivilConstructorAI'),'CivilConstructorAI 0.1','CivilConstructorAI','');\n");
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
                int shape = b.add("IFCSHAPEREPRESENTATION(#" + b.ctx + ",'Body','SweptSolid',(#" + solid + "))");
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
            case ESCADA, RAMPA -> {
                // perfil lateral (dente de serra ou cunha) no plano vertical do lance, extrudado pela largura
                var a = e.ponto("p1");
                var b2 = e.ponto("p2");
                double L = a.distancia(b2), larg = e.num("largura", 1.0);
                double ux = (b2.x() - a.x()) / L, uy = (b2.y() - a.y()) / L;
                var coords = new ArrayList<String>();
                for (var q : br.com.civilconstructorai.modelo.Circulacao.perfil(e)) {
                    coords.add("#" + b.add("IFCCARTESIANPOINT((" + d(q[0]) + "," + d(q[1]) + "))"));
                }
                int poly = b.add("IFCPOLYLINE((" + String.join(",", coords) + "," + coords.get(0) + "))");
                int prof = b.add("IFCARBITRARYCLOSEDPROFILEDEF(.AREA.,$,#" + poly + ")");
                // eixo Z do sólido = lado direito do lance (uy, −ux); X = sentido do lance; Y = Z × X = vertical
                int pos = b.add("IFCAXIS2PLACEMENT3D(#" + b.add("IFCCARTESIANPOINT((" + d(a.x() - uy * larg / 2) + "," + d(a.y() + ux * larg / 2) + ",0.))") + ",#"
                        + b.add("IFCDIRECTION((" + d(uy) + "," + d(-ux) + ",0.))") + ",#" + b.add("IFCDIRECTION((" + d(ux) + "," + d(uy) + ",0.))") + ")");
                int solid = b.add("IFCEXTRUDEDAREASOLID(#" + prof + ",#" + pos + ",#" + dirZ + "," + d(larg) + ")");
                int shape = b.add("IFCSHAPEREPRESENTATION(#" + b.ctx + ",'Body','SweptSolid',(#" + solid + "))");
                int pds = b.add("IFCPRODUCTDEFINITIONSHAPE($,$,(#" + shape + "))");
                int lp = b.add("IFCLOCALPLACEMENT(#" + lpStorey + ",#" + b.add("IFCAXIS2PLACEMENT3D(#" + b.add("IFCCARTESIANPOINT((0.,0.,0.))") + ",#" + dirZ + ",#" + dirX + ")") + ")");
                return e.getTipo() == TipoElemento.ESCADA
                        ? b.add("IFCSTAIR(" + gid + ",#" + hist + "," + nome + ",$,$,#" + lp + ",#" + pds + ",$,.STRAIGHT_RUN_STAIR.)")
                        : b.add("IFCRAMP(" + gid + ",#" + hist + "," + nome + ",$,$,#" + lp + ",#" + pds + ",$,.STRAIGHT_RUN_RAMP.)");
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

    /** entidades sem PredefinedType (8 atributos); as demais recebem .NOTDEFINED. e o nome do item em ObjectType */
    private static final java.util.Set<String> SEM_TIPO_PREDEFINIDO = java.util.Set.of("IFCFLOWTERMINAL", "IFCFURNISHINGELEMENT");

    private static Integer objeto(Buf b, Elemento e, ModeloBim m, int hist, int lpStorey, int dirZ, int dirX, Pavimento pav, java.util.Map<String, java.util.ArrayDeque<Integer>> portas,
                                  java.util.Map<String, Integer> usos) {
        var it = ObjetosPrediais.item(e).orElse(null);
        if (it == null || it.ifc() == null || it.ifc().isBlank()) {
            return null; // pessoas e itens sem entidade IFC
        }
        var dim = ObjetosPrediais.dim(e);
        var c = e.ponto("centro");
        double ang = Math.toRadians(e.num("rotacao", 0)), ca = Math.cos(ang), sa = Math.sin(ang);
        double x0 = c.x() - ca * dim[0] / 2 + sa * dim[1] / 2, y0 = c.y() - sa * dim[0] / 2 - ca * dim[1] / 2;
        int rep = extrusaoRetangulo(b, x0, y0, e.num("elevacao", 0), ang, dim[0], dim[1], dim[2], lpStorey, dirZ, dirX);
        var gid = s(globalId(e.getId()));
        var nome = s(it.nome() + " " + (e.getRotulo() != null ? e.getRotulo() : m.rotuloAutomatico(e)));
        int prod = SEM_TIPO_PREDEFINIDO.contains(it.ifc())
                ? b.add(it.ifc() + "(" + gid + ",#" + hist + "," + nome + ",$," + s(it.nome()) + ",#" + rep + ",#" + (rep + 1) + "," + s(it.id()) + ")")
                : b.add(it.ifc() + "(" + gid + ",#" + hist + "," + nome + ",$," + s(it.nome()) + ",#" + rep + ",#" + (rep + 1) + "," + s(it.id()) + ",.NOTDEFINED.)");
        var ids = new ArrayList<String>();
        for (var con : it.conectores()) {
            var g = ObjetosPrediais.posicaoConector(m, e, con.id()).orElseThrow();
            int pl = b.add("IFCLOCALPLACEMENT(#" + lpStorey + ",#" + b.add("IFCAXIS2PLACEMENT3D(#" + b.add("IFCCARTESIANPOINT((" + d(g.x()) + "," + d(g.y()) + "," + d(g.z() - pav.elevacao()) + "))") + ",#" + dirZ + ",#" + dirX + ")") + ")");
            int n = Math.max(1, usos.getOrDefault(e.getId() + ":" + con.id(), 0));
            var fila = portas.computeIfAbsent(e.getId() + ":" + con.id(), k -> new java.util.ArrayDeque<>());
            for (int k = 1; k <= n; k++) {
                String nomePorta = n == 1 ? con.id() : con.id() + "/" + k;
                int porta = b.add("IFCDISTRIBUTIONPORT(" + s(globalId(UUID.nameUUIDFromBytes(("porta:" + e.getId() + ":" + (k == 1 ? con.id() : nomePorta)).getBytes()))) + ",#" + hist + "," + s(nomePorta) + ","
                        + s(ObjetosPrediais.rotuloSistema(con.sistema()) + (con.papel() == null ? "" : " · " + con.papel())) + ",$,#" + pl + ",$," + ("SAI".equals(con.fluxo()) ? ".SOURCE." : ".SINK.") + ","
                        + ("ELETRICA".equals(con.sistema()) ? ".CABLE." : ".PIPE.") + "," + sistemaIfc(con.sistema()) + ")");
                fila.add(porta);
                ids.add("#" + porta);
            }
        }
        if (!ids.isEmpty()) {
            b.add("IFCRELNESTS(" + s(globalId(UUID.nameUUIDFromBytes(("portas:" + e.getId()).getBytes()))) + ",#" + hist + ",'Portas',$,#" + prod + ",(" + String.join(",", ids) + "))");
        }
        return prod;
    }

    static String sistemaIfc(String sistema) {
        return switch (sistema == null ? "" : sistema) {
            case "ELETRICA" -> ".ELECTRICAL.";
            case "AGUA_FRIA" -> ".DOMESTICCOLDWATER.";
            case "AGUA_QUENTE" -> ".DOMESTICHOTWATER.";
            case "ESGOTO" -> ".SEWAGE.";
            case "VENTILACAO" -> ".VENT.";
            default -> ".NOTDEFINED.";
        };
    }

    /** conexão física (IfcRelConnectsPorts) e ligação lógica (circuito/sistema como grupo) */
    private static void conexoes(Buf b, ModeloBim m, int hist, java.util.Map<String, java.util.ArrayDeque<Integer>> portas, java.util.Map<UUID, Integer> produtos) {
        var grupos = new java.util.LinkedHashMap<String, java.util.LinkedHashSet<Integer>>();
        for (var c : m.elementos()) {
            if (c.getTipo() != TipoElemento.CONEXAO || c.getRascunhoIaId() != null) {
                continue;
            }
            var de = ObjetosPrediais.ponta(c, "de");
            var para = ObjetosPrediais.ponta(c, "para");
            var fa = portas.get(de.get("id") + ":" + de.get("conector"));
            var fb = portas.get(para.get("id") + ":" + para.get("conector"));
            if (fa == null || fb == null || fa.isEmpty() || fb.isEmpty()) {
                continue;
            }
            int pa = fa.poll(), pb = fb.poll();
            String sistema = c.texto("sistema");
            double comprimento = ObjetosPrediais.comprimento(ObjetosPrediais.percurso(m, c));
            String desc = ObjetosPrediais.rotuloSistema(sistema) + String.format(Locale.ROOT, "; percurso esquemático %.2f m", comprimento)
                    + ("ELETRICA".equals(sistema) ? String.format(Locale.ROOT, "; %.1f mm²", c.num("secaoMm2", 2.5)) : String.format(Locale.ROOT, "; DN %.0f", c.num("dnMm", 0)))
                    + (c.texto("materialId") == null ? "" : "; " + c.texto("materialId"));
            b.add("IFCRELCONNECTSPORTS(" + s(globalId(c.getId())) + ",#" + hist + "," + s(m.rotuloAutomatico(c)) + "," + s(desc) + ",#" + pa + ",#" + pb + ",$)");
            String grupo = "ELETRICA".equals(sistema) ? "Circuito " + (c.texto("circuito") == null ? "C1" : c.texto("circuito")) : "Sistema " + ObjetosPrediais.rotuloSistema(sistema);
            var membros = grupos.computeIfAbsent(sistema + "|" + grupo, k -> new java.util.LinkedHashSet<>());
            for (var ponta : List.of(de, para)) {
                try {
                    var p = produtos.get(UUID.fromString(String.valueOf(ponta.get("id"))));
                    if (p != null) {
                        membros.add(p);
                    }
                } catch (IllegalArgumentException ignored) {
                    // ponta sem objeto válido: a conexão já foi descartada acima
                }
            }
        }
        for (var g : grupos.entrySet()) {
            var partes = g.getKey().split("\\|", 2);
            String entidade = "ELETRICA".equals(partes[0]) ? "IFCDISTRIBUTIONCIRCUIT" : "IFCDISTRIBUTIONSYSTEM";
            int grupo = b.add(entidade + "(" + s(globalId(UUID.nameUUIDFromBytes(("grupo:" + g.getKey()).getBytes()))) + ",#" + hist + "," + s(partes[1]) + ",$,$,$," + sistemaIfc(partes[0]) + ")");
            b.add("IFCRELASSIGNSTOGROUP(" + s(globalId(UUID.nameUUIDFromBytes(("membros:" + g.getKey()).getBytes()))) + ",#" + hist + ",$,$,("
                    + String.join(",", g.getValue().stream().map(i -> "#" + i).toList()) + "),$,#" + grupo + ")");
        }
    }

    private static int extrusaoRetangulo(Buf b, double x, double y, double ang, double comprimento, double largura, double altura, int lpStorey, int dirZ, int dirX) {
        return extrusaoRetangulo(b, x, y, 0, ang, comprimento, largura, altura, lpStorey, dirZ, dirX);
    }

    /** sólido extrudado de um retângulo (comprimento × largura) girado de `ang` a partir de (x,y); devolve o id do placement (o PDS é id+1) */
    private static int extrusaoRetangulo(Buf b, double x, double y, double z, double ang, double comprimento, double largura, double altura, int lpStorey, int dirZ, int dirX) {
        int pt = b.add("IFCCARTESIANPOINT((" + d(x) + "," + d(y) + "," + d(z) + "))");
        int dx = b.add("IFCDIRECTION((" + d(Math.cos(ang)) + "," + d(Math.sin(ang)) + ",0.))");
        int pl = b.add("IFCAXIS2PLACEMENT3D(#" + pt + ",#" + dirZ + ",#" + dx + ")");
        int centro2d = b.add("IFCCARTESIANPOINT((" + d(comprimento / 2) + "," + d(largura / 2) + "))");
        int pl2d = b.add("IFCAXIS2PLACEMENT2D(#" + centro2d + ",$)");
        int prof = b.add("IFCRECTANGLEPROFILEDEF(.AREA.,$,#" + pl2d + "," + d(comprimento) + "," + d(largura) + ")");
        int pos0 = b.add("IFCAXIS2PLACEMENT3D(#" + b.add("IFCCARTESIANPOINT((0.,0.,0.))") + ",#" + dirZ + ",#" + dirX + ")");
        int solid = b.add("IFCEXTRUDEDAREASOLID(#" + prof + ",#" + pos0 + ",#" + dirZ + "," + d(altura) + ")");
        int shape = b.add("IFCSHAPEREPRESENTATION(#" + b.ctx + ",'Body','SweptSolid',(#" + solid + "))");
        int lp = b.add("IFCLOCALPLACEMENT(#" + lpStorey + ",#" + pl + ")");
        b.add("IFCPRODUCTDEFINITIONSHAPE($,$,(#" + shape + "))");
        return lp;
    }

    private static void pset(Buf b, Elemento e, int produto, int hist) {
        var mat = e.getMaterialId() == null ? null : TabelaA.material(e.getMaterialId()).orElse(null);
        var props = new ArrayList<String>();
        props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Camada',$,IFCLABEL(" + s(e.getCamada().rotulo()) + "),$)"));
        props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Origem',$,IFCLABEL(" + s(e.getOrigem().name()) + "),$)"));
        if (br.com.civilconstructorai.modelo.Circulacao.eh(e)) {
            double L = e.ponto("p1").distancia(e.ponto("p2"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Desnivel',$,IFCLENGTHMEASURE(" + d(e.num("desnivel", 0)) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Comprimento',$,IFCLENGTHMEASURE(" + d(L) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Largura',$,IFCLENGTHMEASURE(" + d(e.num("largura", 1.0)) + "),$)"));
            if (e.getTipo() == TipoElemento.ESCADA) {
                int n = br.com.civilconstructorai.modelo.Circulacao.degraus(e);
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('NumberOfRiser',$,IFCCOUNTMEASURE(" + n + "),$)"));
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('RiserHeight',$,IFCPOSITIVELENGTHMEASURE(" + d(e.num("desnivel", 0) / n) + "),$)"));
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('TreadLength',$,IFCPOSITIVELENGTHMEASURE(" + d(L / n) + "),$)"));
            }
        }
        if (e.getTipo() == TipoElemento.OBJETO) {
            var dim = ObjetosPrediais.dim(e);
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Item',$,IFCIDENTIFIER(" + s(e.texto("item")) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Largura',$,IFCLENGTHMEASURE(" + d(dim[0]) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Profundidade',$,IFCLENGTHMEASURE(" + d(dim[1]) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Altura',$,IFCLENGTHMEASURE(" + d(dim[2]) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Elevacao',$,IFCLENGTHMEASURE(" + d(e.num("elevacao", 0)) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Fabricante',$,IFCLABEL('genérico (dimensões típicas adotadas)'),$)"));
            Double pw = e.getProps().get("potenciaW") instanceof Number n ? n.doubleValue()
                    : ObjetosPrediais.item(e).flatMap(it -> it.conectores().stream().map(br.com.civilconstructorai.notacao.CatalogoObjetos.Conector::potenciaW).filter(java.util.Objects::nonNull).findFirst()).orElse(null);
            if (pw != null) {
                // valor de referência da biblioteca quando o projetista não informou o do equipamento
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('PotenciaW',$,IFCPOWERMEASURE(" + d(pw) + "),$)"));
            }
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('BibliotecaVersao',$,IFCLABEL(" + s(br.com.civilconstructorai.notacao.CatalogoObjetos.padrao().versao()) + "),$)"));
        }
        if (mat != null) {
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('MaterialId',$,IFCLABEL(" + s(mat.materialId()) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('Material',$,IFCLABEL(" + s(mat.name()) + "),$)"));
            if (mat.physicalProperties().fck_MPa() != null) {
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('fck_MPa',$,IFCREAL(" + d(mat.physicalProperties().fck_MPa()) + "),$)"));
            }
            // resistência ao fogo não é exportada: depende do elemento (dimensões, cobrimento), não só do material
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('CatalogoVersao',$,IFCLABEL(" + s(CatalogoMateriais.padrao().versao()) + "),$)"));
            props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('LoadBearing',$,IFCBOOLEAN(" + (mat.physicalProperties().loadBearing() ? ".T." : ".F.") + "),$)"));
            if (mat.baseQuantities().sinapi() != null) {
                props.add("#" + b.add("IFCPROPERTYSINGLEVALUE('SINAPI',$,IFCLABEL(" + s(mat.baseQuantities().sinapi()) + "),$)"));
            }
        }
        int ps = b.add("IFCPROPERTYSET(" + s(globalId(UUID.nameUUIDFromBytes(("pset:" + e.getId()).getBytes()))) + ",#" + hist + ",'Pset_TabelaA',$,(" + String.join(",", props) + "))");
        b.add("IFCRELDEFINESBYPROPERTIES(" + s(globalId(UUID.nameUUIDFromBytes(("rel:" + e.getId()).getBytes()))) + ",#" + hist + ",$,$,(#" + produto + "),#" + ps + ")");
    }
}
