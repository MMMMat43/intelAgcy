package ru.vyatsu.ita.lab12.repository;
import ru.vyatsu.ita.lab12.db.*; import ru.vyatsu.ita.lab12.model.*; import java.sql.*; import java.util.*;
public final class RunRepository {private final DatabaseManager db;public RunRepository(DatabaseManager db){this.db=db;}
 public List<AnalysisRun> findByProject(long id)throws SQLException{var out=new ArrayList<AnalysisRun>();try(var c=db.connection();var s=c.prepareStatement("SELECT id,project_id,status,started_at,llm_model FROM analysis_runs WHERE project_id=? ORDER BY id DESC")){s.setLong(1,id);try(var r=s.executeQuery()){while(r.next())out.add(new AnalysisRun(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5)));}}return out;}}
