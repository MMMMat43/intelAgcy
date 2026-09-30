package com.example.agent.source

import java.io.File

class SourceLoaderFactory(private val allowRemote: Boolean = false) {

    fun create(location: String): SourceLoader =
        if (allowRemote && isRemoteRepository(location)) GitSourceLoader() else LocalFileSourceLoader()

    companion object {
        private val REMOTE_PATTERN = Regex("^(https?://|ssh://|git://|git@[A-Za-z0-9._-]+:).+")

        fun isRemoteRepository(location: String): Boolean {
            val trimmed = location.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("-")) return false
            if (File(trimmed).exists()) return false
            return REMOTE_PATTERN.matches(trimmed)
        }
    }
}
