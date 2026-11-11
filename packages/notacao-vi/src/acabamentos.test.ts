import { describe, expect, it } from "vitest";
import { ModeloBim } from "./modelo";
import { criarCamadaAcabamento, errosAcabamentos, medirAcabamentos, retangulosLivresParede } from "./acabamentos";
import { quantificar } from "./quantitativos";
import type { Abertura, Laje, Parede } from "./tipos";

function parede(): Parede {
  return {id:"p",tipo:"PAREDE",pavimentoId:"pav_terreo",camada:"arquitetura",origem:"CANVAS",materialId:"MAT_ALV_BLOCO14",p1:{x:0,y:0},p2:{x:5,y:0},altura:2.8,espessura:.14,externa:false};
}
function porta(id="d"): Abertura {
  return {id,tipo:"PORTA",pavimentoId:"pav_terreo",camada:"arquitetura",origem:"CANVAS",paredeId:"p",posicao:.5,largura:.8,altura:2.1,peitoril:0};
}
describe("Composição construtiva e medição física", () => {
  it("desconta união de vãos sobrepostos nas duas faces sem duplicar perda no peso", () => {
    const p=parede(), a=criarCamadaAcabamento("MAT_REBOCO","LADO_A","a");
    a.espessura_m=.02;a.pesoEspecifico_kN_m3=20;a.perda_percentual=10;
    p.acabamentos=[a,{...a,id:"b",face:"LADO_B"}];
    const modelo=new ModeloBim({elementos:[p,porta(),porta("sobreposta")]});
    for (const r of medirAcabamentos(modelo)) {
      expect(r.areaBruta_m2).toBe(14);expect(r.areaLiquida_m2).toBeCloseTo(12.32,10);
      expect(r.volume_m3).toBeCloseTo(.2464,10);expect(r.pesoInstalado_kN).toBeCloseTo(4.928,10);
      expect(r.areaCompra_m2).toBeCloseTo(13.552,10);
    }
    expect(quantificar(modelo).linhas[0]!.alvenaria_m2).toBe(12.32);
  });
  it("usa área real da laje para acabamento e separa compra modular de peso instalado", () => {
    const l: Laje={id:"l",tipo:"LAJE",pavimentoId:"pav_terreo",camada:"estrutura",origem:"CANVAS",h:.12,materialId:"MAT_CONC_FCK30",contorno:[{x:0,y:0},{x:5,y:0},{x:5,y:4},{x:0,y:4}],acabamentos:[{...criarCamadaAcabamento("MAT_PORCELANATO","SUPERIOR","c"),larguraPeca_m:.5,alturaPeca_m:.5,junta_m:0,perda_percentual:10,massaSuperficial_kg_m2:10}]};
    const [r]=medirAcabamentos(new ModeloBim({elementos:[l]}));
    expect(r!.areaLiquida_m2).toBe(20);expect(r!.areaCompra_m2).toBe(22);
    expect(r!.pecasEstimadas).toBe(88);expect(r!.pesoInstalado_kN).toBeCloseTo(1.96133,10);
  });
  it("massa desconhecida não vira zero e rascunho não vira quantitativo", () => {
    const p=parede();p.acabamentos=[criarCamadaAcabamento("MAT_GESSO","LADO_A","c")];
    expect(medirAcabamentos(new ModeloBim({elementos:[p]}))[0]!.pesoInstalado_kN).toBeUndefined();
    p.rascunhoIaId="ia";expect(medirAcabamentos(new ModeloBim({elementos:[p]}))).toEqual([]);
  });
  it("auditor rejeita números não finitos, faces inválidas e material fora do elemento", () => {
    const m=new ModeloBim({elementos:[parede()]});const v=m.versao;
    for (const props of [{espessura_m:NaN},{face:"SUPERIOR"},{materialId:"MAT_CONC_FCK30"}]) {
      expect(() => m.aplicar("ALTERAR",{id:"p",props:{acabamentos:[{...criarCamadaAcabamento("MAT_REBOCO","LADO_A","c"),...props}]}})).toThrow();
      expect(m.versao).toBe(v);expect((m.elemento("p") as Parede).acabamentos).toBeUndefined();
    }
    expect(errosAcabamentos({...parede(),massaNucleo_kg_m2:NaN})).not.toEqual([]);
  });
  it("mudança de especificação entra no hash e composição sobrevive desfazer/refazer", () => {
    const m=new ModeloBim({elementos:[parede()]}), h=quantificar(m).hash;
    m.aplicar("ALTERAR",{id:"p",props:{acabamentos:[criarCamadaAcabamento("MAT_REBOCO","LADO_A","a")]}});
    const novo=quantificar(m).hash;expect(novo).not.toBe(h);
    m.desfazer();expect(quantificar(m).hash).toBe(h);m.refazer();expect(quantificar(m).hash).toBe(novo);
  });
  it("recorta desconto ao domínio da parede sem área negativa", () => {
    const p=parede();const d={...porta(),posicao:0,largura:2,altura:5};
    const area=retangulosLivresParede(p,[d]).reduce((s,r) => s+(r.x1-r.x0)*(r.z1-r.z0),0);
    expect(area).toBeCloseTo(11.2,10);
  });
});
