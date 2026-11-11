#!/usr/bin/env python3
"""Inventário local de PDFs fornecidos pelo usuário; não é auditoria normativa.

Não executa arquivos do acervo e não publica os textos. Cache integral de extração,
quando solicitado, fica no caminho explicitado (recomenda-se /tmp).
"""
import argparse
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path


def examinar(pdf, raiz, cache):
    with pdf.open('rb') as arquivo:
        sha256 = hashlib.file_digest(arquivo, 'sha256').hexdigest()
    info = {'arquivo': pdf.relative_to(raiz).as_posix(), 'bytes': pdf.stat().st_size,
            'sha256': sha256,
            'revisao_tecnica': 'NAO_REALIZADA_PELO_INVENTARIO'}
    meta = subprocess.run(['pdfinfo', str(pdf)], capture_output=True, text=True)
    if meta.returncode:
        return dict(info, estado='PDF_NAO_LIDO', erro=meta.stderr[:300])
    pagina = re.search(r'^Pages:\s*(\d+)', meta.stdout, re.M)
    info['paginas'] = int(pagina.group(1)) if pagina else None
    texto_path = cache / (info['sha256'] + '.txt')
    extracao = subprocess.run(['pdftotext', '-layout', '-enc', 'UTF-8', str(pdf), str(texto_path)],
                              capture_output=True, text=True)
    if extracao.returncode:
        return dict(info, estado='EXTRACAO_FALHOU', erro=extracao.stderr[:300])
    texto = texto_path.read_text(encoding='utf-8')
    info['caracteres_extraidos'] = len(texto)
    frente = '\f'.join(texto.split('\f')[:3])
    candidatos = re.findall(r'(?:ABNT\s*)?NBR\s*(?:ISO\s*)?\d+(?:-\d+)?\s*:\s*\d{4}', frente, re.I)
    info['referencias_na_frente'] = list(dict.fromkeys(re.sub(r'\s+', ' ', x).strip() for x in candidatos))
    # Referências da frente são candidatas: carimbos e citações também podem aparecer.
    info['identificacao_edicao'] = 'PENDENTE_CONFERENCIA_VISUAL'
    info['estado'] = 'TEXTO_EXTRAIDO' if len(re.findall(r'[A-Za-zÀ-ÿ]', texto)) >= 500 else 'PRECISA_OCR'
    info['texto_cache'] = str(texto_path) if cache else None
    return info


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('pasta', type=Path)
    p.add_argument('--saida', type=Path)
    p.add_argument('--cache-texto', type=Path)
    args = p.parse_args()
    raiz = args.pasta.resolve(strict=True)
    if not raiz.is_dir(): p.error('A pasta precisa ser um diretório existente.')
    with tempfile.TemporaryDirectory(prefix='cca-inventario-') as temporario:
        cache = args.cache_texto.resolve() if args.cache_texto else Path(temporario)
        cache.mkdir(parents=True, exist_ok=True)
        arquivos = []
        for pdf in sorted(raiz.rglob('*')):
            if pdf.suffix.lower() != '.pdf' or not pdf.is_file(): continue
            if not pdf.resolve().is_relative_to(raiz):
                raise ValueError(f'PDF fora da pasta de origem: {pdf}')
            dado = examinar(pdf, raiz, cache)
            if not args.cache_texto: dado.pop('texto_cache', None)
            arquivos.append(dado)
        resultado = {'escopo': 'Inventário técnico; extração não significa leitura/auditoria de cláusulas.',
                     'quantidade_pdfs': len(arquivos), 'arquivos': arquivos}
        texto = json.dumps(resultado, ensure_ascii=False, indent=2) + '\n'
        if args.saida:
            args.saida.parent.mkdir(parents=True, exist_ok=True)
            args.saida.write_text(texto, encoding='utf-8')
        else:
            print(texto, end='')


if __name__ == '__main__':
    main()
