package com.example.agent.analysis

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

object KotlinPsi {

    private val disposable = Disposer.newDisposable()

    private val factory: KtPsiFactory by lazy {
        System.setProperty("idea.io.use.fallback", "true")
        val configuration = CompilerConfiguration()
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "agent-analysis")
        configuration.put(JVMConfigurationKeys.NO_JDK, true)
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        KtPsiFactory(environment.project, false)
    }

    @Synchronized
    fun parse(fileName: String, content: String): KtFile {
        val name = if (fileName.endsWith(".kt")) fileName else "$fileName.kt"
        return factory.createFile(name, content.replace("\r\n", "\n"))
    }
}

fun <T : PsiElement> PsiElement.collectAll(type: Class<T>): List<T> {
    val result = mutableListOf<T>()
    fun walk(element: PsiElement) {
        if (type.isInstance(element)) {
            result += type.cast(element)
        }
        var child = element.firstChild
        while (child != null) {
            walk(child)
            child = child.nextSibling
        }
    }
    walk(this)
    return result
}

fun lineOf(text: String, offset: Int): Int {
    var line = 1
    val end = minOf(offset, text.length)
    for (i in 0 until end) {
        if (text[i] == '\n') line++
    }
    return line
}
