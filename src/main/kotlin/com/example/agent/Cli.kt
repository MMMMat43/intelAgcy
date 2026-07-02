package com.example.agent

/**
 * Точка входа CLI интеллектуального агента.
 *
 * На этом этапе (Task 01) поддерживается только флаг `--source <path>`,
 * который просто выводит переданный путь. Реальная логика загрузки
 * исходников и анализа кода будет добавлена в задачах 02/03.
 */
fun main(args: Array<String>) {
    val source = parseSourceArgument(args)
    if (source != null) {
        println("Source: $source")
    } else {
        println("Usage: --source <path>")
    }
}

internal fun parseSourceArgument(args: Array<String>): String? {
    val index = args.indexOf("--source")
    if (index == -1 || index + 1 >= args.size) {
        return null
    }
    return args[index + 1]
}
