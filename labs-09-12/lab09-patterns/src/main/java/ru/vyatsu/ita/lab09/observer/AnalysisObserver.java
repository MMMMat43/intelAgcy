package ru.vyatsu.ita.lab09.observer;
import ru.vyatsu.ita.lab09.domain.AnalysisResult;
public interface AnalysisObserver { void onCompleted(AnalysisResult result); }
