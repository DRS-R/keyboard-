package com.onyx.keyboard.util

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class DownloadUrlValidatorTest {

    @Test
    fun testValidHttpsUrl() {
        val url = "https://cdn.onyx.app/dictionaries/arabic_v2.tsv"
        val result = DownloadUrlValidator.validate(url)
        assertTrue("Valid HTTPS URL should pass validation", result.isValid)
        assertTrue(DownloadUrlValidator.isValidDownloadUrl(url))
        if (result is DownloadUrlValidator.ValidationResult.Valid) {
            assertEquals("https", result.scheme)
            assertEquals("cdn.onyx.app", result.host)
        }
    }

    @Test
    fun testRejectInsecureHttpByDefault() {
        val url = "http://example.com/dict.tsv"
        val result = DownloadUrlValidator.validate(url)
        assertFalse("HTTP URL should fail when HTTPS is required", result.isValid)
        if (result is DownloadUrlValidator.ValidationResult.Invalid) {
            assertEquals(DownloadUrlValidator.ErrorCode.INSECURE_SCHEME, result.errorCode)
        }
    }

    @Test
    fun testAllowHttpWhenExplicitlyConfigured() {
        val url = "http://example.com/dict.tsv"
        val result = DownloadUrlValidator.validate(
            url,
            DownloadUrlValidator.Options(requireHttps = false)
        )
        assertTrue("HTTP URL should be allowed when requireHttps is false", result.isValid)
    }

    @Test
    fun testRejectForbiddenSchemes() {
        val forbidden = listOf(
            "file:///data/user/0/com.onyx.keyboard/databases/db",
            "content://contacts/people",
            "javascript:alert(1)",
            "data:text/plain;base64,SGVsbG8=",
            "blob:https://example.com/1234",
            "ftp://files.example.com/dict.zip"
        )
        for (url in forbidden) {
            val result = DownloadUrlValidator.validate(url)
            assertFalse("Scheme should be forbidden for $url", result.isValid)
        }
    }

    @Test
    fun testRejectSsrfAndPrivateIps() {
        val privateUrls = listOf(
            "https://127.0.0.1/admin",
            "https://localhost/api",
            "https://169.254.169.254/latest/meta-data/",
            "https://10.0.0.5/internal",
            "https://192.168.1.1/secret",
            "https://172.16.1.1/keys",
            "https://[::1]/status",
            "https://my-server.local/download",
            "https://backend.internal/dump"
        )
        for (url in privateUrls) {
            val result = DownloadUrlValidator.validate(url)
            assertFalse("Private/internal URL $url should be blocked", result.isValid)
            if (result is DownloadUrlValidator.ValidationResult.Invalid) {
                assertEquals(
                    "Should report private/loopback host error for $url",
                    DownloadUrlValidator.ErrorCode.PRIVATE_OR_LOOPBACK_HOST,
                    result.errorCode
                )
            }
        }
    }

    @Test
    fun testRejectPathTraversal() {
        val urlsWithTraversal = listOf(
            "https://example.com/downloads/../../system/etc",
            "https://example.com/downloads/%2e%2e/etc",
            "https://example.com/downloads/..\\windows"
        )
        for (url in urlsWithTraversal) {
            val result = DownloadUrlValidator.validate(url)
            assertFalse("Path traversal URL $url should be rejected", result.isValid)
            if (result is DownloadUrlValidator.ValidationResult.Invalid) {
                assertEquals(DownloadUrlValidator.ErrorCode.PATH_TRAVERSAL, result.errorCode)
            }
        }
    }

    @Test
    fun testRejectControlCharactersAndCrlf() {
        val maliciousUrls = listOf(
            "https://example.com/test\r\nHeader: inject",
            "https://example.com/test\u0000nullbyte"
        )
        for (url in maliciousUrls) {
            val result = DownloadUrlValidator.validate(url)
            assertFalse("URL with control chars should be rejected", result.isValid)
        }
    }

    @Test
    fun testAllowedDomainsWhitelist() {
        val options = DownloadUrlValidator.Options(
            allowedDomains = setOf("onyx.app", "github.com")
        )
        assertTrue(
            "Domain in whitelist should pass",
            DownloadUrlValidator.validate("https://onyx.app/dicts/ar.tsv", options).isValid
        )
        assertTrue(
            "Subdomain of allowed domain should pass",
            DownloadUrlValidator.validate("https://raw.github.com/onyx/repo/main/ar.tsv", options).isValid
        )
        val rejected = DownloadUrlValidator.validate("https://malicious.org/dict.tsv", options)
        assertFalse("Unwhitelisted domain should fail", rejected.isValid)
        if (rejected is DownloadUrlValidator.ValidationResult.Invalid) {
            assertEquals(DownloadUrlValidator.ErrorCode.DISALLOWED_DOMAIN, rejected.errorCode)
        }
    }

    @Test
    fun testDisallowedPorts() {
        val badPortUrl = "https://example.com:22/payload.bin"
        val result = DownloadUrlValidator.validate(badPortUrl)
        assertFalse("Non-standard port 22 should be rejected", result.isValid)
        if (result is DownloadUrlValidator.ValidationResult.Invalid) {
            assertEquals(DownloadUrlValidator.ErrorCode.DISALLOWED_PORT, result.errorCode)
        }
    }

    @Test
    fun testVerifyChecksum() {
        val payload = "Onyx Keyboard Dictionary TSV Content".toByteArray(Charsets.UTF_8)
        // echo -n "Onyx Keyboard Dictionary TSV Content" | sha256sum
        // 94c34cb212e3e5bba8f0e012e8c267ddb7325997d91d64aa9d76c7c427be55e4
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = md.digest(payload).joinToString("") { "%02x".format(it) }

        assertTrue(
            "Matching checksum should return true",
            DownloadUrlValidator.verifyChecksum(payload, expectedHash)
        )
        assertFalse(
            "Corrupted data should return false",
            DownloadUrlValidator.verifyChecksum("Tampered content".toByteArray(), expectedHash)
        )
        assertFalse(
            "Mismatched hash should return false",
            DownloadUrlValidator.verifyChecksum(payload, "0000000000000000000000000000000000000000000000000000000000000000")
        )

        // Stream verification
        val stream = ByteArrayInputStream(payload)
        assertTrue(
            "Stream checksum should return true for matching data",
            DownloadUrlValidator.verifyStreamChecksum(stream, expectedHash)
        )
    }
}
