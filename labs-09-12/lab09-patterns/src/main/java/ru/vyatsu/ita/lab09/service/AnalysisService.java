package ru.vyatsu.ita.lab09.service;
import ru.vyatsu.ita.lab09.analysis.*; import ru.vyatsu.ita.lab09.domain.*; import ru.vyatsu.ita.lab09.observer.*; import ru.vyatsu.ita.lab09.repository.*; import ru.vyatsu.ita.lab09.strategy.*;
import java.time.Instant; import java.util.*; import java.util.concurrent.CopyOnWriteArrayList;
public final class AnalysisService {
 private final SourceAnalyzer analyzer; private ScenarioGenerationStrategy strategy; private final AnalysisRepository repository;
 private final List<AnalysisObserver> observers=new CopyOnWriteArrayList<>();
 public AnalysisService(SourceAnalyzer a, ScenarioGenerationStrategy s, AnalysisRepository r){analyzer=a;strategy=s;repository=r;}
 public void setStrategy(ScenarioGenerationStrategy s){strategy=Objects.requireNonNull(s);} public void addObserver(AnalysisObserver o){observers.add(o);}
 public AnalysisResult analyze(AnalysisRequest request){
   var methods=analyzer.analyze(request); var scenarios=strategy.generate(methods);
   var result=new AnalysisResult(UUID.randomUUID(),request.projectName(),Instant.now(),methods,scenarios);
   repository.save(result); observers.forEach(o->o.onCompleted(result)); return result;
 }
}
