package com.onyx.keyboard.util

import org.junit.Assert.*
import org.junit.Test

class ClipboardUrlDetectorTest {

    @Test
    fun testExtractPlainHttpsUrl() {
        val input = "https://cdn.onyx.app/dicts/arabic_extended.tsv"
        val detected = ClipboardUrlDetector.extractUrl(input)
        assertEquals("https://cdn.onyx.app/dicts/arabic_extended.tsv", detected)
        assertTrue(ClipboardUrlDetector.isLikelyUrl(input))
    }

    @Test
    fun testExtractPlainHttpUrl() {
        val input = "http://example.com/wordlist.txt"
        val detected = ClipboardUrlDetector.extractUrl(input)
        assertEquals("http://example.com/wordlist.txt", detected)
        assertTrue(ClipboardUrlDetector.isLikelyUrl(input))
    }

    @Test
    fun testExtractWwwUrlPrependsHttps() {
        val input = "www.onyx.app/download/dictionary.tsv"
        val detected = ClipboardUrlDetector.extractUrl(input)
        assertEquals("https://www.onyx.app/download/dictionary.tsv", detected)
        assertTrue(ClipboardUrlDetector.isLikelyUrl(input))
    }

    @Test
    fun testExtractUrlWithTrailingPunctuation() {
        val input1 = "Check this dictionary: https://onyx.app/dict.tsv."
        assertEquals("https://onyx.app/dict.tsv", ClipboardUrlDetector.extractUrl(input1))

        val input2 = "Download from (https://onyx.app/dict.tsv), and try it!"
        assertEquals("https://onyx.app/dict.tsv", ClipboardUrlDetector.extractUrl(input2))
    }

    @Test
    fun testExtractUrlWithQueryParamsAndHash() {
        val input = "https://example.com/api/get_dict?lang=ar&version=2#section1"
        val detected = ClipboardUrlDetector.extractUrl(input)
        assertEquals("https://example.com/api/get_dict?lang=ar&version=2#section1", detected)
    }

    @Test
    fun testNonUrlReturnsNull() {
        assertNull(ClipboardUrlDetector.extractUrl(null))
        assertNull(ClipboardUrlDetector.extractUrl(""))
        assertNull(ClipboardUrlDetector.extractUrl("   "))
        assertNull(ClipboardUrlDetector.extractUrl("السلام عليكم ورحمة الله"))
        assertNull(ClipboardUrlDetector.extractUrl("Just some random text with 1234 numbers"))
        assertFalse(ClipboardUrlDetector.isLikelyUrl("Hello world!"))
    }

    @Test
    fun testUrlSurroundedByWhitespace() {
        val input = "   \n\t https://example.com/dict.tsv \t \n  "
        val detected = ClipboardUrlDetector.extractUrl(input)
        assertEquals("https://example.com/dict.tsv", detected)
    }

    @Test
    fun testExtractedUrlWithDownloadUrlValidatorIntegration() {
        val clipboardContent = "Check out the new Arabic lexicon: https://cdn.onyx.app/dicts/arabic_v2.tsv"
        val detected = ClipboardUrlDetector.extractUrl(clipboardContent)
        assertNotNull(detected)

        val validation = DownloadUrlValidator.validate(detected)
        assertTrue("Detected valid HTTPS URL should pass validation", validation.isValid)
        assertTrue(validation is DownloadUrlValidator.ValidationResult.Valid)
        val validResult = validation as DownloadUrlValidator.ValidationResult.Valid
        assertEquals("cdn.onyx.app", validResult.host)
        assertEquals("https", validResult.scheme)
    }

    @Test
    fun testExtractedInsecureUrlFlaggedByValidator() {
        val clipboardContent = "Insecure link: http://example.com/dict.tsv"
        val detected = ClipboardUrlDetector.extractUrl(clipboardContent)
        assertNotNull(detected)

        val validation = DownloadUrlValidator.validate(detected)
        assertFalse("HTTP URL should fail default HTTPS requirement in validator", validation.isValid)
        assertTrue(validation is DownloadUrlValidator.ValidationResult.Invalid)
        val invalidResult = validation as DownloadUrlValidator.ValidationResult.Invalid
        assertEquals(DownloadUrlValidator.ErrorCode.INSECURE_SCHEME, invalidResult.errorCode)
    }
}
