package br.com.civilconstructorai.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Aplicação de teste que sobe só a fatia de identidade/acesso/empresa/obra (mais a infraestrutura comum),
 * para os ITs deste módulo não dependerem do estado dos demais módulos durante o desenvolvimento em paralelo.
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = {"br.com.civilconstructorai.seguranca", "br.com.civilconstructorai.identidade"})
@ComponentScan(basePackages = "br.com.civilconstructorai", excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
        pattern = "br\\.com\\.civilconstructorai\\.(CivilConstructorAiApplication|(api\\.)?(lgpd|ia|modelo|notacao|prancha|interop|estrutura|geotecnia|quantitativo|orcamento|cassandra|geometria)\\..*)"))
public class AppIdentidadeTeste {
}
