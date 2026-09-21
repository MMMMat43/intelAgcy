package ru.vyatsu.ita.lab12;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.io.TempDir; import ru.vyatsu.ita.lab12.db.*; import ru.vyatsu.ita.lab12.repository.*; import ru.vyatsu.ita.lab12.service.*; import java.nio.file.*; import static org.junit.jupiter.api.Assertions.*;
class RepositoryTest {@TempDir Path temp;
 @Test void projectCanBeAddedAndRead()throws Exception{var db=new DatabaseManager(temp.resolve("test.db").toString());db.initialize(Path.of("../lab11-database/schema.sql"));var repo=new ProjectRepository(db);var p=repo.add("Demo",temp.toString());assertEquals("Demo",p.name());assertEquals(1,repo.findAll().size());}
 @Test void serviceValidatesProject()throws Exception{var db=new DatabaseManager(temp.resolve("test2.db").toString());db.initialize(Path.of("../lab11-database/schema.sql"));var service=new LabService(new ProjectRepository(db),new RunRepository(db),new TestCaseRepository(db));assertThrows(IllegalArgumentException.class,()->service.addProject("","x"));}
}
