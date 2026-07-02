package com.example.agent

import com.example.agent.analysis.JavaCodeAnalyzer
import com.example.agent.source.LocalFileSourceLoader
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Точка входа CLI интеллектуального агента.
 *
 * На этом этапе (Task 03) поддерживается флаг `--source <path>`, который
 * загружает Java-исходники (файл или директория) через [LocalFileSourceLoader],
 * анализирует их через [JavaCodeAnalyzer] и печатает результат анализа
 * (структуру кода: функции, ветвления, циклы, исключения, цикломатическую
 * сложность) в виде JSON. Генерация тестовых сценариев и кода будет
 * добавлена в последующих задачах.
 */
private val jsonMapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .enable(SerializationFeature.INDENT_OUTPUT)

fun main(args: Array<String>) {
    val source = parseSourceArgument(args)
    if (source == null) {
        println("Usage: --source <path>")
        return
    }

    val files = try {
        LocalFileSourceLoader().load(source)
    } catch (e: IllegalArgumentException) {
        println("Source: $source")
        println("Error: ${e.message}")
        return
    }

    println("Source: $source")
    println("Found ${files.size} Java source file(s)")

    val structure = JavaCodeAnalyzer().analyze(source, files)
    println(jsonMapper.writeValueAsString(structure))
}

internal fun parseSourceArgument(args: Array<String>): String? {
    val index = args.indexOf("--source")
    if (index == -1 || index + 1 >= args.size) {
        return null
    }
    return args[index + 1]
}
