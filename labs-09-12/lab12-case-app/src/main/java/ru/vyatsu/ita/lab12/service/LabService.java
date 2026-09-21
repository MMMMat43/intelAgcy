package ru.vyatsu.ita.lab12.service;
import ru.vyatsu.ita.lab12.model.*; import ru.vyatsu.ita.lab12.repository.*; import java.sql.*; import java.util.*;
public final class LabService {private final ProjectRepository projects;private final RunRepository runs;private final TestCaseRepository tests;
 public LabService(ProjectRepository p,RunRepository r,TestCaseRepository t){projects=p;runs=r;tests=t;}
 public List<Project> projects()throws SQLException{return projects.findAll();} public Project addProject(String n,String p)throws SQLException{if(n.isBlank()||p.isBlank())throw new IllegalArgumentException("Заполните имя и путь");return projects.add(n,p);}
 public List<AnalysisRun> runs(long id)throws SQLException{return runs.findByProject(id);} public List<TestCaseItem> tests(long id)throws SQLException{return tests.findByProject(id);}
 public void addTestCase(long methodId,String type,String title,String expected)throws SQLException{if(title.isBlank())throw new IllegalArgumentException("Введите название");tests.add(methodId,type,title,expected);}
}
