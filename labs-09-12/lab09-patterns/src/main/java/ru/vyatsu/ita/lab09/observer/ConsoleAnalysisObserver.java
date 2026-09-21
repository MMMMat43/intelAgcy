package ru.vyatsu.ita.lab09.observer;
import ru.vyatsu.ita.lab09.domain.AnalysisResult;
public final class ConsoleAnalysisObserver implements AnalysisObserver {
 public void onCompleted(AnalysisResult r){System.out.printf("Анализ %s завершён: методов=%d, сценариев=%d%n",r.projectName(),r.methods().size(),r.scenarios().size());}
}
