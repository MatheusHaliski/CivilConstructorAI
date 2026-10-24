#!/usr/bin/env python3
"""Verificação "Docs por RF".

1. Todo RF do catálogo (docs/rf/README.md) tem doc com cabeçalho válido e os seis diagramas.
2. (--diff BASE) Mudança em arquivo listado em `paths` de um RF exige o doc desse RF no mesmo diff.
3. (--render) Renderiza todo bloco Mermaid de docs/ com @mermaid-js/mermaid-cli (mmdc).

Uso:
  python3 scripts/check_rf_docs.py                     # estrutura
  python3 scripts/check_rf_docs.py --render            # + render Mermaid
  python3 scripts/check_rf_docs.py --diff origin/main  # + regra de doc atualizado
"""
from __future__ import annotations

import argparse
import fnmatch
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
DOCS = RAIZ / "docs"
RF_DIR = DOCS / "rf"
CAMPOS = ("rf", "titulo", "modulo", "status", "cards", "paths")
STATUS = {"especificado", "em-andamento", "implementado", "homologado"}
DIAGRAMAS = {
    "casos de uso": "flowchart",
    "atividades": "flowchart",
    "classes": "classDiagram",
    "sequência": "sequenceDiagram",
    "estados": "stateDiagram-v2",
    "componentes": "flowchart",
}
BLOCO = re.compile(r"```mermaid\n(.*?)```", re.S)


def ler_cabecalho(texto: str) -> dict[str, object]:
    m = re.match(r"---\n(.*?)\n---\n", texto, re.S)
    if not m:
        return {}
    dados: dict[str, object] = {}
    chave = None
    for linha in m.group(1).splitlines():
        if linha.startswith("  - ") and chave:
            lista = dados.setdefault(chave, [])
            assert isinstance(lista, list)
            lista.append(linha[4:].strip())
        elif ":" in linha:
            chave, valor = linha.split(":", 1)
            valor = valor.strip()
            dados[chave.strip()] = valor if valor else []
    return dados


def catalogo() -> list[str]:
    texto = (RF_DIR / "README.md").read_text(encoding="utf-8")
    return re.findall(r"^\| (RF\d{2}) \|", texto, re.M)


def doc_do_rf(rf: str) -> Path | None:
    achados = sorted(RF_DIR.glob(f"{rf}-*.md"))
    return achados[0] if achados else None


def verificar_estrutura() -> list[str]:
    erros: list[str] = []
    rfs = catalogo()
    if not rfs:
        erros.append("docs/rf/README.md: catálogo vazio")
    for rf in rfs:
        doc = doc_do_rf(rf)
        if doc is None:
            erros.append(f"{rf}: doc ausente em docs/rf/")
            continue
        texto = doc.read_text(encoding="utf-8")
        cab = ler_cabecalho(texto)
        for campo in CAMPOS:
            if not cab.get(campo):
                erros.append(f"{doc.name}: cabeçalho sem '{campo}'")
        if cab.get("rf") and cab["rf"] != rf:
            erros.append(f"{doc.name}: rf '{cab['rf']}' difere do nome do arquivo")
        if cab.get("status") and cab["status"] not in STATUS:
            erros.append(f"{doc.name}: status '{cab['status']}' inválido")
        blocos = BLOCO.findall(texto)
        tipos = [b.strip().split()[0] for b in blocos]
        for nome, tipo in DIAGRAMAS.items():
            if not re.search(rf"^###? .*{nome}", texto, re.M | re.I):
                erros.append(f"{doc.name}: falta a seção de diagrama '{nome}'")
            if tipo not in tipos:
                erros.append(f"{doc.name}: falta diagrama Mermaid do tipo {tipo} ({nome})")
    return erros


def verificar_diff(base: str) -> list[str]:
    saida = subprocess.run(
        ["git", "diff", "--name-only", f"{base}...HEAD"],
        cwd=RAIZ, capture_output=True, text=True, check=True,
    ).stdout.split()
    mudados = set(saida)
    erros: list[str] = []
    for rf in catalogo():
        doc = doc_do_rf(rf)
        if doc is None:
            continue
        paths = ler_cabecalho(doc.read_text(encoding="utf-8")).get("paths") or []
        assert isinstance(paths, list)
        tocados = [f for f in mudados if any(fnmatch.fnmatch(f, p) for p in paths)]
        rel = str(doc.relative_to(RAIZ))
        if tocados and rel not in mudados:
            erros.append(f"{rf}: código mudou ({', '.join(sorted(tocados)[:3])}) sem atualizar {rel}")
    return erros


def renderizar() -> list[str]:
    mmdc = os.environ.get("MMDC", "mmdc")
    puppeteer = os.environ.get("PUPPETEER_CONFIG")
    erros: list[str] = []
    total = 0
    with tempfile.TemporaryDirectory() as tmp:
        for md in sorted(DOCS.rglob("*.md")):
            blocos = BLOCO.findall(md.read_text(encoding="utf-8"))
            if not blocos:
                continue
            total += len(blocos)
            # mmdc renderiza todos os blocos de um .md numa única abertura do navegador
            saida = Path(tmp) / md.relative_to(DOCS)
            saida.parent.mkdir(parents=True, exist_ok=True)
            cmd = [mmdc, "-q", "-i", str(md), "-o", str(saida)]
            if puppeteer:
                cmd += ["-p", puppeteer]
            r = subprocess.run(cmd, capture_output=True, text=True)
            if r.returncode != 0:
                msg = [l for l in (r.stderr or r.stdout).strip().splitlines() if l.strip()]
                erros.append(f"{md.relative_to(RAIZ)}: {' | '.join(msg[:3]) if msg else 'falhou'}")
    print(f"Mermaid: {total} diagramas em docs/, {len(erros)} arquivo(s) com erro")
    return erros


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--render", action="store_true")
    ap.add_argument("--diff", metavar="BASE")
    args = ap.parse_args()
    erros = verificar_estrutura()
    if args.diff:
        erros += verificar_diff(args.diff)
    if args.render:
        erros += renderizar()
    for e in erros:
        print(f"ERRO {e}")
    print("OK" if not erros else f"{len(erros)} erro(s)")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main())
