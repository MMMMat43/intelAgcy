package com.example.agent.history.ui

import com.example.agent.history.RunSummaryRow
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class NoteEditorPanel(run: RunSummaryRow, initialText: String = "") : JPanel(BorderLayout(6, 6)) {

    val textArea = JTextArea(initialText, 6, 46)

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        val title = JLabel("Заметка к запуску ${run.project} от ${HistoryFormat.date(run.startedAt)}")
        title.font = HistoryTheme.bold()
        add(title, BorderLayout.NORTH)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        add(JScrollPane(textArea), BorderLayout.CENTER)
        add(JLabel("Заметка сохраняется в таблицу note и удаляется вместе с запуском."), BorderLayout.SOUTH)
    }
}

class DeleteConfirmPanel(run: RunSummaryRow) : JPanel(BorderLayout(6, 6)) {

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        val title = JLabel("Удалить запуск ${run.project} от ${HistoryFormat.date(run.startedAt)}?")
        title.font = HistoryTheme.bold()
        add(title, BorderLayout.NORTH)
        val details = JTextArea(
            "Будут удалены записи запуска и связанные данные:\n" +
                "функций: ${run.functions}, тест-кейсов: ${run.testCases}, ветвей: ${run.totalBranches},\n" +
                "а также артефакты и заметки запуска (каскадное удаление по внешним ключам).\n" +
                "Действие нельзя отменить."
        )
        details.isEditable = false
        details.isOpaque = false
        details.font = HistoryTheme.font()
        add(details, BorderLayout.CENTER)
    }
}

class DialogFrame(title: String, content: Component, buttons: List<String>) : JPanel(BorderLayout()) {

    init {
        background = HistoryTheme.panelBackground
        border = BorderFactory.createLineBorder(HistoryTheme.border)
        val header = JLabel(" $title")
        header.font = HistoryTheme.bold()
        header.foreground = java.awt.Color.WHITE
        header.isOpaque = true
        header.background = HistoryTheme.accent
        header.preferredSize = Dimension(10, 30)
        add(header, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8))
        actions.isOpaque = false
        buttons.forEach { actions.add(JButton(it)) }
        add(actions, BorderLayout.SOUTH)
    }
}

object HistoryDialogs {

    fun askNote(parent: Component, run: RunSummaryRow): String? {
        val panel = NoteEditorPanel(run)
        val result = JOptionPane.showConfirmDialog(
            parent, panel, "Новая заметка", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        return if (result == JOptionPane.OK_OPTION) panel.textArea.text.takeIf { it.isNotBlank() } else null
    }

    fun confirmDelete(parent: Component, run: RunSummaryRow): Boolean = JOptionPane.showConfirmDialog(
        parent, DeleteConfirmPanel(run), "Подтверждение удаления", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
    ) == JOptionPane.YES_OPTION

    fun showError(parent: Component, message: String) {
        JOptionPane.showMessageDialog(parent, message, "Ошибка", JOptionPane.ERROR_MESSAGE)
    }
}
