"use client";
import { useState } from "react";
import { criarCamadaAcabamento, MATERIAIS_CONSTRUTIVOS, medirAcabamentos, type CamadaAcabamento, type Laje, type ModeloBim, type Parede } from "@cca/notacao-vi";
import { Alternar, Botao } from "@/components/ui";

type Props = { elemento: Parede | Laje; modelo: ModeloBim; podeEditar: boolean; onAlterar: (campo: string, valor: unknown) => void };
const NUMERO = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 3 });
const ROTULOS: Record<CamadaAcabamento["face"], string> = { LADO_A: "Lado A (esquerda de p1 → p2)", LADO_B: "Lado B (direita de p1 → p2)", SUPERIOR: "Superior", INFERIOR: "Inferior" };
const classe = "min-h-9 w-full rounded border border-line bg-bg px-2 text-sm disabled:opacity-60";

function NumeroCamada({ rotulo, valor, fator = 1, minimo = 0, disabled, onChange }: { rotulo: string; valor?: number; fator?: number; minimo?: number; disabled: boolean; onChange: (v: number | undefined) => void }) {
  return <label className="flex flex-col gap-1 text-xs"><span>{rotulo}</span><input key={String(valor)} type="number" min={minimo} step="any" defaultValue={valor === undefined ? "" : Number((valor * fator).toFixed(6))} disabled={disabled} className={classe} onBlur={ev => { const v = ev.target.value === "" ? undefined : Number(ev.target.value) / fator; if (v === undefined || (Number.isFinite(v) && v >= minimo / fator)) onChange(v); }} /></label>;
}

function DimensoesPeca({ camada, disabled, onApply }: { camada: CamadaAcabamento; disabled: boolean; onApply: (largura?: number, altura?: number) => void }) {
  const [largura, setLargura] = useState(camada.larguraPeca_m === undefined ? "" : String(camada.larguraPeca_m * 1000));
  const [altura, setAltura] = useState(camada.alturaPeca_m === undefined ? "" : String(camada.alturaPeca_m * 1000));
  const vazias = largura === "" && altura === "";
  const validas = vazias || (Number(largura) > 0 && Number(altura) > 0 && Number.isFinite(Number(largura)) && Number.isFinite(Number(altura)));
  return <div className="grid grid-cols-2 gap-2"><label className="text-xs">Largura da peça (mm)<input type="number" min="0.001" step="any" value={largura} disabled={disabled} onChange={e => setLargura(e.target.value)} className={classe} /></label><label className="text-xs">Altura da peça (mm)<input type="number" min="0.001" step="any" value={altura} disabled={disabled} onChange={e => setAltura(e.target.value)} className={classe} /></label><Botao pequeno variante="secundario" disabled={disabled || !validas} onClick={() => onApply(vazias ? undefined : Number(largura) / 1000, vazias ? undefined : Number(altura) / 1000)}>Aplicar dimensões</Botao>{!validas && <p className="col-span-2 text-xs text-warning">Informe ambas as dimensões positivas, ou deixe ambas vazias para medir só por área.</p>}</div>;
}

export function PainelAcabamentos({ elemento, modelo, podeEditar, onAlterar }: Props) {
  const materiais = MATERIAIS_CONSTRUTIVOS.filter(m => m.aplicacoes.includes(elemento.tipo));
  const [materialId, setMaterialId] = useState(materiais[0]?.id ?? "");
  const faces: CamadaAcabamento["face"][] = elemento.tipo === "PAREDE" ? ["LADO_A", "LADO_B"] : ["SUPERIOR", "INFERIOR"];
  const [face, setFace] = useState<CamadaAcabamento["face"]>(faces[0]!);
  const camadas = elemento.acabamentos ?? [];
  const medicoes = medirAcabamentos(modelo).filter(m => m.elementoId === elemento.id);
  const alterar = (id: string, campo: keyof CamadaAcabamento, valor: unknown) => onAlterar("acabamentos", camadas.map(c => c.id === id ? { ...c, [campo]: valor } : c));
  return <section aria-label="Materiais e acabamentos" className="flex flex-col gap-3 border-t border-line pt-3">
    <h3 className="font-semibold">Materiais e acabamentos</h3>
    <p className="text-xs text-text-3">Camadas em ordem de aplicação, do núcleo para fora de cada face. As dimensões alteram o 3D e o levantamento. Peças e juntas aparecem como paginação diagramática, sem plano de corte. O material do núcleo permanece separado.</p>
    <label className="text-xs">Material da nova camada<select aria-label="Material da nova camada" value={materialId} onChange={e => setMaterialId(e.target.value)} disabled={!podeEditar} className={classe}>{materiais.map(m => <option key={m.id} value={m.id}>{m.nome}</option>)}</select></label>
    <label className="text-xs">Face da nova camada<select aria-label="Face da nova camada" value={face} onChange={e => setFace(e.target.value as CamadaAcabamento["face"])} disabled={!podeEditar} className={classe}>{faces.map(f => <option key={f} value={f}>{ROTULOS[f]}</option>)}</select></label>
    <Botao pequeno variante="secundario" disabled={!podeEditar || !materialId} onClick={() => onAlterar("acabamentos", [...camadas, criarCamadaAcabamento(materialId, face, `acab-${crypto.randomUUID()}`)])}>Adicionar camada</Botao>
    {camadas.map((c, indice) => { const m = MATERIAIS_CONSTRUTIVOS.find(m => m.id === c.materialId); const med = medicoes.find(x => x.camadaId === c.id); return <fieldset key={c.id} className="flex flex-col gap-2 rounded border border-line p-2"><legend className="px-1 text-xs font-semibold">{indice + 1}. {m?.nome ?? c.materialId} · {ROTULOS[c.face]}</legend>
      <p className="text-xs text-text-2">{m?.descricao}</p>{m?.fonte && <p className="text-[11px] text-text-3">Referência: {m.fonte}</p>}
      <NumeroCamada rotulo="Espessura (mm)" valor={c.espessura_m} fator={1000} minimo={0.001} disabled={!podeEditar} onChange={v => v !== undefined && alterar(c.id, "espessura_m", v)} />
      {m && m.formatos.length > 0 && <label className="text-xs">Formato comercial<select aria-label={`Formato comercial ${indice + 1}`} value={m.formatos.some(f => f.largura_m === c.larguraPeca_m && f.altura_m === c.alturaPeca_m) ? `${c.larguraPeca_m}x${c.alturaPeca_m}` : ""} disabled={!podeEditar} className={classe} onChange={ev => { if (!ev.target.value) return; const [larguraPeca_m, alturaPeca_m] = ev.target.value.split("x").map(Number); onAlterar("acabamentos", camadas.map(item => item.id === c.id ? { ...item, larguraPeca_m, alturaPeca_m } : item)); }}><option value="">Personalizado</option>{m.formatos.map(f => <option key={`${f.largura_m}x${f.altura_m}`} value={`${f.largura_m}x${f.altura_m}`}>{f.largura_m * 1000} × {f.altura_m * 1000} mm</option>)}</select></label>}
      <DimensoesPeca key={`${c.id}-${c.larguraPeca_m}-${c.alturaPeca_m}`} camada={c} disabled={!podeEditar} onApply={(larguraPeca_m, alturaPeca_m) => onAlterar("acabamentos", camadas.map(item => item.id === c.id ? { ...item, larguraPeca_m, alturaPeca_m } : item))} />
      <div className="grid grid-cols-2 gap-2"><NumeroCamada rotulo="Junta (mm)" valor={c.junta_m} fator={1000} disabled={!podeEditar} onChange={v => alterar(c.id, "junta_m", v ?? 0)} /><NumeroCamada rotulo="Perda para compra (%)" valor={c.perda_percentual} disabled={!podeEditar} onChange={v => alterar(c.id, "perda_percentual", v ?? 0)} /></div>
      <p className="text-[11px] text-text-3">Quando ambas as propriedades são informadas, a massa superficial prevalece no peso instalado.</p>
      <NumeroCamada rotulo="Peso específico instalado (kN/m³)" valor={c.pesoEspecifico_kN_m3} disabled={!podeEditar} onChange={v => alterar(c.id, "pesoEspecifico_kN_m3", v)} /><NumeroCamada rotulo="Massa superficial instalada (kg/m²)" valor={c.massaSuperficial_kg_m2} disabled={!podeEditar} onChange={v => alterar(c.id, "massaSuperficial_kg_m2", v)} />
      <label className="text-xs">Especificação qualitativa<textarea aria-label={`Especificação qualitativa ${indice + 1}`} key={c.especificacao} defaultValue={c.especificacao ?? ""} disabled={!podeEditar} className={classe} onBlur={ev => alterar(c.id, "especificacao", ev.target.value)} /></label>
      {med && <div className="text-xs text-text-2" aria-live="polite"><p>Área líquida: {NUMERO.format(med.areaLiquida_m2)} m² · volume: {NUMERO.format(med.volume_m3)} m³</p><p>Compra: {NUMERO.format(med.areaCompra_m2)} m²{med.pecasEstimadas !== undefined ? ` · ${med.pecasEstimadas} peças (estimativa por módulo)` : ""}</p><p>Peso instalado: {med.pesoInstalado_kN === undefined ? "pendente de caracterização" : `${NUMERO.format(med.pesoInstalado_kN)} kN`}</p>{med.pendencias.map(p => <p key={p} className="text-warning">{p}</p>)}</div>}
      <Botao pequeno variante="perigo" disabled={!podeEditar} onClick={() => onAlterar("acabamentos", camadas.filter(item => item.id !== c.id))}>Remover camada {indice + 1}</Botao>
    </fieldset>; })}
    {elemento.tipo === "PAREDE" && elemento.materialId === "MAT_DIVISORIA_SECA" && <><NumeroCamada rotulo="Massa de perfis e isolantes do núcleo (kg/m²)" valor={elemento.massaNucleo_kg_m2} disabled={!podeEditar} onChange={v => onAlterar("massaNucleo_kg_m2", v)} /><p className="text-[11px] text-text-3">Espessura da parede representa a cavidade. Informe as chapas ST, RU ou RF como camadas; a massa do núcleo contém apenas perfis e isolantes.</p></>}
    <Alternar rotulo="Integrar peso dos acabamentos na carga G" ligado={!!elemento.integrarPesoAcabamentos} disabled={!podeEditar} onChange={v => onAlterar("integrarPesoAcabamentos", v)} />
    {elemento.tipo === "PAREDE" && <label className="text-xs">Viga de apoio para carga G<select aria-label="Viga de apoio para carga G" value={elemento.apoioEstruturalId ?? ""} disabled={!podeEditar} className={classe} onChange={ev => onAlterar("apoioEstruturalId", ev.target.value || undefined)}><option value="">Selecione o apoio explícito</option>{modelo.elementos.filter(e => e.tipo === "VIGA" && e.pavimentoId === elemento.pavimentoId).map(e => <option key={e.id} value={e.id}>{e.rotulo ?? e.id}</option>)}</select></label>}
    <p className="text-[11px] text-text-3">Peso usa área instalada, sem perdas de compra. A integração em G exige propriedades de peso definidas; paredes exigem viga com mesma extensão, alinhamento e nível. Cargas manuais conflitantes impedem a integração.</p>
  </section>;
}
