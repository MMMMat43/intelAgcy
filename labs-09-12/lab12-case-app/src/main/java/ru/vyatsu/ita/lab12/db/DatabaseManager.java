package ru.vyatsu.ita.lab12.db;
import java.nio.file.*; import java.sql.*; import java.io.*;
public final class DatabaseManager {
 private final String url; public DatabaseManager(String path){url="jdbc:sqlite:"+path;}
 public Connection connection() throws SQLException {var c=DriverManager.getConnection(url); try(var s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");} return c;}
 public void initialize(Path schema) throws Exception {String sql=Files.readString(schema); try(var c=connection(); var s=c.createStatement()){for(String q:sql.split(";")){if(!q.isBlank())s.execute(q);}}}
}
