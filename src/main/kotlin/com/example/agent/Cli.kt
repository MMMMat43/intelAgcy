package com.example.agent

import com.example.agent.source.LocalFileSourceLoader

/**
 * Точка входа CLI интеллектуального агента.
 *
 * На этом этапе (Task 02) поддерживается флаг `--source <path>`, который
 * загружает Java-исходники (файл или директория) через [LocalFileSourceLoader]
 * и печатает количество найденных `.java`-файлов. Реальный анализ кода будет
 * добавлен в Task 03.
 */
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
}

internal fun parseSourceArgument(args: Array<String>): String? {
    val index = args.indexOf("--source")
    if (index == -1 || index + 1 >= args.size) {
        return null
    }
    return args[index + 1]
}
