package br.com.civilconstructorai.acabamento;

import br.com.civilconstructorai.estrutura.modelo.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import java.util.*;

public final class MedidorAcabamentos {
    private MedidorAcabamentos() {}
    public record Linha(String pavimentoId,String elementoId,String id,String materialId,String face,double espessura_m,
            double areaBrutaM2,double descontoVaosM2,double areaLiquidaM2,double volumeM3,Double pesoInstaladoKN,
            double quantidadeCompraM2,Double pecasCompra,String especificacao,Double larguraPeca_m,Double alturaPeca_m,double junta_m,double perda_percentual) {}
    public static double areaLiquida(ModeloEstrutural m,ElementoEstrutural e) {return Math.max(0,areaBruta(e)-desconto(m,e));}
    public static double areaBruta(ElementoEstrutural e) {return e.tipo()==TipoElementoEstrutural.PAREDE?e.comprimento()*e.h():ElementoEstrutural.areaPoligono(e.contorno());}
    public static double desconto(ModeloEstrutural m,ElementoEstrutural e) {
        if(e.tipo()!=TipoElementoEstrutural.PAREDE)return 0;
        var g=new GeometryFactory();var vazios=new ArrayList<Geometry>();double l=e.comprimento();
        for(var a:m.elementos())if(!a.rascunhoIa() && e.id().equals(a.paredeId()) && (a.tipo()==TipoElementoEstrutural.PORTA||a.tipo()==TipoElementoEstrutural.JANELA)) {
            double x=a.posicao()*l-a.b()/2, y=a.peitoril();
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(a.b())||!Double.isFinite(a.h())||a.b()<=0||a.h()<=0||x < -1e-8||y < 0||x+a.b()>l+1e-8||y+a.h()>e.h()+1e-8)throw new IllegalArgumentException("Vão fora da parede ou com dimensões inválidas: "+a.id());
            vazios.add(g.createPolygon(new Coordinate[]{new Coordinate(x,y),new Coordinate(x+a.b(),y),new Coordinate(x+a.b(),y+a.h()),new Coordinate(x,y+a.h()),new Coordinate(x,y)}));
        }
        return vazios.isEmpty()?0:UnaryUnionOp.union(vazios).getArea();
    }
    public static List<Linha> medir(ModeloEstrutural m) {
        var linhas=new ArrayList<Linha>();
        for(var e:m.elementos())if(!e.rascunhoIa()&&!e.acabamentos().isEmpty()) {
            if(e.tipo()!=TipoElementoEstrutural.PAREDE&&e.tipo()!=TipoElementoEstrutural.LAJE)throw new IllegalArgumentException("Acabamentos apenas em paredes e lajes.");
            double bruta=areaBruta(e),desc=desconto(m,e),liquida=Math.max(0,bruta-desc);
            for(var a:e.acabamentos()) {a.validarFace(e.tipo()==TipoElementoEstrutural.PAREDE);double compra=liquida*(1+a.perda_percentual()/100);Double pecas=a.larguraPeca_m()==null?null:Math.ceil(compra/((a.larguraPeca_m()+a.junta_m())*(a.alturaPeca_m()+a.junta_m())));
                linhas.add(new Linha(e.pavimentoId(),e.id(),a.id(),a.materialId(),a.face(),a.espessura_m(),bruta,desc,liquida,liquida*a.espessura_m(),a.pesoPorArea()==null?null:liquida*a.pesoPorArea(),compra,pecas,a.especificacao(),a.larguraPeca_m(),a.alturaPeca_m(),a.junta_m(),a.perda_percentual()));}
        }
        return List.copyOf(linhas);
    }
    public static double pesoCamadasPorArea(ElementoEstrutural e) {double total=0;for(var a:e.acabamentos()){a.validarFace(e.tipo()==TipoElementoEstrutural.PAREDE);if(a.pesoPorArea()==null)throw new IllegalArgumentException("Informe peso específico ou massa superficial da camada "+a.id());total+=a.pesoPorArea();}return total;}
}
