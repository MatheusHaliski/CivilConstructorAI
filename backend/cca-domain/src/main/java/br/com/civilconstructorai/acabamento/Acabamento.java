package br.com.civilconstructorai.acabamento;

import java.util.*;

/** Camada instalada. Perdas são de compra e nunca aumentam o peso instalado. */
public record Acabamento(String id, String materialId, String face, double espessura_m,
        Double larguraPeca_m, Double alturaPeca_m, double junta_m, double perda_percentual,
        Double massaSuperficial_kg_m2, Double pesoEspecifico_kN_m3, String especificacao) {
    public static final Set<String> MATERIAIS = Set.of("MAT_REBOCO","MAT_SEMIREBOCO","MAT_CHAPISCO","MAT_ARGAMASSA","MAT_AZULEJO","MAT_PORCELANATO","MAT_GRANITO","MAT_MARMORE","MAT_GESSO","MAT_DRYWALL_ST","MAT_DRYWALL_RU","MAT_DRYWALL_RF","MAT_PINTURA");
    public Acabamento {
        if (id == null || id.isBlank() || !MATERIAIS.contains(materialId == null ? "" : materialId)) throw new IllegalArgumentException("Camada precisa de id e material de acabamento conhecido.");
        if (!Set.of("LADO_A","LADO_B","SUPERIOR","INFERIOR").contains(face == null ? "" : face)) throw new IllegalArgumentException("Face de acabamento inválida.");
        if (!Double.isFinite(espessura_m) || espessura_m <= 0 || espessura_m > 0.5 || !Double.isFinite(junta_m) || junta_m < 0 || junta_m > 0.1 || !Double.isFinite(perda_percentual) || perda_percentual < 0 || perda_percentual > 100) throw new IllegalArgumentException("Espessura, junta ou perda de acabamento inválida.");
        for (Double v : new Double[]{larguraPeca_m,alturaPeca_m,massaSuperficial_kg_m2,pesoEspecifico_kN_m3}) if(v != null && (!Double.isFinite(v) || v <= 0)) throw new IllegalArgumentException("Dimensões e pesos devem ser positivos e finitos.");
        if ((larguraPeca_m == null) != (alturaPeca_m == null)) throw new IllegalArgumentException("Informe largura e altura da peça juntas.");
    }
    /** Massa superficial explicitamente informada prevalece sobre peso específico volumétrico. */
    public Double pesoPorArea() {
        if (massaSuperficial_kg_m2 != null) return massaSuperficial_kg_m2 * 0.00980665;
        if (pesoEspecifico_kN_m3 != null) return espessura_m * pesoEspecifico_kN_m3;
        return null;
    }
    public void validarFace(boolean parede) {
        if (parede != face.startsWith("LADO_")) throw new IllegalArgumentException("Face incompatível com o elemento.");
        if (!parede && "MAT_AZULEJO".equals(materialId)) throw new IllegalArgumentException("Azulejo é revestimento exclusivo de parede.");
    }
    public static List<Acabamento> ler(Object valor, boolean parede) {
        if(valor == null) return List.of();
        if(!(valor instanceof List<?> lista)) throw new IllegalArgumentException("Acabamentos deve ser uma lista.");
        var saida=new ArrayList<Acabamento>(); var ids=new HashSet<String>();
        for(Object v:lista) {
            if(!(v instanceof Map<?,?> m)) throw new IllegalArgumentException("Camada de acabamento inválida.");
            var a = new Acabamento(texto(m,"id"),texto(m,"materialId"),texto(m,"face"),numero(m,"espessura_m",0),opcional(m,"larguraPeca_m"),opcional(m,"alturaPeca_m"),numero(m,"junta_m",0),numero(m,"perda_percentual",0),opcional(m,"massaSuperficial_kg_m2"),opcional(m,"pesoEspecifico_kN_m3"),texto(m,"especificacao"));
            a.validarFace(parede); if(!ids.add(a.id())) throw new IllegalArgumentException("Id de acabamento duplicado."); saida.add(a);
        }
        return List.copyOf(saida);
    }
    private static String texto(Map<?,?> m,String k){return m.get(k)==null?null:String.valueOf(m.get(k));}
    private static Double opcional(Map<?,?> m,String k){if(m.get(k)==null)return null;if(!(m.get(k) instanceof Number n))throw new IllegalArgumentException("Valor numérico inválido: "+k);return n.doubleValue();}
    private static double numero(Map<?,?> m,String k,double padrao){Double v=opcional(m,k);return v==null?padrao:v;}
}
