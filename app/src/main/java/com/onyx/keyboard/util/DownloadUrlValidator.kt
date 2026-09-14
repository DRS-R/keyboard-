package com.onyx.keyboard.util

import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * Helper object to validate the security and integrity of URL inputs before
 * initiating downloads, protecting against SSRF, dangerous schemes, path traversal,
 * and malicious payloads.
 */
object DownloadUrlValidator {

    const val DEFAULT_MAX_URL_LENGTH: Int = 2048

    // Blocked internal or private host domain suffixes
    private val BLOCKED_HOST_SUFFIXES = listOf(
        ".localhost",
        ".local",
        ".internal",
        ".lan",
        ".home",
        ".corp"
    )

    // Dangerous schemes that must never be initiated for remote downloads
    private val FORBIDDEN_SCHEMES = setOf(
        "file", "content", "javascript", "data", "blob", "intent",
        "android.resource", "ftp", "tftp", "tel", "sms", "mailto", "jar"
    )

    /**
     * Configuration options for URL validation.
     */
    data class Options(
        val requireHttps: Boolean = true,
        val allowPrivateIps: Boolean = false,
        val allowedDomains: Set<String>? = null,
        val allowedPorts: Set<Int>? = setOf(80, 443),
        val maxUrlLength: Int = DEFAULT_MAX_URL_LENGTH
    )

    /**
     * Result of URL validation with details.
     */
    sealed class ValidationResult {
        data class Valid(
            val uri: URI,
            val sanitizedUrl: String,
            val host: String,
            val scheme: String
        ) : ValidationResult()

        data class Invalid(
            val reason: String,
            val errorCode: ErrorCode
        ) : ValidationResult()

        val isValid: Boolean get() = this is Valid
    }

    enum class ErrorCode {
        EMPTY_OR_NULL,
        EXCEEDS_MAX_LENGTH,
        ILLEGAL_CHARACTERS,
        INVALID_SYNTAX,
        NOT_ABSOLUTE,
        FORBIDDEN_SCHEME,
        INSECURE_SCHEME,
        MISSING_HOST,
        PRIVATE_OR_LOOPBACK_HOST,
        DISALLOWED_DOMAIN,
        DISALLOWED_PORT,
        PATH_TRAVERSAL
    }

    /**
     * Quick helper function to validate whether a URL input string is secure and valid
     * before initiating a download.
     *
     * @param urlString The raw input URL to validate
     * @param requireHttps Whether HTTPS is mandatory (true by default)
     * @return true if the URL passes all security and integrity checks, false otherwise
     */
    @JvmStatic
    fun isValidDownloadUrl(urlString: String?, requireHttps: Boolean = true): Boolean {
        return validate(urlString, Options(requireHttps = requireHttps)).isValid
    }

    /**
     * Comprehensive validator helper function returning a [ValidationResult] detailing
     * security and integrity validity or specific rejection reason.
     *
     * @param urlString The input URL string to validate
     * @param options Security options (HTTPS requirement, allowed domains, etc.)
     * @return [ValidationResult.Valid] or [ValidationResult.Invalid]
     */
    @JvmStatic
    fun validate(urlString: String?, options: Options = Options()): ValidationResult {
        if (urlString.isNullOrBlank()) {
            return ValidationResult.Invalid("URL input is empty or null", ErrorCode.EMPTY_OR_NULL)
        }

        val trimmed = urlString.trim()

        if (trimmed.length > options.maxUrlLength) {
            return ValidationResult.Invalid(
                "URL length (${trimmed.length}) exceeds maximum limit (${options.maxUrlLength})",
                ErrorCode.EXCEEDS_MAX_LENGTH
            )
        }

        // Check for control characters, null bytes, or CRLF injection
        for (char in trimmed) {
            if (char.code < 32 || char.code == 127) {
                return ValidationResult.Invalid(
                    "URL contains illegal control characters or null bytes",
                    ErrorCode.ILLEGAL_CHARACTERS
                )
            }
        }

        // Path traversal checks (raw string check catches encoded %2e%2e and backslash traversal before/after normalization)
        if (trimmed.contains("..") || trimmed.contains("%2e%2e", ignoreCase = true) || trimmed.contains("\\")) {
            return ValidationResult.Invalid(
                "Path traversal sequences detected in URL",
                ErrorCode.PATH_TRAVERSAL
            )
        }

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return ValidationResult.Invalid("Malformed URL syntax: ${e.message}", ErrorCode.INVALID_SYNTAX)
        }

        if (!uri.isAbsolute || uri.scheme.isNullOrEmpty()) {
            return ValidationResult.Invalid("URL is not absolute or lacks a scheme", ErrorCode.NOT_ABSOLUTE)
        }

        val scheme = uri.scheme.lowercase(Locale.ROOT)
        if (FORBIDDEN_SCHEMES.contains(scheme)) {
            return ValidationResult.Invalid("Scheme '$scheme' is forbidden for downloads", ErrorCode.FORBIDDEN_SCHEME)
        }

        if (options.requireHttps && scheme != "https") {
            return ValidationResult.Invalid("Insecure scheme '$scheme'; HTTPS is required", ErrorCode.INSECURE_SCHEME)
        } else if (!options.requireHttps && scheme != "https" && scheme != "http") {
            return ValidationResult.Invalid("Scheme '$scheme' is not supported; must be HTTP or HTTPS", ErrorCode.FORBIDDEN_SCHEME)
        }

        val host = uri.host?.lowercase(Locale.ROOT)?.trim()
        if (host.isNullOrEmpty()) {
            return ValidationResult.Invalid("URL host is missing", ErrorCode.MISSING_HOST)
        }

        if (!isValidHostFormat(host)) {
            return ValidationResult.Invalid("Host contains invalid characters", ErrorCode.ILLEGAL_CHARACTERS)
        }

        // SSRF and private network checks
        if (!options.allowPrivateIps && isPrivateOrLoopbackHost(host)) {
            return ValidationResult.Invalid(
                "Access to private/loopback address '$host' is forbidden",
                ErrorCode.PRIVATE_OR_LOOPBACK_HOST
            )
        }

        // Domain whitelist check
        if (options.allowedDomains != null && options.allowedDomains.isNotEmpty()) {
            val matchesDomain = options.allowedDomains.any { allowed ->
                val normalized = allowed.lowercase(Locale.ROOT).trim()
                host == normalized || host.endsWith(".$normalized")
            }
            if (!matchesDomain) {
                return ValidationResult.Invalid(
                    "Host '$host' is not in allowed domains list",
                    ErrorCode.DISALLOWED_DOMAIN
                )
            }
        }

        // Port checks
        val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
        if (options.allowedPorts != null && !options.allowedPorts.contains(port)) {
            return ValidationResult.Invalid(
                "Port $port is not permitted for downloads",
                ErrorCode.DISALLOWED_PORT
            )
        }

        // Path traversal checks
        val path = uri.path ?: ""
        if (path.contains("..") || path.contains("%2e%2e", ignoreCase = true) || path.contains("\\")) {
            return ValidationResult.Invalid(
                "Path traversal sequences detected in URL path",
                ErrorCode.PATH_TRAVERSAL
            )
        }

        return ValidationResult.Valid(
            uri = uri,
            sanitizedUrl = uri.toASCIIString(),
            host = host,
            scheme = scheme
        )
    }

    /**
     * Checks if the given host resolves or points to a private, loopback, link-local, or internal host.
     */
    @JvmStatic
    fun isPrivateOrLoopbackHost(host: String): Boolean {
        val cleanHost = host.removePrefix("[").removeSuffix("]").trim().lowercase(Locale.ROOT)

        if (cleanHost == "localhost" || cleanHost == "0.0.0.0") {
            return true
        }

        for (suffix in BLOCKED_HOST_SUFFIXES) {
            if (cleanHost.endsWith(suffix)) return true
        }

        // IPv4 check
        if (cleanHost.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) {
            val parts = cleanHost.split(".")
            val octets = parts.mapNotNull { it.toIntOrNull() }
            if (octets.size == 4 && octets.all { it in 0..255 }) {
                val (o1, o2, _, _) = octets
                if (o1 == 127) return true // Loopback (127.0.0.0/8)
                if (o1 == 0) return true   // Current network (0.0.0.0/8)
                if (o1 == 10) return true  // Private class A (10.0.0.0/8)
                if (o1 == 172 && o2 in 16..31) return true // Private class B (172.16.0.0/12)
                if (o1 == 192 && o2 == 168) return true    // Private class C (192.168.0.0/16)
                if (o1 == 169 && o2 == 254) return true    // Link-local / Cloud metadata (169.254.0.0/16)
                if (o1 == 100 && o2 in 64..127) return true // Carrier-grade NAT (100.64.0.0/10)
                if (o1 == 192 && o2 == 0) return true      // IETF Protocol Assignments
                if (o1 == 198 && (o2 == 18 || o2 == 19 || o2 == 51)) return true // Benchmark / Documentation
                if (o1 == 203 && o2 == 0) return true      // Documentation
                if (o1 >= 224) return true                 // Multicast / Reserved (224.0.0.0+)
            }
        }

        // IPv6 check
        if (cleanHost == "::1" || cleanHost == "::" || cleanHost == "0:0:0:0:0:0:0:1") {
            return true
        }
        if (cleanHost.startsWith("fe8") || cleanHost.startsWith("fe9") ||
            cleanHost.startsWith("fea") || cleanHost.startsWith("feb")
        ) {
            return true // IPv6 link-local (fe80::/10)
        }
        if (cleanHost.startsWith("fc") || cleanHost.startsWith("fd")) {
            return true // IPv6 unique local (fc00::/7)
        }
        if (cleanHost.contains("::ffff:") || cleanHost.startsWith("ffff:")) {
            val lastColon = cleanHost.lastIndexOf(':')
            val ipv4Part = cleanHost.substring(lastColon + 1)
            if (isPrivateOrLoopbackHost(ipv4Part)) return true
        }

        // Dotless hostname rejection (e.g. "intranet", "metadata")
        if (!cleanHost.contains(".") && !cleanHost.contains(":")) {
            return true
        }

        return false
    }

    private fun isValidHostFormat(host: String): Boolean {
        val cleanHost = host.removePrefix("[").removeSuffix("]")
        if (cleanHost.isEmpty()) return false
        return cleanHost.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' || it == ':' }
    }

    /**
     * Validates data integrity against an expected hash using constant-time comparison.
     *
     * @param data Downloaded byte array
     * @param expectedHexHash Expected checksum in hexadecimal
     * @param algorithm Digest algorithm (defaults to "SHA-256")
     */
    @JvmStatic
    fun verifyChecksum(data: ByteArray, expectedHexHash: String, algorithm: String = "SHA-256"): Boolean {
        if (expectedHexHash.isBlank()) return false
        return try {
            val md = MessageDigest.getInstance(algorithm)
            val digest = md.digest(data)
            val actualHex = digest.joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                actualHex.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8),
                expectedHexHash.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates file integrity against an expected hash.
     */
    @JvmStatic
    fun verifyFileChecksum(file: File, expectedHexHash: String, algorithm: String = "SHA-256"): Boolean {
        if (!file.exists() || !file.isFile || expectedHexHash.isBlank()) return false
        return try {
            file.inputStream().use { stream ->
                verifyStreamChecksum(stream, expectedHexHash, algorithm)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates stream integrity against an expected hash.
     */
    @JvmStatic
    fun verifyStreamChecksum(stream: InputStream, expectedHexHash: String, algorithm: String = "SHA-256"): Boolean {
        if (expectedHexHash.isBlank()) return false
        return try {
            val md = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
            val digest = md.digest()
            val actualHex = digest.joinToString("") { "%02x".format(it) }
            MessageDigest.isEqual(
                actualHex.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8),
                expectedHexHash.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            false
        }
    }
}
