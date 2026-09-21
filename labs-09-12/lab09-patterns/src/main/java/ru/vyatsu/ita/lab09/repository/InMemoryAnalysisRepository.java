package ru.vyatsu.ita.lab09.repository;
import ru.vyatsu.ita.lab09.domain.AnalysisResult; import java.util.*; import java.util.concurrent.ConcurrentHashMap;
public final class InMemoryAnalysisRepository implements AnalysisRepository {
 private final Map<UUID,AnalysisResult> data=new ConcurrentHashMap<>();
 public void save(AnalysisResult r){data.put(r.id(),r);} public Optional<AnalysisResult> findById(UUID id){return Optional.ofNullable(data.get(id));}
 public List<AnalysisResult> findAll(){return List.copyOf(data.values());}
}
