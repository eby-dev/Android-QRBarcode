package com.ahmadabuhasan.qrbarcode.model

import android.net.Uri

sealed class ScanContent {
    abstract val raw: String

    data class Url(override val raw: String) : ScanContent()

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String,
        val security: String,
        val hidden: Boolean,
    ) : ScanContent()

    data class Phone(override val raw: String, val number: String) : ScanContent()

    data class Sms(override val raw: String, val number: String, val body: String) : ScanContent()

    data class Email(
        override val raw: String,
        val to: String,
        val subject: String,
        val body: String,
    ) : ScanContent()

    data class Geo(
        override val raw: String,
        val lat: String,
        val lng: String,
        val query: String?,
    ) : ScanContent()

    data class VCard(
        override val raw: String,
        val name: String?,
        val phone: String?,
        val email: String?,
    ) : ScanContent()

    // Calendar event dates are kept as raw strings — VEVENT DTSTART/DTEND are
    // typically ISO-basic (yyyyMMdd'T'HHmmss'Z'), and the Calendar intent
    // accepts long millis, so we parse-to-millis at intent time in the sheet.
    data class CalendarEvent(
        override val raw: String,
        val title: String?,
        val location: String?,
        val description: String?,
        val start: String?,
        val end: String?,
    ) : ScanContent()

    data class Text(override val raw: String) : ScanContent()
}

object ScanContentParser {

    fun parse(text: String): ScanContent = when {
        text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) ->
            ScanContent.Url(text)
        text.startsWith("WIFI:", ignoreCase = true) -> parseWifi(text) ?: ScanContent.Text(text)
        text.startsWith("tel:", ignoreCase = true) -> parseTel(text) ?: ScanContent.Text(text)
        text.startsWith("SMSTO:", ignoreCase = true) || text.startsWith("sms:", ignoreCase = true) ->
            parseSms(text) ?: ScanContent.Text(text)
        text.startsWith("mailto:", ignoreCase = true) -> parseMailto(text) ?: ScanContent.Text(text)
        text.startsWith("MATMSG:", ignoreCase = true) -> parseMatmsg(text) ?: ScanContent.Text(text)
        text.startsWith("geo:", ignoreCase = true) -> parseGeo(text) ?: ScanContent.Text(text)
        text.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVCard(text)
        text.startsWith("BEGIN:VEVENT", ignoreCase = true) ||
            (text.startsWith("BEGIN:VCALENDAR", ignoreCase = true) && text.contains("BEGIN:VEVENT", ignoreCase = true)) ->
            parseCalendarEvent(text)
        else -> ScanContent.Text(text)
    }

    // WIFI:S:<ssid>;T:<WPA|WEP|nopass>;P:<pass>;H:<true|false>;;
    // Values can contain \; and \: as escaped delimiters.
    private fun parseWifi(raw: String): ScanContent.Wifi? {
        val body = raw.substring("WIFI:".length)
        val fields = mutableMapOf<String, String>()
        var i = 0
        while (i < body.length) {
            val colon = body.indexOf(':', i)
            if (colon < 0) break
            val key = body.substring(i, colon).uppercase()
            val value = StringBuilder()
            var j = colon + 1
            while (j < body.length) {
                val c = body[j]
                if (c == '\\' && j + 1 < body.length) {
                    value.append(body[j + 1]); j += 2
                } else if (c == ';') {
                    j++; break
                } else {
                    value.append(c); j++
                }
            }
            fields[key] = value.toString()
            i = j
        }
        val ssid = fields["S"]?.takeIf { it.isNotBlank() } ?: return null
        return ScanContent.Wifi(
            raw = raw,
            ssid = ssid,
            password = fields["P"].orEmpty(),
            security = fields["T"].orEmpty(),
            hidden = fields["H"]?.equals("true", ignoreCase = true) == true,
        )
    }

    private fun parseTel(raw: String): ScanContent.Phone? {
        val number = raw.substring("tel:".length).trim()
        return if (number.isBlank()) null else ScanContent.Phone(raw, number)
    }

    // SMSTO:<number>:<body>  OR  sms:<number>?body=<body>
    private fun parseSms(raw: String): ScanContent.Sms? {
        return if (raw.startsWith("SMSTO:", ignoreCase = true)) {
            val body = raw.substring("SMSTO:".length)
            val colon = body.indexOf(':')
            val number = (if (colon >= 0) body.substring(0, colon) else body).trim()
            val message = if (colon >= 0) body.substring(colon + 1) else ""
            if (number.isBlank()) null else ScanContent.Sms(raw, number, message)
        } else {
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
            val number = uri.schemeSpecificPart?.substringBefore('?')?.trim().orEmpty()
            if (number.isBlank()) null
            else ScanContent.Sms(raw, number, uri.getQueryParameter("body").orEmpty())
        }
    }

    private fun parseMailto(raw: String): ScanContent.Email? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val to = uri.schemeSpecificPart?.substringBefore('?').orEmpty().trim()
        if (to.isBlank()) return null
        return ScanContent.Email(
            raw = raw,
            to = to,
            subject = uri.getQueryParameter("subject").orEmpty(),
            body = uri.getQueryParameter("body").orEmpty(),
        )
    }

    // MATMSG:TO:<addr>;SUB:<subject>;BODY:<body>;;
    private fun parseMatmsg(raw: String): ScanContent.Email? {
        val body = raw.substring("MATMSG:".length)
        val fields = parseSemicolonKV(body)
        val to = fields["TO"]?.takeIf { it.isNotBlank() } ?: return null
        return ScanContent.Email(
            raw = raw,
            to = to,
            subject = fields["SUB"].orEmpty(),
            body = fields["BODY"].orEmpty(),
        )
    }

    // geo:<lat>,<lng>?q=<query>
    private fun parseGeo(raw: String): ScanContent.Geo? {
        val afterScheme = raw.substring("geo:".length)
        val coords = afterScheme.substringBefore('?')
        val parts = coords.split(',', limit = 2)
        if (parts.size < 2) return null
        val query = if ('?' in afterScheme) {
            runCatching { Uri.parse(raw).getQueryParameter("q") }.getOrNull()
        } else null
        return ScanContent.Geo(raw, parts[0].trim(), parts[1].trim(), query)
    }

    private fun parseCalendarEvent(raw: String): ScanContent.CalendarEvent {
        fun field(key: String): String? = raw.lineSequence().firstNotNullOfOrNull { line ->
            val trimmed = line.trimEnd()
            // vEvent lines can be like "SUMMARY:foo" or "DTSTART;TZID=UTC:20260901T090000Z"
            val marker = trimmed.substringBefore(':', "")
            val name = marker.substringBefore(';').uppercase()
            if (name == key) trimmed.substringAfter(':', "").takeIf { it.isNotBlank() } else null
        }
        return ScanContent.CalendarEvent(
            raw = raw,
            title = field("SUMMARY"),
            location = field("LOCATION"),
            description = field("DESCRIPTION"),
            start = field("DTSTART"),
            end = field("DTEND"),
        )
    }

    private fun parseVCard(raw: String): ScanContent.VCard {
        fun find(prefixes: List<String>): String? = raw.lineSequence().firstNotNullOfOrNull { line ->
            val trimmed = line.trimEnd()
            prefixes.firstNotNullOfOrNull { p ->
                if (trimmed.startsWith(p, ignoreCase = true))
                    trimmed.substringAfter(':', "").takeIf { it.isNotBlank() }
                else null
            }
        }
        return ScanContent.VCard(
            raw = raw,
            name = find(listOf("FN:", "N:")),
            phone = find(listOf("TEL:", "TEL;")),
            email = find(listOf("EMAIL:", "EMAIL;")),
        )
    }

    private fun parseSemicolonKV(body: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        var i = 0
        while (i < body.length) {
            val colon = body.indexOf(':', i)
            if (colon < 0) break
            val key = body.substring(i, colon).uppercase()
            val value = StringBuilder()
            var j = colon + 1
            while (j < body.length) {
                val c = body[j]
                if (c == '\\' && j + 1 < body.length) {
                    value.append(body[j + 1]); j += 2
                } else if (c == ';') {
                    j++; break
                } else {
                    value.append(c); j++
                }
            }
            fields[key] = value.toString()
            i = j
        }
        return fields
    }
}
