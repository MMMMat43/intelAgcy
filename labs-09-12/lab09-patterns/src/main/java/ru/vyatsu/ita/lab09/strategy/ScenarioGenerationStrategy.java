package ru.vyatsu.ita.lab09.strategy;
import ru.vyatsu.ita.lab09.domain.*; import java.util.List;
public interface ScenarioGenerationStrategy { List<TestScenario> generate(List<SourceMethod> methods); String name(); }
