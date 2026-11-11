#!/usr/bin/env python3
"""Referência independente 2D, soluções fechadas e auditoria dos resultados Java.

Não certifica conformidade ABNT. Requer numpy/matplotlib; entradas e resultados
Java são arquivos versionáveis em docs/validacao. Nenhuma chamada ao solver Java.
"""
import json
import hashlib
import math
import os
from pathlib import Path
os.environ.setdefault('MPLCONFIGDIR', '/tmp/cca-matplotlib')
os.environ.setdefault('XDG_CACHE_HOME', '/tmp/cca-font-cache')
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/validacao'

def referencia(caso, comb):
    """Pórtico XZ, três GDL por nó. Rotação 2D positiva é oposta a Ry do Java."""
    nodes = np.array(caso['nos'], dtype=float)
    nd = 3 * len(nodes)
    k = np.zeros((nd, nd)); f = np.zeros(nd)
    elementos = []
    for num, b in enumerate(caso['barras']):
        p, q = nodes[b['i']], nodes[b['j']]
        dx, dz = q-p; length = np.linalg.norm(q-p)
        co, si = dx/length, dz/length
        e, a, inertia = caso['e_kN_m2'], b['b']*b['h'], b['b']*b['h']**3/12
        ax, flex = e*a/length, e*inertia
        kl = np.array([[ax,0,0,-ax,0,0],
              [0,12*flex/length**3,6*flex/length**2,0,-12*flex/length**3,6*flex/length**2],
              [0,6*flex/length**2,4*flex/length,0,-6*flex/length**2,2*flex/length],
              [-ax,0,0,ax,0,0],
              [0,-12*flex/length**3,-6*flex/length**2,0,12*flex/length**3,-6*flex/length**2],
              [0,6*flex/length**2,2*flex/length,0,-6*flex/length**2,4*flex/length]])
        rot = np.array([[co,si,0],[-si,co,0],[0,0,1]])
        t = np.zeros((6,6)); t[:3,:3] = rot; t[3:,3:] = rot
        dofs = np.array([3*b['i']+i for i in range(3)]+[3*b['j']+i for i in range(3)])
        uniform = sum(comb['g']*x['g']+comb['q']*x['q'] for x in caso['distribuidas'] if x['barra']==num)
        wx, wy = -si*uniform, -co*uniform
        fe = np.array([wx*length/2, wy*length/2, wy*length**2/12, wx*length/2, wy*length/2,-wy*length**2/12])
        k[np.ix_(dofs,dofs)] += t.T@kl@t
        f[dofs] += t.T@fe
        elementos.append(dict(dofs=dofs,t=t,k=kl,fe=fe,l=length,wy=wy,wx=wx,ei=flex))
    for n in caso['nodais']:
        f[3*n['no']] += comb['g']*n['gx']+comb['q']*n['qx']
        f[3*n['no']+1] += comb['g']*n['gz']+comb['q']*n['qz']
    restritos = [3*a['no']+i for a in caso['apoios'] for i,key in enumerate(['ux','uz','ry']) if a[key]]
    livres = [i for i in range(nd) if i not in restritos]
    u = np.zeros(nd); u[livres] = np.linalg.solve(k[np.ix_(livres,livres)],f[livres])
    reacoes = k@u-f
    residual = np.max(np.abs((k@u-f)[livres])) if livres else 0.
    for b in elementos:
        b['u'] = b['t']@u[b['dofs']]
        b['f'] = b['k']@b['u']-b['fe']
    return elementos, reacoes, residual

def secao(b, x):
    l, f, u, wy, wx = b['l'], b['f'], b['u'], b['wy'], b['wx']
    xi=x/l
    n1,n2,n3,n4=1-3*xi**2+2*xi**3,l*(xi-2*xi**2+xi**3),3*xi**2-2*xi**3,l*(xi**3-xi**2)
    flecha = n1*u[1]+n2*u[2]+n3*u[4]+n4*u[5]+wy*x*x*(l-x)**2/(24*b['ei'])
    return -f[0]-wx*x, f[1]+wy*x, -f[2]+f[1]*x+wy*x*x/2, abs(flecha)

def assert_close(actual, expected, absolute=1.1e-4, relative=1e-8):
    if not math.isclose(float(actual),float(expected),abs_tol=absolute,rel_tol=relative):
        raise AssertionError(f'{actual} != {expected}; tolerância absoluta={absolute}, relativa={relative}')

def conferir(in_, java):
    resumo=[]
    for c in in_['casos']:
        max_err=0.; max_res=0.
        for comb in in_['combinacoes']:
            bars, reactions, residual = referencia(c,comb)
            max_res=max(max_res,residual)
            assert residual < 1e-8
            j=java[c['id']]['porCombinacao'][comb['nome']]
            for r in j['reacoes']:
                for key,idx,sign in [('fx',0,1),('fz',1,1),('my',2,-1)]:
                    actual,expected=r[key], sign*reactions[3*r['no']+idx]
                    assert_close(actual,expected); max_err=max(max_err,abs(actual-expected))
            for rb,b in zip(j['barras'],bars,strict=True):
                extremos=[0., b['l']]
                if b['wy'] != 0:
                    critico=-b['f'][1]/b['wy']
                    if 0 < critico < b['l']: extremos.append(critico)
                assert_close(max(abs(rb['mMax']),abs(rb['mMin'])), max(abs(secao(b,x)[2]) for x in extremos))
                assert_close(rb['nMax'],max(abs(secao(b,x)[0]) for x in [0.,b['l']]))
                assert_close(rb['vMax'],max(abs(secao(b,x)[1]) for x in [0.,b['l']]))
                for pt in rb['pontos']:
                    n,v,m,d=secao(b,pt['s'])
                    # Referenciais locais da barra vertical diferem: compara módulos transversais.
                    for a,e in [(pt['n'],n),(abs(pt['my']),abs(m)),(math.hypot(pt['vy'],pt['vz']),abs(v))]:
                        assert_close(a,e); max_err=max(max_err,abs(a-e))
                    assert_close(pt['flecha'],d,absolute=1e-10,relative=1e-7)
            if c['id'] in ['B1','B2','L1']:
                rb=j['barras'][0]; l=bars[0]['l']
                q=sum(comb['g']*x['g']+comb['q']*x['q'] for x in c['distribuidas'])
                momento=q*l*l/(2 if c['id']=='B2' else 8)
                assert_close(max(abs(rb['mMax']),abs(rb['mMin'])),momento)
                elastic=q*l**4/(8*bars[0]['ei']) if c['id']=='B2' else 5*q*l**4/(384*bars[0]['ei'])
                assert_close(rb['flechaMax'],elastic,absolute=1e-10,relative=1e-7)
        j=java[c['id']]['porCombinacao']['ELU_TESTE']
        resumo.append(dict(caso=c['id'],erro_max_esforcos_reacoes=max_err,residuo_ref=max_res,
            barras=[dict(id=b['elementoId'],n_abs_kN=b['nMax'],v_abs_kN=b['vMax'],m_positivo_kNm=b['mMax'],m_negativo_kNm=b['mMin'],deslocamento_transversal_mm=1000*b['flechaMax']) for b in j['barras']],
            reacoes=j['reacoes'],estado_global='NAO_VERIFICADO'))
    return resumo

def conferir_integrado(in_, java):
    """D1: decompõe a estrutura retangular simétrica em dois pórticos planos.

    Válido apenas para a geometria, rigidez e carregamento simétricos deste caso;
    não é um método geral de distribuição da carga de laje.
    """
    d=in_['desenhoIntegrado']; area=d['largura']*d['profundidade']; per=2*(d['largura']+d['profundidade'])
    g=(d['hLaje']*25+d['revestimento'])*area/per+d['bViga']*d['hViga']*25
    q=d['sobrecarga']*area/per; peso_pilar=d['bPilar']*d['hPilar']*d['altura']*25
    erro=0.
    for comb in in_['combinacoes']:
        resultados=[]
        for l,outro in [(d['largura'],d['profundidade']),(d['profundidade'],d['largura'])]:
            plano=dict(e_kN_m2=d['e'],nos=[[0,0],[0,d['altura']],[l,d['altura']],[l,0]],
                barras=[dict(i=0,j=1,b=d['bPilar'],h=d['hPilar']),dict(i=1,j=2,b=d['bViga'],h=d['hViga']),dict(i=3,j=2,b=d['bPilar'],h=d['hPilar'])],
                apoios=[dict(no=0,ux=True,uz=True,ry=True),dict(no=3,ux=True,uz=True,ry=True)],
                distribuidas=[dict(barra=1,g=g,q=q)]+[dict(barra=i,g=peso_pilar/d['altura'],q=0) for i in [0,2]],
                nodais=[dict(no=i,gx=0,gz=-g*outro/2,qx=0,qz=-q*outro/2) for i in [1,2]])
            resultados.append(referencia(plano,comb)[0])
        jc=java['D1']['porCombinacao'][comb['nome']]
        for b in jc['barras']:
            col=b['elementoId'].startswith('P')
            for pt in b['pontos']:
                if col:
                    x=secao(resultados[0][0],pt['s']);y=secao(resultados[1][0],pt['s'])
                    pares=[(pt['n'],x[0]),(abs(pt['vz']),abs(x[1])),(abs(pt['vy']),abs(y[1])),(abs(pt['my']),abs(x[2])),(abs(pt['mz']),abs(y[2]))]
                else:
                    ref=resultados[0 if b['elementoId'] in ['V1','V3'] else 1][1]
                    n,v,m,_=secao(ref,pt['s'])
                    pares=[(pt['n'],n),(abs(pt['vz']),abs(v)),(pt['my'],m)]
                for a,e in pares: assert_close(a,e);erro=max(erro,abs(a-e))
        esperado=comb['g']*(g*per+4*peso_pilar)+comb['q']*q*per
        for r in jc['reacoes']: assert_close(r['fz'],esperado/4)
    elu=java['D1']['porCombinacao']['ELU_TESTE']
    return dict(erro_max_esforcos=erro,carga_vertical_elu_kN=sum(r['fz'] for r in elu['reacoes']),
        reacao_por_apoio_kN=elu['reacoes'][0]['fz'],
        barras=[dict(id=b['elementoId'],n_abs_kN=b['nMax'],v_abs_kN=b['vMax'],m_min_kNm=b['mMin'],m_max_kNm=b['mMax'],mz_abs_kNm=max(abs(p['mz']) for p in b['pontos']),t_abs_kNm=max(abs(p['t']) for p in b['pontos'])) for b in elu['barras']],
        q_g_vigas_kN_m=g,q_q_vigas_kN_m=q,estado_global='NAO_VERIFICADO')

def resistencia_parcial(in_, java, hipoteses):
    """Equilíbrio independente com a geometria e demanda atuais, sem Nbr6118.java.

    As armaduras e coeficientes são hipóteses rastreáveis do ensaio. Somente
    flexão simples em My é suportada aqui; interação não vira aprovação parcial.
    """
    def positivo(value):
        value=float(value)
        if not math.isfinite(value) or value <= 0:
            raise ValueError('Hipótese resistente deve ser finita e positiva')
        return value

    mat=hipoteses['material']
    fck,fyk=positivo(mat['fck_MPa']),positivo(mat['fyk_MPa'])
    if not 20 <= fck <= 50 or fyk != 500:
        raise ValueError('Material fora do domínio deste ensaio parcial')
    fcd,fyd=fck/positivo(mat['gamma_c']),fyk/positivo(mat['gamma_s'])
    alpha,lam,xd_lim=map(positivo,(mat['alpha_bloco'],mat['lambda_bloco'],mat['limite_x_d']))
    out=[]
    for arm in hipoteses['armaduras']:
        id_=arm['caso']
        caso=next(c for c in in_['casos'] if c['id']==id_)
        spec=next(b for b in caso['barras'] if b['id']==arm['barra'])
        candidatos=[b for b in java[id_]['porCombinacao'][hipoteses['combinacao']]['barras'] if b['elementoId']==arm['barra']]
        if len(candidatos)!=1 or not candidatos[0]['pontos']:
            raise ValueError(f'{id_}: demanda ausente ou ambígua')
        rb=candidatos[0]
        valores=[p[k] for p in rb['pontos'] for k in ('n','my','mz','t','vy','vz')]
        if not all(math.isfinite(v) for v in valores):
            raise ValueError(f'{id_}: demanda não finita')
        if any(abs(p[k])>1e-8 for p in rb['pontos'] for k in ('n','mz','t')):
            raise ValueError(f'{id_}: interação fora da flexão simples suportada')
        momentos=[p['my'] for p in rb['pontos']]
        face=arm['face']
        if face not in ('inferior','superior') or any((m if face=='superior' else -m)>1e-8 for m in momentos):
            raise ValueError(f'{id_}: armadura não cobre a face tracionada atual')
        md=max(abs(m) for m in momentos)*1e6
        vd=max(math.hypot(p['vy'],p['vz']) for p in rb['pontos'])*1e3
        b,h=positivo(spec['b'])*1000,positivo(spec['h'])*1000
        phi=positivo(arm['phi_mm']); estribo=arm.get('estribo')
        d=h-positivo(arm['cobrimento_mm'])-(positivo(estribo['phi_mm']) if estribo else 0)-phi/2
        if d<=0: raise ValueError(f'{id_}: altura útil inválida')
        as_mm2=math.pi*phi**2/4*(positivo(arm['quantidade']) if 'quantidade' in arm else b/positivo(arm['espacamento_mm']))
        x=as_mm2*fyd/(alpha*fcd*lam*b)
        z=d-lam*x/2; mrd=as_mm2*fyd*z
        if mrd<=0: raise ValueError(f'{id_}: bloco comprimido fora da seção útil')
        r=dict(caso=id_,barra=arm['barra'],combinacao=hipoteses['combinacao'],
               hipotese=hipoteses['escopo'],fcd_MPa=fcd,fyd_MPa=fyd,b_mm=b,h_mm=h,
               d_mm=d,as_cm2=as_mm2/100,x_sobre_d=x/d,mrd_kNm=mrd/1e6,msd_kNm=md/1e6,
               utilizacao_flexao=md/mrd,atende_equilibrio_flexao=md<=mrd and x/d<=xd_lim,
               posicao_armadura=face,armadura=arm,estado_global='NAO_VERIFICADO')
        if estribo:
            fctm=.3*fck**(2/3); fctd=.7*fctm/positivo(mat['gamma_c'])
            vrd2=.27*(1-fck/250)*fcd*b*d; vc=.6*fctd*b*d
            asw=positivo(estribo['ramos'])*math.pi*positivo(estribo['phi_mm'])**2/4/positivo(estribo['espacamento_mm'])
            minimo=.2*fctm/fyk*b
            vrd3=vc+.9*d*min(fyd,435)*asw
            r.update(vsd_kN=vd/1000,vrd2_kN=vrd2/1000,vrd3_kN=vrd3/1000,
                     asw_s_cm2_m=asw*10,asw_min_cm2_m=minimo*10,
                     atende_cisalhamento_modelo_I=vd<=min(vrd2,vrd3) and asw>=minimo)
        out.append(r)
    return out

def desenhar(in_, java):
    fig, axs=plt.subplots(5,3,figsize=(15,17),layout='constrained')
    for row,c in enumerate(in_['casos']):
        bars,_,_=referencia(c,next(x for x in in_['combinacoes'] if x['nome']=='ELU_TESTE'))
        a=axs[row,0]
        for b in c['barras']:
            pts=np.array([c['nos'][b['i']],c['nos'][b['j']]])
            a.plot(pts[:,0],pts[:,1],'-o',lw=4,color='#164e63')
            mid=pts.mean(axis=0); a.annotate(f"{b['id']} · {b['b']*100:g}×{b['h']*100:g} cm",mid,xytext=(3,8),textcoords='offset points',fontsize=8)
        for ap in c['apoios']:
            x,z=c['nos'][ap['no']];a.plot(x,z,'s' if ap['ry'] else '^',ms=10,color='#b45309')
        a.set_title(c['titulo'],fontsize=10);a.set_xlabel('X (m)');a.set_ylabel('Z (m)');a.grid(alpha=.25);a.margins(.25)
        for k,(label,idx,factor) in enumerate([('Momento local · referência (kN·m)',2,1),('Deslocamento transversal linear (mm)',3,1000)],1):
            for spec,b in zip(c['barras'],bars,strict=True):
                xx=np.linspace(0,b['l'],201); yy=[secao(b,x)[idx]*factor for x in xx]
                axs[row,k].plot(xx,yy,label=spec['id']);
                rb=java[c['id']]['porCombinacao']['ELU_TESTE']['barras'][c['barras'].index(spec)]
                if idx==3: axs[row,k].scatter([p['s'] for p in rb['pontos']],[p['flecha']*1000 for p in rb['pontos']],s=10,color='black')
            axs[row,k].axhline(0,color='gray',lw=.6);axs[row,k].set_title(label,fontsize=10);axs[row,k].set_xlabel('s local (m)');axs[row,k].grid(alpha=.25);axs[row,k].legend(fontsize=7)
    fig.suptitle('Ensaios lineares didáticos · esforços ELU_TESTE · NÃO É PROJETO EXECUTIVO\nLinhas: referência Python; pontos de deslocamento: Java. Cargas e apoios no arquivo de casos.',fontsize=12)
    fig.savefig(OUT/'ensaios-estruturais.png',dpi=150)
    fig.savefig(OUT/'ensaios-estruturais.pdf')
    plt.close(fig)

def documentar(in_, java, out):
    """Toda demanda, capacidade e conclusão numérica vem dos dados desta execução."""
    linhas = ["# Memorial dos ensaios de desenho e análise estrutural", "",
        "Resultado: **mecânica linear comparada; segurança global ABNT NÃO COMPROVADA**.", "",
        "## Escopo e evidências", "",
        f"Foram comparados {len(in_['casos'])} casos isolados e o conjunto D1 montado a partir da geometria BIM. Todos permanecem NAO_VERIFICADO. As capturas registram o desenho de treino original; dimensões e esforços desta execução estão nas entradas e tabelas abaixo.", "",
        "- [Planta](desenho-planta.png), [cena 3D](desenho-3d.png) e [diagramas](ensaios-estruturais.pdf).",
        "- [Entradas](casos-estruturais.json), [resultados Java](resultados-motor.json), [comparação Python](comparacao-independente.json) e [hipóteses resistentes](hipoteses-resistencia.json).",
        "- [Matriz normativa](MATRIZ_NORMATIVA_ABNT.md), [inventário dos PDFs fornecidos](INVENTARIO_ACERVO.json) e [revisão independente](REVISAO_INDEPENDENTE_MOTOR.md).", "",
        "O teste de navegador cria elementos no motor real da área de treino local. A representação foi conferida por coordenadas, captura visual e pixels; isso não demonstra a segurança da estrutura.", "",
        "## Hipóteses e entradas atuais", "",
        "Unidades: m, kN, kN·m e kN/m² para o módulo de elasticidade. Barras Euler–Bernoulli, seção bruta, material linear, pequenas deformações e vínculos ideais. Deslocamentos não são flechas finais normativas.", ""]
    linhas += ['- '+h for h in in_['hipoteses']]
    linhas += ["", "Os casos isolados aplicam apenas as cargas explícitas abaixo. Uma ação horizontal artificial não representa vento calculado pela NBR 6123. D1 acrescenta o peso próprio no montador.", "",
        "| Caso | E (kN/m²) | Nós X,Z (m) | Barras: id, b×h (m) | Cargas distribuídas G,Q (kN/m) | Cargas nodais (kN) |",
        "|---|---:|---|---|---|---|"]
    for c in in_['casos']:
        secoes='; '.join(f"{b['id']}: {b['b']:g}×{b['h']:g}" for b in c['barras'])
        cargas='; '.join(f"barra {x['barra']}: {x['g']:g}, {x['q']:g}" for x in c['distribuidas']) or '—'
        linhas.append(f"| {c['id']} | {c['e_kN_m2']:g} | {json.dumps(c['nos'])} | {secoes} | {cargas} | {json.dumps(c['nodais'],ensure_ascii=False)} |")
    linhas += ["", "Vínculos completos no JSON. As combinações abaixo são hipóteses de teste; sua aplicabilidade por ocupação, situação e sinal favorável/desfavorável aguarda revisão integral da NBR 8681 e normas de ações.", "",
        "| Combinação | G | Q |", "|---|---:|---:|"]
    for c in in_['combinacoes']:
        linhas.append(f"| {c['nome']} | {c['g']:g} | {c['q']:g} |")
    linhas += ["", "## Esforços e deslocamentos — ELU_TESTE", "",
        "N e V são máximos em módulo. M segue eixos locais; o JSON conserva sinais por seção. A faixa L1 representa a largura indicada na entrada e não uma solução geral de placa. A flecha é a resultante transversal elástica, sem limites normativos verificados.", "",
        "| Caso / barra | N (kN) | V (kN) | M mín. (kN·m) | M máx. (kN·m) | Flecha (mm) |",
        "|---|---:|---:|---:|---:|---:|"]
    for c in out['casos']:
        for b in c['barras']:
            linhas.append(f"| {c['caso']} / {b['id']} | {b['n_abs_kN']:.3f} | {b['v_abs_kN']:.3f} | {b['m_negativo_kNm']:.3f} | {b['m_positivo_kNm']:.3f} | {b['deslocamento_transversal_mm']:.6f} |")
    linhas += ["", "### Reações de apoio", "", "| Caso / nó | Rx (kN) | Rz (kN) | My global (kN·m) |", "|---|---:|---:|---:|"]
    for c in out['casos']:
        for r in c['reacoes']:
            if max(abs(r[k]) for k in ['fx','fz','my'])>1e-8:
                linhas.append(f"| {c['caso']} / {r['no']} | {r['fx']:.3f} | {r['fz']:.3f} | {r['my']:.3f} |")
    d=in_['desenhoIntegrado']; di=out['integrado']
    linhas += ["", "### D1 — conjunto integrado", "",
        f"Geometria: {d['largura']:g}×{d['profundidade']:g}×{d['altura']:g} m; pilares {d['bPilar']*100:g}×{d['hPilar']*100:g} cm; vigas {d['bViga']*100:g}×{d['hViga']*100:g} cm; laje {d['hLaje']*100:g} cm. Revestimento {d['revestimento']:g} kN/m² e sobrecarga {d['sobrecarga']:g} kN/m².", "",
        "O montador usa peso específico de 25 kN/m³ e transfere a carga da laje proporcionalmente ao comprimento das vigas de contorno. Isso não é solução de placa. A soma dos sólidos não desconta suas interseções. O peso próprio dos pilares é carga axial distribuída; o encurtamento é conferido separadamente por qL²/(2EA). A decomposição Python em dois pórticos depende da simetria deste ensaio.", "",
        f"Carga linear nas vigas: G={di['q_g_vigas_kN_m']:.6f} kN/m e Q={di['q_q_vigas_kN_m']:.6f} kN/m. Reação vertical ELU total={di['carga_vertical_elu_kN']:.6f} kN; por apoio={di['reacao_por_apoio_kN']:.6f} kN.", "",
        "| Barra D1 | N (kN) | V (kN) | My mín. (kN·m) | My máx. (kN·m) | Mz abs. (kN·m) | T abs. (kN·m) |",
        "|---|---:|---:|---:|---:|---:|---:|"]
    for b in di['barras']:
        linhas.append(f"| {b['id']} | {b['n_abs_kN']:.3f} | {b['v_abs_kN']:.3f} | {b['m_min_kNm']:.3f} | {b['m_max_kNm']:.3f} | {b['mz_abs_kNm']:.3f} | {b['t_abs_kNm']:.3f} |")
    linhas += ["", "## Resistência parcial sob hipóteses explícitas", "",
        "Coeficientes e armaduras estão no arquivo de hipóteses, com hash abaixo; não se presumiu sua conformidade com a edição vigente. A geometria vem da entrada atual e Md/Vd vêm das seções da saída Java atual, após comparação mecânica. Demanda ausente, não finita, interação N–M, torção ou face tracionada não coberta impedem este cálculo parcial.", "",
        "Equilíbrio: T=As·fyd; x=T/(α·fcd·λ·b); z=d−λx/2; MRd=T·z. O resultado restrito compara Md≤MRd e x/d com o limite assumido; não verifica todo o detalhamento, aderência ou capacidade global.", "",
        "| Caso | Md (kN·m) | MRd idealizado (kN·m) | Md/MRd | x/d | Resultado restrito |", "|---|---:|---:|---:|---:|---|"]
    for r in out['resistencias_parciais']:
        resultado='ATENDE sob hipóteses' if r['atende_equilibrio_flexao'] else 'NÃO ATENDE à flexão idealizada'
        linhas.append(f"| {r['caso']} | {r['msd_kNm']:.3f} | {r['mrd_kNm']:.3f} | {r['utilizacao_flexao']:.3f} | {r['x_sobre_d']:.3f} | {resultado} |")
    linhas += ["", "| Caso | b×h (mm) | d (mm) | As (cm²) | Face | fcd (MPa) | fyd (MPa) |", "|---|---:|---:|---:|---|---:|---:|"]
    for r in out['resistencias_parciais']:
        linhas.append(f"| {r['caso']} | {r['b_mm']:g}×{r['h_mm']:g} | {r['d_mm']:.3f} | {r['as_cm2']:.6f} | {r['posicao_armadura']} | {r['fcd_MPa']:.6f} | {r['fyd_MPa']:.6f} |")
    linhas += ["", "Modelo I idealizado de cisalhamento, apenas nos casos com estribos:", "",
        "| Caso | Vsd (kN) | VRd2 (kN) | VRd3 (kN) | Asw/s (cm²/m) | Mínimo assumido (cm²/m) | Resultado restrito |",
        "|---|---:|---:|---:|---:|---:|---|"]
    for r in out['resistencias_parciais']:
        if 'vsd_kN' in r:
            resultado='ATENDE sob hipóteses' if r['atende_cisalhamento_modelo_I'] else 'NÃO ATENDE ao modelo I'
            linhas.append(f"| {r['caso']} | {r['vsd_kN']:.3f} | {r['vrd2_kN']:.3f} | {r['vrd3_kN']:.3f} | {r['asw_s_cm2_m']:.6f} | {r['asw_min_cm2_m']:.6f} | {resultado} |")
    linhas += ["", "## Comparação e pendências", "",
        "A referência Python usa montagem matricial própria, sem chamar o solver Java. Reações e esforços são comparados com tolerância absoluta 1,1×10⁻⁴ e relativa 10⁻⁸; deslocamentos por seção, com 10⁻¹⁰ m e relativa 10⁻⁷. Máximos de B1/B2/L1 têm soluções fechadas. Os testes Java adicionais incluem pórtico e flexão espacial: extremos da resultante de deslocamento são isolados pelo polinômio da derivada, além dos extremos de momento V=0.", "",
        "| Caso | Maior diferença de esforços/reações | Resíduo da referência |", "|---|---:|---:|"]
    for c in out['casos']:
        linhas.append(f"| {c['caso']} | {c['erro_max_esforcos_reacoes']:.3e} | {c['residuo_ref']:.3e} |")
    linhas += ["", f"D1: maior diferença de esforços={di['erro_max_esforcos']:.3e}.", "",
        "Correções verificadas: extremos de momento entre estações; máximo da resultante transversal; carga axial distribuída de peso próprio do pilar; precisão sem arredondamento nas decisões; coeficiente algébrico exato do bloco retangular declarado; memorial alimentado pela demanda atual. A regressão triplica as cargas de B1 e exige reprovação resistente com Md atualizado.", "",
        "Persistem: combinações normativas por ação/situação, N–Mx–My, estabilidade e segunda ordem, ligações, detalhamento/ancoragem, durabilidade, verificações da placa/punção quando aplicáveis, fissuração e deformação no tempo, materiais/execução e fundações com investigação geotécnica. Nem C1/P1/D1 nem a laje real têm comprovação resistente global. A faixa L1 não valida a laje D1.", "",
        "O acervo contém NBR 6118:2014 corrigida, utilizada como referência histórica catalogada; não contém as edições atuais de 6118/8681/6120/7480 nem norma específica de aço estrutural. O arquivo intitulado NBR 6122 é projeto de revisão de dezembro/2018, sem valor normativo. Memoriais/TCCs são fontes técnicas secundárias e requerem contraprova; não substituem as edições vigentes. O estado de leitura e cada pendência estão nos relatórios e na matriz.", "",
        "Resultado global de todos os casos: **NAO_VERIFICADO**.", "",
        "## Reprodução", "", "```sh",
        "mvn -q -f backend/pom.xml -pl cca-application -am -Dcca.validacao.saida=$PWD/docs/validacao/resultados-motor.json test",
        "python3 -m unittest discover -s scripts -p test_validar_estruturas.py",
        "python3 scripts/validar_estruturas.py", "```", "",
        "Requer Java 21/Maven e dependências de requirements-validacao.txt. Para repetir o desenho local: iniciar o frontend e executar e2e/motor-estruturas.spec.ts com Playwright.", "",
        f"SHA-256 das entradas: `{out['sha256_entradas']}`.", "",
        f"SHA-256 da saída Java: `{out['sha256_resultados_java']}`.", "",
        f"SHA-256 das hipóteses resistentes: `{out['sha256_hipoteses_resistencia']}`."]
    (OUT/'MEMORIAL_ENSAIOS.md').write_text('\n'.join(linhas)+'\n')

def main():
    entradas=OUT/'casos-estruturais.json';resultados=OUT/'resultados-motor.json'
    hipoteses=OUT/'hipoteses-resistencia.json'
    in_=json.loads(entradas.read_text());java=json.loads(resultados.read_text())
    resumo=conferir(in_,java)
    out=dict(escopo='Comparação mecânica linear e resistência parcial sob hipóteses; sem comprovação global ABNT',
             sha256_entradas=hashlib.sha256(entradas.read_bytes()).hexdigest(),
             sha256_resultados_java=hashlib.sha256(resultados.read_bytes()).hexdigest(),
             sha256_hipoteses_resistencia=hashlib.sha256(hipoteses.read_bytes()).hexdigest(),
             casos=resumo,integrado=conferir_integrado(in_,java),resistencias_parciais=resistencia_parcial(in_,java,json.loads(hipoteses.read_text())))
    (OUT/'comparacao-independente.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
    desenhar(in_,java)
    documentar(in_,java,out)
    print('D1 integrado: comparação OK; reação por apoio =', out['integrado']['reacao_por_apoio_kN'], 'kN')
    for c in resumo: print(c['caso'], 'comparação OK;', 'erro máximo:',f"{c['erro_max_esforcos_reacoes']:.3g}")
    for r in out['resistencias_parciais']:print(r['caso'],'Md=',r['msd_kNm'],'MRd=',round(r['mrd_kNm'],3),'kN.m; estado global NÃO VERIFICADO')

if __name__=='__main__': main()
