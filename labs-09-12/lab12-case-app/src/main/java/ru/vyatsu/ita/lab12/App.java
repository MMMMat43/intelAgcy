package ru.vyatsu.ita.lab12;

import ru.vyatsu.ita.lab12.db.DatabaseManager;
import ru.vyatsu.ita.lab12.repository.ProjectRepository;
import ru.vyatsu.ita.lab12.repository.RunRepository;
import ru.vyatsu.ita.lab12.repository.TestCaseRepository;
import ru.vyatsu.ita.lab12.service.LabService;
import ru.vyatsu.ita.lab12.ui.MainFrame;

import javax.swing.SwingUtilities;

public final class App {
    public static void main(String[] args) {
        String dbPath = args.length > 0 ? args[0] : "lab11-database/intelligent_test_agent.db";
        var manager = new DatabaseManager(dbPath);
        var service = new LabService(
                new ProjectRepository(manager),
                new RunRepository(manager),
                new TestCaseRepository(manager));
        SwingUtilities.invokeLater(() -> new MainFrame(service).setVisible(true));
    }
}
