package ru.vyatsu.ita.lab12.repository;
import ru.vyatsu.ita.lab12.db.*; import ru.vyatsu.ita.lab12.model.*; import java.sql.*; import java.util.*;
public final class ProjectRepository { private final DatabaseManager db; public ProjectRepository(DatabaseManager db){this.db=db;}
 public List<Project> findAll() throws SQLException {var out=new ArrayList<Project>(); try(var c=db.connection();var s=c.prepareStatement("SELECT id,name,root_path,language FROM projects ORDER BY id");var r=s.executeQuery()){while(r.next())out.add(new Project(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)));} return out;}
 public Project add(String name,String path) throws SQLException {try(var c=db.connection();var s=c.prepareStatement("INSERT INTO projects(name,root_path,language) VALUES(?,?,'JAVA')",Statement.RETURN_GENERATED_KEYS)){s.setString(1,name);s.setString(2,path);s.executeUpdate();try(var k=s.getGeneratedKeys()){k.next();return new Project(k.getLong(1),name,path,"JAVA");}}}
}
