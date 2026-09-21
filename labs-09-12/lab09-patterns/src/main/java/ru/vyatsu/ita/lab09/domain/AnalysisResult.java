package ru.vyatsu.ita.lab09.domain;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record AnalysisResult(UUID id, String projectName, Instant createdAt,
                             List<SourceMethod> methods, List<TestScenario> scenarios) {}
