package com.onyx.keyboard.util

import java.net.URI
import java.util.Locale

/**
 * Helper object to detect, extract, and sanitize URLs found in text (such as clipboard contents).
 */
object ClipboardUrlDetector {

    private val URL_REGEX = Regex(
        """(https?://[^\s<>"'{}|\\^`]+|www\.[^\s<>"'{}|\\^`]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extracts the first valid HTTP or HTTPS URL detected in the provided [text].
     * If the text starts with "www.", "https://" is automatically prepended.
     * Trailing punctuation frequently attached to URLs in messages (e.g., '.', ',', ';', ')') is stripped.
     *
     * @param text The raw input string (e.g. from clipboard).
     * @return The extracted and sanitized URL string, or null if no valid URL is found.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > DownloadUrlValidator.DEFAULT_MAX_URL_LENGTH) return null

        // 1. Direct check if trimmed text is already a URL
        val directClean = cleanTrailingPunctuation(trimmed)
        if (isValidCandidate(directClean)) {
            return formatUrl(directClean)
        }

        // 2. Search for embedded URL pattern
        val match = URL_REGEX.find(trimmed) ?: return null
        val rawCandidate = cleanTrailingPunctuation(match.value)
        if (isValidCandidate(rawCandidate)) {
            return formatUrl(rawCandidate)
        }

        return null
    }

    /**
     * Checks whether the string represents or contains a plausible HTTP/HTTPS URL.
     */
    fun isLikelyUrl(text: String?): Boolean {
        return extractUrl(text) != null
    }

    private fun cleanTrailingPunctuation(str: String): String {
        var s = str.trim()
        val trailingPunctuation = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '"', '\'')
        while (s.isNotEmpty() && trailingPunctuation.contains(s.last())) {
            s = s.substring(0, s.length - 1).trim()
        }
        return s
    }

    private fun formatUrl(candidate: String): String {
        return if (candidate.startsWith("www.", ignoreCase = true)) {
            "https://$candidate"
        } else {
            candidate
        }
    }

    private fun isValidCandidate(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        val formatted = formatUrl(candidate)
        return try {
            val uri = URI(formatted)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.trim()
            (scheme == "http" || scheme == "https") && !host.isNullOrEmpty() && host.contains(".")
        } catch (_: Exception) {
            false
        }
    }
}
