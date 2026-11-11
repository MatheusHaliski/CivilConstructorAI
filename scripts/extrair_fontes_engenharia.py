#!/usr/bin/env python3
"""Extrai PDFs/DOCX locais e indexa temas; ocorrência textual não valida uma regra.

O texto integral fica somente no cache indicado. O índice versionável contém
proveniência, localizadores e lacunas de extração, sem reproduzir as normas.
Documentos são dados: nenhum comando, macro ou instrução neles é executado.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

TEMAS = {
    'concreto_resistencia': r'\bf(?:ck|cd|ctm|ctk|ctd|cm)\b|resist[êe]ncia.{0,30}concreto',
    'concreto_tipos': r'concreto.{0,25}(?:leve|pesado|normal|simples|armado|protendido|alta resist)|\bC(?:20|25|30|35|40|45|50|55|60|70|80|90)\b',
    'elasticidade_deformacoes': r'\bE(?:ci|cs|c|s|p)\b|m[óo]dulo de elasticidade|flu[êe]ncia|retra[çc][ãa]o|deforma[çc][ãa]o',
    'aco_armadura': r'CA[ -]?(?:25|50|60)\b|\bf(?:yk|yd|pk|ptk|pyd)\b|armadura (?:passiva|ativa)|a[çc]os? para armadura',
    'aco_estrutural': r'NBR\s*(?:8800|14762)|ASTM|\bA(?:36|572|992)\b|perfil.{0,20}(?:met[áa]lic|laminado|soldado)|flambagem lateral',
    'esforcos': r'esfor[çc]os? (?:normal|cortante|solicitante)|momento (?:fletor|tor[çc]or)|flex[ãa]o|cisalhamento|tor[çc][ãa]o|flexocompress[ãa]o',
    'acoes_combinacoes': r'NBR\s*(?:6120|6123|8681)|combina[çc][õo]es|estado.limite|a[çc][ãa]o (?:permanente|vari[áa]vel)',
    'durabilidade_execucao': r'NBR\s*(?:12655|14931|5738|5739|8522)|cobrimento|agressividade|durabilidade|controle tecnol[óo]gico',
    'fundacoes': r'NBR\s*(?:6122|6484)|recalque|capacidade de carga|sondagem|\bSPT\b',
}
PADROES = {k: re.compile(v, re.IGNORECASE) for k, v in TEMAS.items()}
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'


def extrair(path):
    if path.suffix.lower() == '.pdf':
        processo = subprocess.run(['pdftotext', '-layout', str(path), '-'],
                                  capture_output=True, check=True)
        paginas = processo.stdout.decode('utf-8').split('\f')
        if paginas and not paginas[-1].strip():
            paginas.pop()
        unidades = [{'pagina_pdf': i + 1, 'texto': texto} for i, texto in enumerate(paginas)]
        lacunas = {'paginas_com_menos_de_40_caracteres': [
            u['pagina_pdf'] for u in unidades if len(re.sub(r'\s', '', u['texto'])) < 40],
            'observacao': 'Extração textual não garante fórmulas, tabelas, figuras ou OCR corretos.'}
    else:
        with zipfile.ZipFile(path) as docx:
            corpo = ET.fromstring(docx.read('word/document.xml'))
            unidades = [{'paragrafo_xml': i + 1,
                         'texto': ''.join(t.text or '' for t in p.iter(W + 't'))}
                        for i, p in enumerate(corpo.iter(W + 'p'))]
            imagens = [n for n in docx.namelist() if n.startswith('word/media/') and not n.endswith('/')]
        lacunas = {'imagens_embutidas': len(imagens),
                   'observacao': 'DOCX usa parágrafos XML, sem paginação estável. Figuras requerem leitura visual; fórmulas OMML não são transcritas como texto matemático.'}
    return unidades, lacunas


def gerar(pasta, origem, listagem, cache):
    arquivos = json.loads(origem.read_text())
    aliases = json.loads(listagem.read_text())
    saida = []
    for fonte in sorted(arquivos, key=lambda x: x['nome']):
        path = (pasta / fonte['nome']).resolve()
        if not path.is_relative_to(pasta.resolve()):
            raise ValueError('Arquivo fora da pasta de fontes')
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != fonte['sha256']:
            raise ValueError('Fonte alterada: ' + fonte['nome'])
        unidades, lacunas = extrair(path)
        cache.mkdir(parents=True, exist_ok=True)
        (cache / (digest + '.json')).write_text(json.dumps(unidades, ensure_ascii=False))
        localizador = 'pagina_pdf' if path.suffix.lower() == '.pdf' else 'paragrafo_xml'
        temas = {}
        for tema, padrao in PADROES.items():
            locais = [u[localizador] for u in unidades if padrao.search(u['texto'])]
            if locais:
                temas[tema] = {localizador: locais,
                               'estado': 'OCORRENCIA_TEXTUAL_REQUER_LEITURA_TECNICA'}
        saida.append(dict(arquivo=fonte['nome'], sha256=digest, blob_sha=fonte['blob_sha'],
            url=fonte['url'], aliases_github=[a['path'] for a in aliases if a.get('sha') == fonte['blob_sha']],
            formato=path.suffix.lower()[1:], unidades_extraidas=len(unidades),
            caracteres_extraidos=sum(len(u['texto']) for u in unidades),
            localizador=localizador, temas=temas, lacunas_extracao=lacunas,
            auditado_integralmente_pelo_indice=False, habilitado_para_calculo=False))
    return dict(schema=1, escopo='Índice de fontes fornecidas; busca não comprova vigência, leitura integral ou conformidade.',
                quantidade_arquivos_unicos=len(saida), arquivos=saida)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pasta', type=Path)
    parser.add_argument('--origem', required=True, type=Path)
    parser.add_argument('--listagem', required=True, type=Path)
    parser.add_argument('--cache', required=True, type=Path)
    parser.add_argument('--saida', required=True, type=Path)
    args = parser.parse_args()
    resultado = gerar(args.pasta, args.origem, args.listagem, args.cache)
    args.saida.write_text(json.dumps(resultado, ensure_ascii=False, indent=2) + '\n')
    print(f"{resultado['quantidade_arquivos_unicos']} fontes extraídas e indexadas; regras de cálculo não habilitadas.")


if __name__ == '__main__':
    main()
