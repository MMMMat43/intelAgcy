package ru.vyatsu.ita.lab09;
import org.junit.jupiter.api.Test; import ru.vyatsu.ita.lab09.analysis.*; import ru.vyatsu.ita.lab09.domain.*; import ru.vyatsu.ita.lab09.repository.*; import ru.vyatsu.ita.lab09.service.*; import ru.vyatsu.ita.lab09.strategy.*; import java.util.List; import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class AnalysisServiceTest {
 @Test void combinedStrategyPersistsAndNotifies(){
   var repo=new InMemoryAnalysisRepository(); var service=new AnalysisService(new SimpleJavaAnalyzer(),new CombinedStrategy(List.of(new BoundaryStrategy(),new NegativeStrategy())),repo);
   var calls=new AtomicInteger(); service.addObserver(r->calls.incrementAndGet());
   var result=service.analyze(new AnalysisRequest("Demo","public class Demo { public int check(int x){ if(x>0) return 1; return 0; } }"));
   assertEquals(1,result.methods().size()); assertEquals(2,result.scenarios().size()); assertEquals(1,calls.get()); assertTrue(repo.findById(result.id()).isPresent());
 }
 @Test void strategyCanChangeAtRuntime(){
   var service=new AnalysisService(new SimpleJavaAnalyzer(),new BoundaryStrategy(),new InMemoryAnalysisRepository());
   var request=new AnalysisRequest("Demo","public class Demo { public int value(){ return 1; } }");
   assertEquals("BOUNDARY",service.analyze(request).scenarios().get(0).type()); service.setStrategy(new NegativeStrategy());
   assertEquals("NEGATIVE",service.analyze(request).scenarios().get(0).type());
 }
}
