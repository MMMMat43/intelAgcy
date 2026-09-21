package ru.vyatsu.ita.lab09.factory;
import ru.vyatsu.ita.lab09.analysis.*; import ru.vyatsu.ita.lab09.repository.*; import ru.vyatsu.ita.lab09.strategy.*; import java.util.List;
public final class DefaultAnalysisComponentFactory implements AnalysisComponentFactory {
 public SourceAnalyzer createAnalyzer(){return new SimpleJavaAnalyzer();}
 public ScenarioGenerationStrategy createStrategy(){return new CombinedStrategy(List.of(new BoundaryStrategy(),new NegativeStrategy()));}
 public AnalysisRepository createRepository(){return new InMemoryAnalysisRepository();}
}
