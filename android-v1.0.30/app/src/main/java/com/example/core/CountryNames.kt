package com.example.core

import java.util.Locale

object CountryNames {
    private val names = mapOf(
        "AD" to "Andorra", "AE" to "United Arab Emirates", "AF" to "Afghanistan",
        "AG" to "Antigua & Barbuda", "AI" to "Anguilla", "AL" to "Albania",
        "AM" to "Armenia", "AO" to "Angola", "AQ" to "Antarctica", "AR" to "Argentina",
        "AS" to "American Samoa", "AT" to "Austria", "AU" to "Australia", "AW" to "Aruba",
        "AX" to "Åland Islands", "AZ" to "Azerbaijan", "BA" to "Bosnia & Herzegovina",
        "BB" to "Barbados", "BD" to "Bangladesh", "BE" to "Belgium", "BF" to "Burkina Faso",
        "BG" to "Bulgaria", "BH" to "Bahrain", "BI" to "Burundi", "BJ" to "Benin",
        "BL" to "St. Barthélemy", "BM" to "Bermuda", "BN" to "Brunei", "BO" to "Bolivia",
        "BQ" to "Caribbean Netherlands", "BR" to "Brazil", "BS" to "Bahamas", "BT" to "Bhutan",
        "BV" to "Bouvet Island", "BW" to "Botswana", "BY" to "Belarus", "BZ" to "Belize",
        "CA" to "Canada", "CC" to "Cocos Islands", "CD" to "Congo - Kinshasa",
        "CF" to "Central African Republic", "CG" to "Congo - Brazzaville", "CH" to "Switzerland",
        "CI" to "Côte d’Ivoire", "CK" to "Cook Islands", "CL" to "Chile", "CM" to "Cameroon",
        "CN" to "China", "CO" to "Colombia", "CR" to "Costa Rica", "CU" to "Cuba",
        "CV" to "Cape Verde", "CW" to "Curaçao", "CX" to "Christmas Island", "CY" to "Cyprus",
        "CZ" to "Czechia", "DE" to "Germany", "DJ" to "Djibouti", "DK" to "Denmark",
        "DM" to "Dominica", "DO" to "Dominican Republic", "DZ" to "Algeria", "EC" to "Ecuador",
        "EE" to "Estonia", "EG" to "Egypt", "EH" to "Western Sahara", "ER" to "Eritrea",
        "ES" to "Spain", "ET" to "Ethiopia", "FI" to "Finland", "FJ" to "Fiji",
        "FK" to "Falkland Islands", "FM" to "Micronesia", "FO" to "Faroe Islands",
        "FR" to "France", "GA" to "Gabon", "GB" to "United Kingdom", "GD" to "Grenada",
        "GE" to "Georgia", "GF" to "French Guiana", "GG" to "Guernsey", "GH" to "Ghana",
        "GI" to "Gibraltar", "GL" to "Greenland", "GM" to "Gambia", "GN" to "Guinea",
        "GP" to "Guadeloupe", "GQ" to "Equatorial Guinea", "GR" to "Greece",
        "GS" to "South Georgia & South Sandwich Islands", "GT" to "Guatemala", "GU" to "Guam",
        "GW" to "Guinea-Bissau", "GY" to "Guyana", "HK" to "Hong Kong",
        "HM" to "Heard & McDonald Islands", "HN" to "Honduras", "HR" to "Croatia",
        "HT" to "Haiti", "HU" to "Hungary", "ID" to "Indonesia", "IE" to "Ireland",
        "IL" to "Israel", "IM" to "Isle of Man", "IN" to "India",
        "IO" to "British Indian Ocean Territory", "IQ" to "Iraq", "IR" to "Iran",
        "IS" to "Iceland", "IT" to "Italy", "JE" to "Jersey", "JM" to "Jamaica",
        "JO" to "Jordan", "JP" to "Japan", "KE" to "Kenya", "KG" to "Kyrgyzstan",
        "KH" to "Cambodia", "KI" to "Kiribati", "KM" to "Comoros", "KN" to "St. Kitts & Nevis",
        "KP" to "North Korea", "KR" to "South Korea", "KW" to "Kuwait", "KY" to "Cayman Islands",
        "KZ" to "Kazakhstan", "LA" to "Laos", "LB" to "Lebanon", "LC" to "St. Lucia",
        "LI" to "Liechtenstein", "LK" to "Sri Lanka", "LR" to "Liberia", "LS" to "Lesotho",
        "LT" to "Lithuania", "LU" to "Luxembourg", "LV" to "Latvia", "LY" to "Libya",
        "MA" to "Morocco", "MC" to "Monaco", "MD" to "Moldova", "ME" to "Montenegro",
        "MF" to "St. Martin", "MG" to "Madagascar", "MH" to "Marshall Islands",
        "MK" to "North Macedonia", "ML" to "Mali", "MM" to "Myanmar", "MN" to "Mongolia",
        "MO" to "Macao", "MP" to "Northern Mariana Islands", "MQ" to "Martinique",
        "MR" to "Mauritania", "MS" to "Montserrat", "MT" to "Malta", "MU" to "Mauritius",
        "MV" to "Maldives", "MW" to "Malawi", "MX" to "Mexico", "MY" to "Malaysia",
        "MZ" to "Mozambique", "NA" to "Namibia", "NC" to "New Caledonia", "NE" to "Niger",
        "NF" to "Norfolk Island", "NG" to "Nigeria", "NI" to "Nicaragua", "NL" to "Netherlands",
        "NO" to "Norway", "NP" to "Nepal", "NR" to "Nauru", "NU" to "Niue", "NZ" to "New Zealand",
        "OM" to "Oman", "PA" to "Panama", "PE" to "Peru", "PF" to "French Polynesia",
        "PG" to "Papua New Guinea", "PH" to "Philippines", "PK" to "Pakistan", "PL" to "Poland",
        "PM" to "St. Pierre & Miquelon", "PN" to "Pitcairn Islands", "PR" to "Puerto Rico",
        "PS" to "Palestinian Authority", "PT" to "Portugal", "PW" to "Palau", "PY" to "Paraguay",
        "QA" to "Qatar", "RE" to "Réunion", "RO" to "Romania", "RS" to "Serbia", "RU" to "Russia",
        "RW" to "Rwanda", "SA" to "Saudi Arabia", "SB" to "Solomon Islands", "SC" to "Seychelles",
        "SD" to "Sudan", "SE" to "Sweden", "SG" to "Singapore", "SH" to "St Helena",
        "SI" to "Slovenia", "SJ" to "Svalbard & Jan Mayen", "SK" to "Slovakia",
        "SL" to "Sierra Leone", "SM" to "San Marino", "SN" to "Senegal", "SO" to "Somalia",
        "SR" to "Suriname", "SS" to "South Sudan", "ST" to "São Tomé & Príncipe",
        "SV" to "El Salvador", "SX" to "Sint Maarten", "SY" to "Syria", "SZ" to "Eswatini",
        "TC" to "Turks & Caicos Islands", "TD" to "Chad", "TF" to "French Southern Territories",
        "TG" to "Togo", "TH" to "Thailand", "TJ" to "Tajikistan", "TK" to "Tokelau",
        "TL" to "Timor-Leste", "TM" to "Turkmenistan", "TN" to "Tunisia", "TO" to "Tonga",
        "TR" to "Türkiye", "TT" to "Trinidad & Tobago", "TV" to "Tuvalu", "TW" to "Taiwan",
        "TZ" to "Tanzania", "UA" to "Ukraine", "UG" to "Uganda",
        "UM" to "U.S. Outlying Islands", "US" to "United States", "UY" to "Uruguay",
        "UZ" to "Uzbekistan", "VA" to "Vatican City", "VC" to "St. Vincent & Grenadines",
        "VE" to "Venezuela", "VG" to "British Virgin Islands", "VI" to "U.S. Virgin Islands",
        "VN" to "Vietnam", "VU" to "Vanuatu", "WF" to "Wallis & Futuna", "WS" to "Samoa",
        "XK" to "Kosovo", "YE" to "Yemen", "YT" to "Mayotte", "ZA" to "South Africa",
        "ZM" to "Zambia", "ZW" to "Zimbabwe",
    )

    val routingRegions = listOf(
        "" to "None (All traffic via VPN)",
        "ir" to "Iran",
        "cn" to "China",
        "ru" to "Russia",
        "af" to "Afghanistan",
        "tr" to "Türkiye",
        "id" to "Indonesia",
        "br" to "Brazil",
    )

    fun isRecommendedPlace(code: String): Boolean = code.uppercase(Locale.US) == "REC"

    fun locationName(code: String, fallbackName: String = ""): String {
        if (isRecommendedPlace(code)) return "Recommended"
        if (code.equals("fastest", ignoreCase = true)) return "Fastest"
        val upper = code.uppercase(Locale.US)
        return names[upper] ?: fallbackName.ifEmpty { upper }
    }

    fun formatBytes(bytes: Long): String {
        val v = bytes.coerceAtLeast(0L)
        return when {
            v >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", v / 1_073_741_824.0)
            v >= 1_048_576L -> String.format(Locale.US, "%.1f MB", v / 1_048_576.0)
            v >= 1024L -> "${v / 1024L} KB"
            else -> "$v B"
        }
    }

    /** Formats bytes/sec in Mbps with comma decimal matching clxy-ui (`20,3` / `0,0`). */
    fun formatMbpsComma(bytesPerSec: Long): String {
        val mbps = (bytesPerSec.coerceAtLeast(0L) * 8.0) / 1_000_000.0
        return String.format(Locale.US, "%.1f", mbps).replace('.', ',')
    }

    fun formatTimerParts(totalSeconds: Long): Pair<String, String> {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val hrs = clamped / 3600
        val mins = (clamped % 3600) / 60
        val secs = clamped % 60
        val hrsStr = String.format(Locale.US, "%02d", hrs)
        val minSecStr = String.format(Locale.US, ":%02d:%02d", mins, secs)
        return hrsStr to minSecStr
    }

    private val UPSTREAM_HOST_RE = Regex(
        """(?i)(?:[a-z0-9-]+\.)*(?:mozilla\.(?:org|com|net)|firefox\.com|fastly-masque\.net)(?::(\d+))?""",
    )
    private val GUARDIAN_HOST_RE = Regex("""(?i)\bguardian\.[a-z0-9.-]+(?::(\d+))?""")
    private val MOZILLA_VPN_RE = Regex("""(?i)\bmozilla\s+vpn\b""")
    private val MOZILLA_WORD_RE = Regex("""(?i)\bmozilla(?:'s)?\b""")
    private val FIREFOX_ACCOUNT_RE = Regex("""(?i)\bfirefox\s+accounts?\b""")
    private val FIREFOX_WORD_RE = Regex("""(?i)\bfirefox(?:'s)?\b""")
    private val GUARDIAN_WORD_RE = Regex("""(?i)\bguardian\b""")
    private val FXA_WORD_RE = Regex("""(?i)\bfxa\b""")

    /**
     * Censors upstream Mozilla / Firefox / Guardian / Fastly identifiers in core logs and live
     * connections using neutral/obfuscated tokens so internal upstream names never appear.
     */
    fun censorLogText(raw: String): String {
        if (raw.isEmpty()) return raw
        var out = UPSTREAM_HOST_RE.replace(raw) { m ->
            val port = m.groupValues.getOrNull(1).orEmpty()
            if (port.isNotEmpty()) "service-endpoint.invalid:$port" else "service-endpoint.invalid"
        }
        out = GUARDIAN_HOST_RE.replace(out) { m ->
            val port = m.groupValues.getOrNull(1).orEmpty()
            if (port.isNotEmpty()) "service-endpoint.invalid:$port" else "service-endpoint.invalid"
        }
        out = MOZILLA_WORD_RE.replace(out, "xsvc-m0")
        out = FIREFOX_WORD_RE.replace(out, "xsvc-f1")
        out = GUARDIAN_WORD_RE.replace(out, "xsvc-g2")
        out = FXA_WORD_RE.replace(out, "xsvc-a3")
        return out
    }

    /**
     * Replaces any remaining upstream Mozilla / Firefox / Guardian / FxA branding or hosts in
     * user-facing UI (DNS diagnostics, relay diagnostics, errors, notices, toasts) with CLXY.
     */
    fun brandAsClxy(raw: String): String {
        if (raw.isEmpty()) return raw
        var out = raw
            .replace(Regex("""(?i)\bvpn\.mozilla\.org\b"""), "api.clxy.network")
            .replace(Regex("""(?i)(?:[a-z0-9-]+\.)*accounts\.firefox\.com\b"""), "auth.clxy.network")
            .replace(Regex("""(?i)\bidentity\.mozilla\.com\b"""), "id.clxy.network")
            .replace(Regex("""(?i)\bproduct-details\.mozilla\.org\b"""), "meta.clxy.network")
        out = UPSTREAM_HOST_RE.replace(out) { m ->
            val port = m.groupValues.getOrNull(1).orEmpty()
            if (port.isNotEmpty()) "edge.clxy.network:$port" else "edge.clxy.network"
        }
        out = GUARDIAN_HOST_RE.replace(out) { m ->
            val port = m.groupValues.getOrNull(1).orEmpty()
            if (port.isNotEmpty()) "edge.clxy.network:$port" else "edge.clxy.network"
        }
        out = MOZILLA_VPN_RE.replace(out, "CLXY")
        out = FIREFOX_ACCOUNT_RE.replace(out, "CLXY Account")
        out = MOZILLA_WORD_RE.replace(out, "CLXY")
        out = FIREFOX_WORD_RE.replace(out, "CLXY")
        out = GUARDIAN_WORD_RE.replace(out, "CLXY")
        out = FXA_WORD_RE.replace(out, "CLXY")
        return out
    }

    fun describeFailure(code: String, fallback: String): String = when (code) {
        FailureCode.NO_NETWORK -> "No internet connection detected on this device."
        FailureCode.UNREACHABLE -> "Could not reach CLXY servers. Try DNS or a Personal Relay in Profile."
        FailureCode.UPSTREAM_PROXY -> "Could not reach your configured upstream SOCKS5 proxy."
        FailureCode.INSIDE_VPN -> "Device internet is already routed through another VPN exit."
        FailureCode.REFUSED -> "CLXY server refused the connection."
        FailureCode.LOCATION_REFUSED -> "Selected country refused this account. Switch to Recommended."
        FailureCode.SIGN_IN -> "Account session expired. Please sign in again."
        FailureCode.QUOTA -> "Account monthly bandwidth quota reached."
        FailureCode.NOT_ENTITLED -> "Account is not entitled to CLXY service."
        FailureCode.REGION -> "CLXY refused the request from this network region (HTTP 451)."
        FailureCode.SERVER -> "CLXY server returned a temporary error."
        FailureCode.NO_ACCOUNT -> "No usable account is enabled or ready."
        FailureCode.TEST_SITE -> "Tunnel connected, but the connection check page did not answer."
        else -> brandAsClxy(fallback).ifEmpty { "Connection failed." }
    }

    fun describeState(state: EngineState, totalAccounts: Int): String = when (state.code) {
        StateCode.WAITING_FOR_NETWORK -> "Waiting for internet connection…"
        StateCode.NO_ACCOUNTS -> if (totalAccounts == 0) {
            "Add a CLXY Account in Profile to connect"
        } else {
            "Enable at least one account in Profile"
        }
        StateCode.NO_USABLE_ACCOUNT -> "All accounts are exhausted or need sign-in"
        StateCode.PROXY_PORT_IN_USE -> "Local proxy port ${CoreLocator.portOf(state.address) ?: state.address} is in use"
        StateCode.START_FAILED -> brandAsClxy(state.message).ifEmpty { "Could not start local proxy listener" }
        else -> brandAsClxy(state.message)
    }

    fun describeSignInError(code: String, fallback: String): String = when (code) {
        SignInError.NO_ACCOUNT -> "No CLXY Account exists for this email."
        SignInError.WRONG_PASSWORD -> "Incorrect email or password. Please check your credentials."
        SignInError.NO_PASSWORD -> "This account has no password set. Set a password first or use Browser Sign-In."
        SignInError.TOO_MANY_ATTEMPTS -> "Too many sign-in attempts. Please wait before trying again."
        SignInError.UNBLOCK_CODE_REJECTED -> "Invalid authorization code. Please check the code and try again."
        SignInError.WRONG_CODE -> "Incorrect verification code. Please check your email and try again."
        SignInError.WRONG_TOTP -> "Incorrect authenticator (TOTP) code. Please try again."
        SignInError.TOO_MANY_CODES -> "Too many verification codes requested. Wait a moment."
        SignInError.TIMED_OUT -> "Sign-in timed out. Please try again."
        SignInError.NETWORK -> "Network error reaching CLXY authentication servers."
        SignInError.TOKEN_BLOCKED, SignInError.BLOCKED -> "Sign-in blocked on this network. Configure a Personal Relay or Upstream Proxy first."
        else -> brandAsClxy(fallback).ifEmpty { "Sign-in failed. Please verify your credentials and try again." }
    }
}
