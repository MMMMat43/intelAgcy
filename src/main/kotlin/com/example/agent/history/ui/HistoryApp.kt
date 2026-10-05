package com.example.agent.history.ui

import com.example.agent.history.HistoryService
import com.example.agent.storage.SqliteRunRepository
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.system.exitProcess

data class HistoryAppOptions(
    val database: Path?,
    val screenshotDir: Path?
)

object HistoryAppLauncher {

    val defaultDatabase: Path = Paths.get("labs-agent-materials", "lab11-database", "agent_history.db")

    fun parse(args: Array<String>): HistoryAppOptions {
        var database: Path? = null
        var screenshots: Path? = null
        var index = 0
        while (index < args.size) {
            when (args[index]) {
                "--db" -> database = args.getOrNull(++index)?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                "--screenshot" -> screenshots = args.getOrNull(++index)?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
            }
            index++
        }
        return HistoryAppOptions(database, screenshots)
    }

    fun openDatabase(options: HistoryAppOptions): Path {
        options.database?.let { return it.toAbsolutePath() }
        require(Files.exists(defaultDatabase)) { "Не найдена база истории: ${defaultDatabase.toAbsolutePath()}" }
        val copy = Files.createTempFile("agent-history-", ".db")
        Files.copy(defaultDatabase, copy, StandardCopyOption.REPLACE_EXISTING)
        copy.toFile().deleteOnExit()
        return copy
    }

    fun service(database: Path): HistoryService = HistoryService(SqliteRunRepository(database.toString()))
}

fun main(args: Array<String>) {
    val options = HistoryAppLauncher.parse(args)
    if (options.screenshotDir != null) System.setProperty("java.awt.headless", "true")
    val database = runCatching { HistoryAppLauncher.openDatabase(options) }.getOrElse {
        System.err.println(it.message)
        exitProcess(1)
    }
    HistoryTheme.install()
    val service = HistoryAppLauncher.service(database)

    val screenshots = options.screenshotDir
    if (screenshots != null) {
        val files = HistoryScreenshots.captureAll(service, screenshots)
        files.forEach { println("Saved: ${it.toAbsolutePath()}") }
        println("Database used: $database")
        return
    }

    SwingUtilities.invokeLater {
        val frame = JFrame("Журнал запусков генерации тестов")
        val view = HistoryView(service)
        view.noteRequester = { run -> HistoryDialogs.askNote(frame, run) }
        view.deleteConfirmer = { run -> HistoryDialogs.confirmDelete(frame, run) }
        view.errorReporter = { message -> HistoryDialogs.showError(frame, message) }
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.contentPane = view
        frame.size = Dimension(1280, 820)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}
