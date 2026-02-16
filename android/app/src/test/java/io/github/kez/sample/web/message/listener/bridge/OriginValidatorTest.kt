package io.github.kez.sample.web.message.listener.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginValidatorTest {

    @Test
    fun `allows exact https origin`() {
        assertTrue(
            OriginValidator.isAllowed(
                sourceOrigin = "https://trusted.example.com",
                allowedOrigins = setOf("https://trusted.example.com")
            )
        )
    }

    @Test
    fun `blocks lookalike host`() {
        assertFalse(
            OriginValidator.isAllowed(
                sourceOrigin = "https://trusted.example.com.evil.com",
                allowedOrigins = setOf("https://trusted.example.com")
            )
        )
    }

    @Test
    fun `treats default https port as equivalent`() {
        assertTrue(
            OriginValidator.isAllowed(
                sourceOrigin = "https://trusted.example.com",
                allowedOrigins = setOf("https://trusted.example.com:443")
            )
        )
    }

    @Test
    fun `supports null origin only when explicitly allowed`() {
        assertTrue(
            OriginValidator.isAllowed(
                sourceOrigin = "null",
                allowedOrigins = setOf("null")
            )
        )

        assertFalse(
            OriginValidator.isAllowed(
                sourceOrigin = "null",
                allowedOrigins = setOf("https://trusted.example.com")
            )
        )
    }
}
