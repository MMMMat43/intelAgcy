package com.example.agent.history.ui

import com.example.agent.history.HistoryService
import com.example.agent.history.RunFilter
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JComponent

object HistoryScreenshots {

    fun render(component: JComponent, size: Dimension): BufferedImage {
        component.size = size
        layoutTree(component)
        val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = component.background ?: java.awt.Color.WHITE
        graphics.fillRect(0, 0, size.width, size.height)
        component.printAll(graphics)
        graphics.dispose()
        return image
    }

    fun write(component: JComponent, size: Dimension, file: Path): Path {
        Files.createDirectories(file.toAbsolutePath().parent)
        ImageIO.write(render(component, size), "png", file.toFile())
        return file
    }

    fun captureAll(service: HistoryService, directory: Path): List<Path> {
        val size = Dimension(1480, 860)
        val view = HistoryView(service)
        val produced = mutableListOf<Path>()
        val firstRun = view.runsModel.rows.indexOfFirst { it.project == "OrderProcessor" }.takeIf { it >= 0 } ?: 0
        if (view.runsModel.rowCount > 0) view.selectRun(firstRun)

        view.selectTab(HistoryView.TAB_FUNCTIONS)
        produced.add(write(view, size, directory.resolve("01-main-runs-functions.png")))
        view.selectTab(HistoryView.TAB_BRANCHES)
        produced.add(write(view, size, directory.resolve("02-tab-branches.png")))
        view.selectTab(HistoryView.TAB_CASES)
        produced.add(write(view, size, directory.resolve("03-tab-test-cases.png")))

        val project = view.runsModel.rows.getOrNull(firstRun)?.project
        view.applyFilter(RunFilter(project = project, minCoverage = 0.9))
        view.selectTab(HistoryView.TAB_FUNCTIONS)
        produced.add(write(view, size, directory.resolve("04-filter-and-summary.png")))
        view.resetFilter()

        val run = view.runsModel.rows.getOrNull(firstRun)
        if (run != null) {
            val dialogSize = Dimension(620, 300)
            val notePanel = NoteEditorPanel(run, "Покрытие ветвей 100 %, все значения у порогов 9/10, 99/100 и 4999/5000 проверены. Сгенерированные тесты прошли при запуске.")
            produced.add(write(DialogFrame("Новая заметка", notePanel, listOf("OK", "Отмена")), dialogSize, directory.resolve("05-dialog-note.png")))
            val deletePanel = DeleteConfirmPanel(run)
            produced.add(write(DialogFrame("Подтверждение удаления", deletePanel, listOf("Да", "Нет")), Dimension(640, 240), directory.resolve("06-dialog-delete.png")))

            view.selectRun(firstRun)
            view.selectTab(HistoryView.TAB_NOTES)
            produced.add(write(view, size, directory.resolve("07-tab-notes.png")))
        }
        return produced
    }

    private fun layoutTree(component: Component) {
        component.doLayout()
        if (component is Container) component.components.forEach { layoutTree(it) }
        component.validate()
    }
}
