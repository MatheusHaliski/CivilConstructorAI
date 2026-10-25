#!/usr/bin/env python3
"""Gera backend/schema.sql como concatenação exata das migrações Flyway (verificado por SchemaConsistencyTest)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
MIGRACOES = RAIZ / "backend" / "cca-infrastructure" / "src" / "main" / "resources" / "db" / "migration"
SAIDA = RAIZ / "backend" / "schema.sql"
RE = re.compile(r"^V(\d+)__.*\.sql$")


def main() -> int:
    arquivos = sorted((p for p in MIGRACOES.iterdir() if RE.match(p.name)), key=lambda p: int(RE.match(p.name).group(1)))
    partes = []
    for p in arquivos:
        texto = p.read_text(encoding="utf-8")
        if not texto.startswith("\n"):
            print(f"ERRO {p.name}: migração deve começar com linha em branco", file=sys.stderr)
            return 1
        partes.append(f"-- {p.name}\n{texto}{'' if texto.endswith(chr(10)) else chr(10)}")
    SAIDA.write_text("".join(partes), encoding="utf-8")
    print(f"schema.sql gerado com {len(arquivos)} migrações")
    return 0


if __name__ == "__main__":
    sys.exit(main())
