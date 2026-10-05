package com.example.agent.history.ui

import com.example.agent.history.HistoryService
import com.example.agent.history.HistoryStatistics
import com.example.agent.history.RunDetails
import com.example.agent.history.RunFilter
import com.example.agent.history.RunSummaryRow
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel

class HistoryView(private val service: HistoryService) : JPanel(BorderLayout(8, 8)) {

    var noteRequester: (RunSummaryRow) -> String? = { null }
    var deleteConfirmer: (RunSummaryRow) -> Boolean = { false }
    var errorReporter: (String) -> Unit = {}

    val runsModel = RunsTableModel()
    val functionsModel = FunctionsTableModel()
    val branchesModel = BranchesTableModel()
    val casesModel = TestCasesTableModel()
    val notesModel = NotesTableModel()
    val complexModel = ListTableModel<com.example.agent.history.ComplexFunction>(
        listOf(
            Column("Функция", 190) { it.function },
            Column("Сложн.", 60) { it.complexity }
        )
    )

    private val allProjects = "Все проекты"
    private val projectBox = JComboBox<String>()
    private val minSpinner = JSpinner(SpinnerNumberModel(0, 0, 100, 5))
    private val maxSpinner = JSpinner(SpinnerNumberModel(100, 0, 100, 5))
    private val uncoveredBox = JCheckBox("Только с непокрытыми ветвями")
    private val applyButton = JButton("Применить")
    private val resetButton = JButton("Сбросить")
    private val refreshButton = JButton("Обновить")
    private val noteButton = JButton("Добавить заметку")
    private val deleteButton = JButton("Удалить запуск")

    val runsTable = JTable(runsModel)
    val tabs = JTabbedPane()
    private val detailsTitle = JLabel(" ")
    private val detailsSource = JLabel(" ")
    private val statRuns = JLabel()
    private val statProjects = JLabel()
    private val statCoverage = JLabel()
    private val statCases = JLabel()
    private val statUncovered = JLabel()

    var currentFilter: RunFilter = RunFilter()
        private set

    init {
        val font = HistoryTheme.font()
        background = HistoryTheme.panelBackground
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        add(buildFilterPanel(), BorderLayout.NORTH)
        add(buildCenter(), BorderLayout.CENTER)
        add(buildSummaryPanel(), BorderLayout.EAST)

        detailsTitle.font = HistoryTheme.bold(font.size2D + 1)
        detailsTitle.foreground = HistoryTheme.accent

        runsTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        runsTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) showSelected()
        }
        applyButton.addActionListener { applyFilter(filterFromControls()) }
        resetButton.addActionListener { resetFilter() }
        refreshButton.addActionListener { reload() }
        noteButton.addActionListener { requestNote() }
        deleteButton.addActionListener { requestDelete() }

        reload()
    }

    fun reload() {
        val selectedId = selectedRun()?.runId
        val projects = service.projects()
        val chosen = projectBox.selectedItem as String?
        projectBox.model = DefaultComboBoxModel((listOf(allProjects) + projects).toTypedArray())
        projectBox.selectedItem = if (chosen != null && chosen in projects) chosen else allProjects
        applyFilter(currentFilter, selectedId)
    }

    fun applyFilter(filter: RunFilter, keepRunId: String? = selectedRun()?.runId) {
        currentFilter = filter
        syncControls(filter)
        runsModel.update(service.runs(filter))
        updateStatistics(service.statistics(filter))
        val index = runsModel.rows.indexOfFirst { it.runId == keepRunId }.takeIf { it >= 0 } ?: if (runsModel.rowCount > 0) 0 else -1
        if (index >= 0) selectRun(index) else showDetails(null)
    }

    fun resetFilter() = applyFilter(RunFilter())

    fun selectRun(index: Int) {
        runsTable.setRowSelectionInterval(index, index)
        showSelected()
    }

    fun selectTab(index: Int) {
        tabs.selectedIndex = index
    }

    fun selectedRun(): RunSummaryRow? = runsTable.selectedRow.takeIf { it >= 0 }?.let { runsModel.rowAt(it) }

    fun addNote(text: String): Boolean {
        val run = selectedRun() ?: return false
        return runCatching { service.addNote(run.runId, text) }
            .onFailure { errorReporter(it.message ?: "Не удалось сохранить заметку") }
            .map { refreshNotes(run.runId); tabs.selectedIndex = TAB_NOTES; true }
            .getOrDefault(false)
    }

    fun deleteSelected(): Boolean {
        val run = selectedRun() ?: return false
        val deleted = service.deleteRun(run.runId)
        if (deleted) reload()
        return deleted
    }

    private fun requestNote() {
        val run = selectedRun() ?: return
        val text = noteRequester(run) ?: return
        addNote(text)
    }

    private fun requestDelete() {
        val run = selectedRun() ?: return
        if (deleteConfirmer(run)) deleteSelected()
    }

    private fun showSelected() {
        val run = selectedRun()
        showDetails(run?.let { service.details(it.runId) })
    }

    private fun showDetails(details: RunDetails?) {
        noteButton.isEnabled = details != null
        deleteButton.isEnabled = details != null
        if (details == null) {
            detailsTitle.text = "Запуск не выбран"
            detailsSource.text = " "
            listOf(functionsModel, branchesModel, casesModel, notesModel).forEach { it.update(emptyList()) }
            return
        }
        val summary = details.summary
        detailsTitle.text = "Запуск: ${summary.project}, ${HistoryFormat.date(summary.startedAt)}, " +
            "покрытие ветвей ${HistoryFormat.percent(summary.coverage)} (${HistoryFormat.branches(summary.coveredBranches, summary.totalBranches)})"
        detailsSource.text = "Источник: ${details.sourcePath}"
        functionsModel.update(details.functions)
        branchesModel.update(details.branches)
        casesModel.update(details.testCases)
        tabs.setTitleAt(TAB_FUNCTIONS, "Функции (${details.functions.size})")
        tabs.setTitleAt(TAB_BRANCHES, "Ветви (${details.branches.size})")
        tabs.setTitleAt(TAB_CASES, "Тест-кейсы (${details.testCases.size})")
        refreshNotes(summary.runId)
    }

    private fun refreshNotes(runId: String) {
        notesModel.update(service.notes(runId))
        tabs.setTitleAt(TAB_NOTES, "Заметки (${notesModel.rowCount})")
    }

    private fun updateStatistics(statistics: HistoryStatistics) {
        statRuns.text = "Запусков: ${statistics.runs}"
        statProjects.text = "Проектов: ${statistics.projects}"
        statCoverage.text = "Среднее покрытие: ${HistoryFormat.percent(statistics.averageCoverage)}"
        statCases.text = "Тест-кейсов: ${statistics.totalTestCases}"
        statUncovered.text = "Непокрытых ветвей: ${statistics.uncoveredBranches}"
        complexModel.update(statistics.mostComplex)
    }

    private fun filterFromControls(): RunFilter {
        val project = (projectBox.selectedItem as String?)?.takeIf { it != allProjects }
        val min = (minSpinner.value as Int)
        val max = (maxSpinner.value as Int)
        return RunFilter(
            project = project,
            minCoverage = if (min > 0) min / 100.0 else null,
            maxCoverage = if (max < 100) max / 100.0 else null,
            onlyWithUncovered = uncoveredBox.isSelected
        )
    }

    private fun syncControls(filter: RunFilter) {
        projectBox.selectedItem = filter.project ?: allProjects
        minSpinner.value = ((filter.minCoverage ?: 0.0) * 100).toInt()
        maxSpinner.value = ((filter.maxCoverage ?: 1.0) * 100).toInt()
        uncoveredBox.isSelected = filter.onlyWithUncovered
    }

    private fun buildFilterPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        panel.background = HistoryTheme.panelBackground
        panel.border = HistoryTheme.sectionBorder("Фильтр запусков")
        panel.add(JLabel("Проект:"))
        projectBox.preferredSize = Dimension(190, projectBox.preferredSize.height)
        panel.add(projectBox)
        panel.add(JLabel("Покрытие от, %:"))
        panel.add(minSpinner)
        panel.add(JLabel("до, %:"))
        panel.add(maxSpinner)
        uncoveredBox.isOpaque = false
        panel.add(uncoveredBox)
        panel.add(applyButton)
        panel.add(resetButton)
        panel.add(Box.createHorizontalStrut(16))
        panel.add(refreshButton)
        return panel
    }

    private fun buildCenter(): JPanel {
        val center = JPanel(BorderLayout(6, 6))
        center.isOpaque = false

        HistoryTheme.style(runsTable) { row ->
            val run = runsModel.rowAt(row) ?: return@style null
            if (run.uncoveredBranches > 0) HistoryTheme.uncoveredTint else null
        }
        val runsScroll = HistoryTheme.scroll(runsTable)
        runsScroll.preferredSize = Dimension(900, 170)
        val runsPanel = JPanel(BorderLayout())
        runsPanel.isOpaque = false
        runsPanel.border = HistoryTheme.sectionBorder("Запуски генерации тестов")
        runsPanel.add(runsScroll, BorderLayout.CENTER)
        center.add(runsPanel, BorderLayout.NORTH)

        val functionsTable = JTable(functionsModel)
        HistoryTheme.style(functionsTable) { row ->
            val function = functionsModel.rowAt(row) ?: return@style null
            if (function.totalBranches > function.coveredBranches) HistoryTheme.uncoveredTint else null
        }
        val branchesTable = JTable(branchesModel)
        HistoryTheme.style(branchesTable) { row ->
            val branch = branchesModel.rowAt(row) ?: return@style null
            if (com.example.agent.history.HistoryService.isUncovered(branch.state)) HistoryTheme.uncoveredTint else HistoryTheme.coveredTint
        }
        val casesTable = JTable(casesModel)
        HistoryTheme.style(casesTable)
        val notesTable = JTable(notesModel)
        HistoryTheme.style(notesTable)

        tabs.addTab("Функции", HistoryTheme.scroll(functionsTable))
        tabs.addTab("Ветви", HistoryTheme.scroll(branchesTable))
        tabs.addTab("Тест-кейсы", HistoryTheme.scroll(casesTable))
        tabs.addTab("Заметки", HistoryTheme.scroll(notesTable))

        val header = JPanel()
        header.layout = BoxLayout(header, BoxLayout.Y_AXIS)
        header.isOpaque = false
        header.add(detailsTitle)
        header.add(Box.createVerticalStrut(2))
        header.add(detailsSource)

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        actions.isOpaque = false
        actions.add(noteButton)
        actions.add(deleteButton)

        val detailsTop = JPanel(BorderLayout())
        detailsTop.isOpaque = false
        detailsTop.add(header, BorderLayout.CENTER)
        detailsTop.add(actions, BorderLayout.EAST)

        val detailsPanel = JPanel(BorderLayout(4, 6))
        detailsPanel.isOpaque = false
        detailsPanel.border = HistoryTheme.sectionBorder("Детали выбранного запуска")
        detailsPanel.add(detailsTop, BorderLayout.NORTH)
        detailsPanel.add(tabs, BorderLayout.CENTER)
        center.add(detailsPanel, BorderLayout.CENTER)
        return center
    }

    private fun buildSummaryPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = HistoryTheme.panelBackground
        panel.border = HistoryTheme.sectionBorder("Сводка по выборке")
        panel.preferredSize = Dimension(340, 400)
        val constraints = GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(2, 2, 2, 2)
        }
        listOf(statRuns, statProjects, statCoverage, statCases, statUncovered).forEachIndexed { index, label ->
            constraints.gridy = index
            panel.add(label, constraints)
        }
        val complexTitle = JLabel("Самые сложные функции:")
        complexTitle.font = HistoryTheme.bold()
        constraints.gridy = 5
        constraints.insets = Insets(10, 2, 4, 2)
        panel.add(complexTitle, constraints)
        val complexTable = JTable(complexModel)
        HistoryTheme.style(complexTable)
        val complexScroll = HistoryTheme.scroll(complexTable)
        complexScroll.preferredSize = Dimension(270, 160)
        constraints.gridy = 6
        constraints.insets = Insets(2, 2, 2, 2)
        constraints.fill = GridBagConstraints.BOTH
        constraints.weighty = 1.0
        panel.add(complexScroll, constraints)
        return panel
    }

    companion object {
        const val TAB_FUNCTIONS = 0
        const val TAB_BRANCHES = 1
        const val TAB_CASES = 2
        const val TAB_NOTES = 3
    }
}
