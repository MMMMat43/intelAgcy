package ru.vyatsu.ita.lab09.domain;
import java.util.Objects;
public record AnalysisRequest(String projectName, String sourceCode) {
    public AnalysisRequest { Objects.requireNonNull(projectName); Objects.requireNonNull(sourceCode); }
}
