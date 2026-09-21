package ru.vyatsu.ita.lab09.factory;
import ru.vyatsu.ita.lab09.analysis.SourceAnalyzer; import ru.vyatsu.ita.lab09.repository.AnalysisRepository; import ru.vyatsu.ita.lab09.strategy.ScenarioGenerationStrategy;
public interface AnalysisComponentFactory { SourceAnalyzer createAnalyzer(); ScenarioGenerationStrategy createStrategy(); AnalysisRepository createRepository(); }
