package ru.vyatsu.ita.lab09;
import ru.vyatsu.ita.lab09.domain.AnalysisRequest; import ru.vyatsu.ita.lab09.factory.*; import ru.vyatsu.ita.lab09.observer.ConsoleAnalysisObserver; import ru.vyatsu.ita.lab09.service.AnalysisService;
public final class App {
 public static void main(String[] args){
   var factory=new DefaultAnalysisComponentFactory(); var service=new AnalysisService(factory.createAnalyzer(),factory.createStrategy(),factory.createRepository());
   service.addObserver(new ConsoleAnalysisObserver());
   String code="public class Calculator { public int abs(int x){ if(x<0) return -x; return x; } }";
   var result=service.analyze(new AnalysisRequest("Calculator",code)); result.scenarios().forEach(System.out::println);
 }
}
