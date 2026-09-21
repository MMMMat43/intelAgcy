package ru.vyatsu.ita.lab09.repository;
import ru.vyatsu.ita.lab09.domain.AnalysisResult; import java.util.*;
public interface AnalysisRepository { void save(AnalysisResult result); Optional<AnalysisResult> findById(UUID id); List<AnalysisResult> findAll(); }
