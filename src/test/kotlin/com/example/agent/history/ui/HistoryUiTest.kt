package com.example.agent.history.ui

import com.example.agent.history.FunctionRow
import com.example.agent.history.HistoryService
import com.example.agent.history.RunFilter
import com.example.agent.storage.BranchRecord
import com.example.agent.storage.BranchState
import com.example.agent.storage.FunctionRunRecord
import com.example.agent.storage.InMemoryRunRepository
import com.example.agent.storage.RunRecord
import com.example.agent.storage.RunStatus
import com.example.agent.storage.TestCaseRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO

class HistoryUiTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun headless() {
            System.setProperty("java.awt.headless", "true")
            HistoryFormat.zone = ZoneOffset.UTC
        }
    }

    @TempDir
    lateinit var tempDir: Path

    private fun service(): HistoryService {
        val repository = InMemoryRunRepository()
        repository.save(
            RunRecord(
                id = "r1",
                sourcePath = "src/OrderProcessor.kt",
                startedAt = Instant.parse("2026-07-01T09:00:00Z"),
                durationMillis = 2500,
                status = RunStatus.COMPLETED,
                coverageMeasured = true,
                projectName = "OrderProcessor",
                functions = listOf(
                    FunctionRunRecord(
                        className = "OrderProcessor",
                        functionName = "calculateDiscount",
                        totalBranches = 2,
                        coveredBranches = 1,
                        testCases = 1,
                        signature = "price: Double, quantity: Int",
                        complexity = 6,
                        branches = listOf(
                            BranchRecord("OrderProcessor.calculateDiscount#1:if-then", "if (quantity <= 0)", BranchState.COVERED),
                            BranchRecord("OrderProcessor.calculateDiscount#2:if-else", "else of if (quantity <= 0)", BranchState.NOT_FOUND)
                        ),
                        cases = listOf(TestCaseRecord("c1", "NEGATIVE", "BOUNDARY", "zero quantity", "IllegalArgumentException", mapOf("price" to "1.0", "quantity" to "0")))
                    )
                )
            )
        )
        return HistoryService(repository)
    }

    @Test
    fun tableModelsExposeColumnsAndFormattedValues() {
        val runs = RunsTableModel()
        runs.update(service().runs())
        assertEquals(1, runs.rowCount)
        assertEquals(8, runs.columnCount)
        assertEquals("Проект", runs.getColumnName(0))
        assertEquals("OrderProcessor", runs.getValueAt(0, 0))
        assertEquals("01.07.2026 09:00", runs.getValueAt(0, 1))
        assertEquals("Завершён", runs.getValueAt(0, 2))
        assertEquals("1 / 2", runs.getValueAt(0, 6))
        assertEquals("50,0 %", runs.getValueAt(0, 7))
        assertFalse(runs.isCellEditable(0, 0))

        val functions = FunctionsTableModel()
        functions.update(listOf(FunctionRow("A", "f", "", null, 0, 0, 0)))
        assertEquals("—", functions.getValueAt(0, 3))
        assertEquals("—", functions.getValueAt(0, 5))

        val details = service().details("r1")!!
        val branches = BranchesTableModel().apply { update(details.branches) }
        assertEquals("Не найдено значение", branches.getValueAt(1, 3))
        assertEquals("2:if-else", branches.getValueAt(1, 1))
        val cases = TestCasesTableModel().apply { update(details.testCases) }
        assertEquals("Негативный", cases.getValueAt(0, 1))
        assertEquals("Границы условий", cases.getValueAt(0, 2))
        assertEquals("price = 1.0, quantity = 0", cases.getValueAt(0, 3))
    }

    @Test
    fun viewShowsSelectedRunAndAppliesFilters() {
        HistoryTheme.install()
        val view = HistoryView(service())

        assertEquals(1, view.runsModel.rowCount)
        assertEquals("r1", view.selectedRun()?.runId)
        assertEquals(1, view.functionsModel.rowCount)
        assertEquals(2, view.branchesModel.rowCount)
        assertEquals(1, view.casesModel.rowCount)

        view.applyFilter(RunFilter(minCoverage = 0.9))
        assertEquals(0, view.runsModel.rowCount)
        assertEquals(0, view.functionsModel.rowCount)

        view.resetFilter()
        assertEquals(1, view.runsModel.rowCount)
        assertTrue(view.addNote("Проверено"))
        assertEquals(1, view.notesModel.rowCount)
        assertEquals("Заметки (1)", view.tabs.getTitleAt(HistoryView.TAB_NOTES))
        assertFalse(view.addNote("   "))
        assertTrue(view.deleteSelected())
        assertEquals(0, view.runsModel.rowCount)
    }

    @Test
    fun panelsRenderIntoNonEmptyImagesInHeadlessMode() {
        HistoryTheme.install()
        val view = HistoryView(service())
        val image = HistoryScreenshots.render(view, Dimension(1280, 820))

        assertEquals(1280, image.width)
        assertEquals(820, image.height)
        val colors = HashSet<Int>()
        for (x in 0 until image.width step 7) for (y in 0 until image.height step 7) colors += image.getRGB(x, y)
        assertTrue(colors.size > 20, "rendered image looks empty: ${colors.size} colours")

        val run = view.selectedRun()!!
        val file = HistoryScreenshots.write(DialogFrame("Новая заметка", NoteEditorPanel(run, "текст"), listOf("OK")), Dimension(600, 280), tempDir.resolve("note.png"))
        assertTrue(Files.size(file) > 1000)
        assertEquals(600, ImageIO.read(file.toFile()).width)
    }

    @Test
    fun launcherParsesArgumentsAndCopiesDefaultDatabase() {
        val options = HistoryAppLauncher.parse(arrayOf("--db", "x.db", "--screenshot", "shots"))
        assertEquals(Path.of("x.db"), options.database)
        assertEquals(Path.of("shots"), options.screenshotDir)

        val defaults = HistoryAppLauncher.parse(emptyArray())
        assertEquals(null, defaults.database)
        val copy = HistoryAppLauncher.openDatabase(defaults)
        assertTrue(Files.exists(copy))
        assertTrue(copy.toAbsolutePath() != HistoryAppLauncher.defaultDatabase.toAbsolutePath())
        assertEquals(Files.size(HistoryAppLauncher.defaultDatabase), Files.size(copy))
    }
}
