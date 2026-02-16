package io.github.kez.sample.web.message.listener.bridge

import java.net.URI

internal object OriginValidator {

    fun isAllowed(sourceOrigin: String, allowedOrigins: Set<String>): Boolean {
        if ("*" in allowedOrigins) return true
        if (sourceOrigin == "null") return "null" in allowedOrigins

        val source = normalize(sourceOrigin) ?: return false

        return allowedOrigins
            .asSequence()
            .filter { it != "*" && it != "null" }
            .mapNotNull(::normalize)
            .any { it == source }
    }

    private fun normalize(raw: String): NormalizedOrigin? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = effectivePort(scheme, uri.port) ?: return null
        return NormalizedOrigin(scheme, host, port)
    }

    private fun effectivePort(scheme: String, rawPort: Int): Int? {
        if (rawPort >= 0) return rawPort
        return when (scheme) {
            "https" -> 443
            "http" -> 80
            else -> null
        }
    }

    private data class NormalizedOrigin(
        val scheme: String,
        val host: String,
        val port: Int
    )
}
