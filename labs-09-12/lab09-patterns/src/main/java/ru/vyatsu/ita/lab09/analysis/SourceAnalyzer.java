package ru.vyatsu.ita.lab09.analysis;
import ru.vyatsu.ita.lab09.domain.*; import java.util.List;
public interface SourceAnalyzer { List<SourceMethod> analyze(AnalysisRequest request); }
