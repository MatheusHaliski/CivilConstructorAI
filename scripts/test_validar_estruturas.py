"""Regressões de demanda/resistência e do memorial gerado (unittest padrão)."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import validar_estruturas as v


class MemorialTest(unittest.TestCase):
    def setUp(self):
        self.entrada = json.loads((v.OUT / 'casos-estruturais.json').read_text())
        self.java = json.loads((v.OUT / 'resultados-motor.json').read_text())
        self.hipoteses = json.loads((v.OUT / 'hipoteses-resistencia.json').read_text())

    def test_triplicar_cargas_reprova_flexao_e_muda_memorial(self):
        caso = next(c for c in self.entrada['casos'] if c['id'] == 'B1')
        for carga in caso['distribuidas']:
            for chave in ('g', 'q'):
                carga[chave] *= 3
        for combinacao in self.java['B1']['porCombinacao'].values():
            combinacao['deslocamentoMax'] *= 3
            for b in combinacao['barras']:
                for k in ('nMax', 'vMax', 'mMax', 'mMin', 'flechaMax'):
                    b[k] *= 3
                for p in b['pontos']:
                    for k in ('n', 'vy', 'vz', 't', 'my', 'mz', 'flecha'):
                        p[k] *= 3
            for r in combinacao['reacoes']:
                for k in ('fx', 'fy', 'fz', 'mx', 'my', 'mz'):
                    r[k] *= 3
        # A resposta linear continua correta; a resistência deve refletir a nova demanda.
        casos = v.conferir(self.entrada, self.java)
        resistencias = v.resistencia_parcial(self.entrada, self.java, self.hipoteses)
        b1 = next(r for r in resistencias if r['caso'] == 'B1')
        self.assertAlmostEqual(b1['msd_kNm'], 189)
        self.assertAlmostEqual(b1['vsd_kN'], 126)
        self.assertFalse(b1['atende_equilibrio_flexao'])
        out = dict(casos=casos, integrado=v.conferir_integrado(self.entrada, self.java),
                   resistencias_parciais=resistencias, sha256_entradas='teste',
                   sha256_resultados_java='teste', sha256_hipoteses_resistencia='teste')
        with tempfile.TemporaryDirectory() as temp, patch.object(v, 'OUT', Path(temp)):
            v.documentar(self.entrada, self.java, out)
            texto = (Path(temp) / 'MEMORIAL_ENSAIOS.md').read_text()
        self.assertIn('| B1 | 189.000 |', texto)
        self.assertIn('NÃO ATENDE', texto)

    def test_demanda_ausente_nao_vira_zero(self):
        self.java['B1']['porCombinacao']['ELU_TESTE']['barras'] = []
        with self.assertRaises(ValueError):
            v.resistencia_parcial(self.entrada, self.java, self.hipoteses)

    def test_altura_util_deriva_da_geometria_atual(self):
        antes = v.resistencia_parcial(self.entrada, self.java, self.hipoteses)[0]
        self.entrada['casos'][0]['barras'][0]['h'] = 0.45
        depois = v.resistencia_parcial(self.entrada, self.java, self.hipoteses)[0]
        self.assertAlmostEqual(antes['d_mm'] - depois['d_mm'], 50)
        self.assertLess(depois['mrd_kNm'], antes['mrd_kNm'])

    def test_interacao_nao_suportada_nao_vira_flexao_simples(self):
        b = self.java['B1']['porCombinacao']['ELU_TESTE']['barras'][0]
        b['pontos'][0]['n'] = -100
        with self.assertRaises(ValueError):
            v.resistencia_parcial(self.entrada, self.java, self.hipoteses)


if __name__ == '__main__':
    unittest.main()
