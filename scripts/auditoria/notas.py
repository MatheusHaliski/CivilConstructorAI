#!/usr/bin/env python3
"""Notas por RF da auditoria 2026-10 (rubrica da auditoria geral: 30% conclusão, 25% precisão, 20% clareza,
15% feedback, 10% consistência/acessibilidade). Subnotas atribuídas pelo auditor a partir das evidências em
docs/auditoria/evidencias (não são medidas com pessoas). Gera a tabela usada em docs/auditoria/AUDITORIA_2026-10.md."""
PESOS = (0.30, 0.25, 0.20, 0.15, 0.10)
# rf: (antes C,P,Cl,F,A), (depois C,P,Cl,F,A), evidência, principal problema restante
NOTAS = {
    "RF01": ((7, 5, 7, 7, 8), (9, 9, 8, 8, 8), "j2 CA-03/04/05, ModeloComandoIT, paridade TS↔Java", "sem comparar/restaurar versões"),
    "RF02": ((7, 7, 6, 6, 8), (9, 9, 8, 8, 8), "j2 CA-01/02, equivalência; j3 CA-04", "texto natural só simulado em dev"),
    "RF03": ((6, 6, 5, 3, 8), (9, 9, 8, 8, 8), "j2 malha/CA-01..04", "—"),
    "RF04": ((7, 6, 6, 6, 8), (9, 9, 8, 8, 8), "j2 CA-02..05, carga-linear", "—"),
    "RF05": ((8, 7, 7, 6, 8), (9, 9, 8, 8, 8), "j2 equilíbrio ΣRz = 1,4·ΣG; SolverMefTest", "doc descreve serviço Python; código é Java"),
    "RF06": ((8, 8, 7, 7, 8), (8, 8, 7, 7, 8), "j2 CA-02/caixa; EstresseAuditoriaTest e04", "—"),
    "RF07": ((7, 4, 6, 5, 8), (8, 9, 8, 8, 8), "j2 CA-01 (409 obsoleta); DimensionamentoIT, Nbr6118Test", "armaduras não conferidas contra memória de cálculo independente (não verificado)"),
    "RF08": ((9, 9, 8, 8, 8), (9, 9, 8, 8, 8), "j2 CA-01/03; ModeloBimTest", "—"),
    "RF09": ((6, 8, 4, 3, 8), (9, 9, 8, 8, 8), "j3 CA-02/03, perfil, upload S3; SondagemIT", "extração do boletim por IA só simulada"),
    "RF10": ((7, 5, 7, 5, 8), (8, 8, 8, 8, 8), "j3 dimensionar, CA-02/04/05; CalculoFundacoesTest", "capacidade de carga não conferida contra caso de referência (não verificado)"),
    "RF11": ((8, 8, 7, 6, 8), (9, 9, 8, 8, 8), "j3 CA-01/03/04/05/06, totais; QuantificadorTest", "critério de interseção não aparece na tela"),
    "RF12": ((5, 3, 5, 4, 8), (8, 8, 8, 8, 8), "j3 sem-tabela, importar, soma, sem-vinculo, CA-05/06; OrcamentoIT", "preço real depende de importar a tabela da Caixa; o seed é demonstração"),
    "RF13": ((8, 6, 7, 7, 7), (8, 8, 7, 7, 7), "j3 CA-08, liberado; SnapshotClienteIT", "3D só inspeciona; sem corte interativo"),
    "RF14": ((3, 6, 5, 3, 8), (8, 8, 8, 8, 8), "j3 CA-03/04/05/07; GeradorPranchaTest, PranchaIT", "sem PDF (só SVG e DXF)"),
    "RF15": ((4, 6, 6, 5, 8), (8, 7, 7, 7, 8), "j3 CA-02/08, conteúdo IFC; EscritorIfcTest, ExportacaoIfcIT", "IFC sem materiais"),
    "RF16": ((6, 6, 6, 6, 8), (7, 7, 7, 7, 8), "EstresseAuditoriaTest e13/e15; j3 CA-04 (simulação)", "laço com LLM real não verificado"),
    "RF17": ((8, 8, 7, 7, 8), (8, 8, 7, 7, 8), "EstresseAuditoriaTest e05/e06, ModeloBimTest disciplinaZ", "não exercitado em jornada"),
    "RF18": ((8, 9, 6, 7, 9), (9, 9, 9, 9, 9), "j1 CA-01..10; j4 UX-erros/corrigir-email; CadastroIT", "—"),
    "RF19": ((7, 5, 7, 6, 9), (9, 9, 8, 9, 9), "j1 CA-01..06c, replay; j4 UX-mfa/voltar; LoginIT", "sem QR do TOTP na tela"),
    "RF20": ((5, 4, 5, 5, 9), (9, 9, 9, 9, 9), "j1 CA-02..08; j4 UX-email/convite/aceite; ConviteEAcessoIT", "—"),
    "RF21": ((6, 3, 6, 6, 9), (9, 9, 8, 8, 9), "j1 CA-01/02/04a; j3 selo; EscopoEmpresaIT", "veDadoDeSaude sem caso de uso"),
    "RF22": ((7, 8, 5, 6, 9), (9, 9, 8, 8, 9), "j1 CA-01/02/04/05; j4 UX-contexto", "—"),
    "RF23": ((8, 7, 7, 7, 9), (9, 9, 8, 8, 9), "j1 CA-02/03/05/07; j3 sudo-email", "—"),
    "RF24": ((9, 9, 8, 8, 9), (9, 9, 8, 8, 9), "j1 CA-01; j3 CA-03/04/07; ConsentimentoIT", "—"),
    "RF25": ((9, 9, 8, 8, 9), (9, 9, 8, 8, 9), "j3 CA-01..08; ExportacaoIT, CifraExportacaoTest", "—"),
    "RF26": ((7, 8, 8, 8, 9), (7, 8, 8, 8, 9), "j3 consequências, CA-02/03; EliminacaoIT", "saga após 30 dias não exercitada em jornada"),
    "RF27": ((6, 6, 7, 6, 8), (8, 8, 8, 8, 8), "j3 CA-01/03/04/06/08/10, promoção; RascunhoIaIT", "IA real não verificada em dev"),
    "RF28": ((7, 6, 7, 6, 8), (9, 9, 8, 8, 8), "catálogo verificável 73/85; estado.test.ts; paridade 34 casos", "BLOCO/INSERIR/ESTILO_COTA/LOCALIZAR ausentes"),
}


# famílias de comandos (rubrica da auditoria de arquitetura: correção 30%, precisão 25%, clareza 15%, feedback 10%,
# consistência visual 10%, acessibilidade 10%)
PESOS_CAD = (0.30, 0.25, 0.15, 0.10, 0.10, 0.10)
FAMILIAS = {
    "Desenho 2D": ((7, 7, 7, 7, 8, 7), (9, 9, 8, 8, 8, 7), "paridade TS↔Java em todos os comandos de desenho; LINHA_DETALHE passou a criar a linha"),
    "Edição": ((6, 5, 7, 6, 8, 7), (9, 9, 8, 8, 8, 7), "CONCORDAR/CHANFRAR respeitam raio e distâncias; QUEBRAR, UNIR, ESTICAR testados"),
    "Precisão": ((8, 8, 7, 8, 8, 7), (8, 9, 8, 8, 8, 7), "osnap, orto, polar, grade e entrada dinâmica testados; sem FROM e UCS por 3 pontos"),
    "Organização": ((6, 7, 7, 6, 7, 7), (6, 7, 7, 7, 7, 7), "camadas fixas por disciplina; sem grupos e estados de camada"),
    "Anotações": ((5, 6, 6, 5, 7, 7), (7, 7, 7, 7, 7, 7), "cotas e linha de detalhe gravando; estilo de cota e blocos ausentes"),
    "Vista e inspeção": ((7, 7, 7, 5, 7, 7), (8, 8, 7, 8, 7, 7), "ocultar/isolar/reexibir testados; F2 funcionando; VG e VU parciais"),
    "Materiais e códigos": ((6, 4, 6, 6, 8, 7), (9, 9, 8, 8, 8, 7), "MAT. aceito nos dois motores (antes só na tela Códigos)"),
}


def nota(sub, pesos=PESOS):
    return round(sum(p * s for p, s in zip(pesos, sub)), 1)


if __name__ == "__main__":
    print("| RF | Antes (C/P/Cl/F/A) | Nota antes | Depois (C/P/Cl/F/A) | Nota depois | Evidência | Principal problema restante |")
    print("|---|---|---:|---|---:|---|---|")
    for rf, (a, d, ev, prob) in NOTAS.items():
        print(f"| {rf} | {'/'.join(map(str, a))} | {nota(a)} | {'/'.join(map(str, d))} | {nota(d)} | {ev} | {prob} |")
    antes = sum(nota(a) for a, *_ in NOTAS.values()) / len(NOTAS)
    depois = sum(nota(d) for _, d, *_ in NOTAS.values()) / len(NOTAS)
    print(f"\nMédia: antes {antes:.1f} · depois {depois:.1f}")
    print("\n| Família | Antes (C/P/Cl/F/V/A) | Nota antes | Depois | Nota depois | Base |")
    print("|---|---|---:|---|---:|---|")
    for fam, (a, d, base) in FAMILIAS.items():
        print(f"| {fam} | {'/'.join(map(str, a))} | {nota(a, PESOS_CAD)} | {'/'.join(map(str, d))} | {nota(d, PESOS_CAD)} | {base} |")
