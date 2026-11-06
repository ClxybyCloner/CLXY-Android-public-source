package com.arman.firefoxvpn

/** Neutralizes service identifiers only at native display and diagnostic boundaries. */
internal object ProviderText {
    private val identifier = Regex(
        """[a-z0-9_./-]*(?:mozilla|firefox|fxa|gecko(?:view)?|thunderbird)[a-z0-9_./-]*""",
        RegexOption.IGNORE_CASE,
    )

    fun neutralize(value: String): String = identifier.replace(value, "[redacted]")

    fun error(error: Throwable): String = neutralize(error.message ?: error.toString())

    fun diagnostic(message: String, error: Throwable): String =
        neutralize("$message\n${error.stackTraceToString()}")
}
