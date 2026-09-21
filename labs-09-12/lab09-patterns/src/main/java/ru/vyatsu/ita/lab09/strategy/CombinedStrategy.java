package ru.vyatsu.ita.lab09.strategy;
import ru.vyatsu.ita.lab09.domain.*; import java.util.*;
public final class CombinedStrategy implements ScenarioGenerationStrategy {
 private final List<ScenarioGenerationStrategy> delegates;
 public CombinedStrategy(List<ScenarioGenerationStrategy> delegates){this.delegates=List.copyOf(delegates);}
 public List<TestScenario> generate(List<SourceMethod> methods){return delegates.stream().flatMap(s->s.generate(methods).stream()).distinct().toList();}
 public String name(){return "Combined";}
}
