package ru.vyatsu.ita.lab09.strategy;
import ru.vyatsu.ita.lab09.domain.*; import java.util.*;
public final class NegativeStrategy implements ScenarioGenerationStrategy {
 public List<TestScenario> generate(List<SourceMethod> methods){ return methods.stream().map(m->new TestScenario(m.name(),"NEGATIVE","Проверить ошибочные данные и исключения для "+m.name())).toList(); }
 public String name(){return "Negative";}
}
