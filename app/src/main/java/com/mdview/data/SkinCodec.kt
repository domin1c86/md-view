package com.mdview.data

import androidx.compose.ui.graphics.Color
import com.mdview.ui.theme.BuiltInSkins
import com.mdview.ui.theme.Skin
import com.mdview.ui.theme.SkinColors
import com.mdview.ui.theme.SkinShape
import com.mdview.ui.theme.SkinType

/** Why a skin file was refused outright, as opposed to having one token ignored. */
class InvalidSkinException(val reason: Reason) : Exception("Invalid skin: $reason") {
    enum class Reason {
        /** Not JSON at all, or JSON this parser does not accept. */
        Malformed,

        /** Over a size, depth, node or string limit. */
        TooLarge,

        /** Missing, or outside `[a-z0-9][a-z0-9-]{0,63}`. */
        BadId,

        /** Missing or blank `name`. */
        BadName,

        /** `base` names a skin that does not ship with the app. */
        UnknownBase,

        /** The id belongs to a built-in skin, which cannot be shadowed. */
        ReservedId,

        /** The imported-skin cap is already reached. */
        TooMany,
    }
}

/**
 * Reads and writes skin files.
 *
 * Pure Kotlin with no Android types, so it is unit-testable on the JVM exactly like
 * [LibraryCodec] and `DocumentCodec`. That rules out `org.json`, whose stubs return null
 * under `isReturnDefaultValues = true` -- hence the small hand-rolled parser below.
 *
 * The format is a subset of JSON: objects, strings, numbers, booleans, null and arrays.
 * Arrays are parsed and then discarded rather than rejected, so a file written against
 * some later schema still loads instead of failing wholesale.
 *
 * Every token is optional. Whatever a file omits -- or gets wrong -- is inherited from
 * the skin named by `base`, which is the bargain [SettingsStore] already strikes: a bad
 * value should cost the user that one value, not the whole file.
 */
internal object SkinCodec {

    /** Skin files are untrusted input, so every dimension an author controls is bounded. */
    const val MAX_CHARS = 64 * 1024
    private const val MAX_DEPTH = 8
    private const val MAX_NODES = 1024
    private const val MAX_STRING = 256
    private const val MAX_NAME = 48

    private const val BOM = '\uFEFF'
    private val ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")

    private val COLOR_KEYS = listOf(
        "canvas", "surface", "surfaceRaised", "surfaceSunken",
        "accent", "onAccent", "accentSubtle",
        "textPrimary", "textSecondary", "textMuted",
        "border", "divider",
        "link", "linkPressed",
        "code", "codeBackground",
        "quoteBar", "quoteText",
        "tableHeader", "tableBorder",
        "danger", "success", "selection",
    )

    fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)

    fun decode(text: String): Skin {
        if (text.length > MAX_CHARS) throw InvalidSkinException(InvalidSkinException.Reason.TooLarge)

        // Windows editors happily save JSON with a byte order mark. Rejecting those as
        // malformed would be technically defensible and practically useless.
        val root = JsonParser(text.trimStart(BOM)).parseDocument()

        val id = root.string("id")?.lowercase()
            ?: throw InvalidSkinException(InvalidSkinException.Reason.BadId)
        if (!isValidId(id)) throw InvalidSkinException(InvalidSkinException.Reason.BadId)

        val name = root.string("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw InvalidSkinException(InvalidSkinException.Reason.BadName)

        val declaredDark = root.boolean("dark")
        val base = when (val baseId = root.string("base")) {
            null -> BuiltInSkins.default(declaredDark ?: false)
            else -> BuiltInSkins.byId(baseId)
                ?: throw InvalidSkinException(InvalidSkinException.Reason.UnknownBase)
        }

        val colors = root.obj("colors").orEmpty()
        val shape = root.obj("shape").orEmpty()
        val type = root.obj("type").orEmpty()

        // An unparseable colour costs that one token; its siblings still apply.
        fun color(key: String, fallback: Color): Color =
            colors.string(key)?.let(::parseColor) ?: fallback

        val b = base.colors
        return Skin(
            id = id,
            name = name.take(MAX_NAME),
            author = root.string("author")?.trim()?.takeIf { it.isNotEmpty() },
            dark = declaredDark ?: base.dark,
            colors = SkinColors(
                canvas = color("canvas", b.canvas),
                surface = color("surface", b.surface),
                surfaceRaised = color("surfaceRaised", b.surfaceRaised),
                surfaceSunken = color("surfaceSunken", b.surfaceSunken),
                accent = color("accent", b.accent),
                onAccent = color("onAccent", b.onAccent),
                accentSubtle = color("accentSubtle", b.accentSubtle),
                textPrimary = color("textPrimary", b.textPrimary),
                textSecondary = color("textSecondary", b.textSecondary),
                textMuted = color("textMuted", b.textMuted),
                border = color("border", b.border),
                divider = color("divider", b.divider),
                link = color("link", b.link),
                linkPressed = color("linkPressed", b.linkPressed),
                code = color("code", b.code),
                codeBackground = color("codeBackground", b.codeBackground),
                quoteBar = color("quoteBar", b.quoteBar),
                quoteText = color("quoteText", b.quoteText),
                tableHeader = color("tableHeader", b.tableHeader),
                tableBorder = color("tableBorder", b.tableBorder),
                danger = color("danger", b.danger),
                success = color("success", b.success),
                selection = color("selection", b.selection),
            ),
            shape = SkinShape(
                small = shape.int("small", base.shape.small).coerceIn(0, MAX_CORNER),
                medium = shape.int("medium", base.shape.medium).coerceIn(0, MAX_CORNER),
                large = shape.int("large", base.shape.large).coerceIn(0, MAX_CORNER),
            ),
            type = SkinType(
                bodyScale = type.float("bodyScale", base.type.bodyScale).coerceIn(0.85f, 1.3f),
                monoScale = type.float("monoScale", base.type.monoScale).coerceIn(0.7f, 1.2f),
                // Snapped to a hundred so an odd value cannot produce a weight no font has.
                headingWeight = type.int("headingWeight", base.type.headingWeight)
                    .coerceIn(100, 900)
                    .let { (it / 100) * 100 },
                tracking = type.float("tracking", base.type.tracking).coerceIn(-0.05f, 0.1f),
            ),
        )
    }

    /**
     * Writes [skin] out in full -- no `base`, every token explicit.
     *
     * Imported skins are re-encoded before being stored, so what lands on disk is always
     * complete. A stored file that still said `"base": "midnight"` would silently change
     * appearance if a later release retuned Midnight.
     */
    fun encode(skin: Skin): String {
        val values = skin.colors.asMap()
        return buildString {
            appendLine("{")
            appendLine("  \"schema\": 1,")
            appendLine("  \"id\": ${quote(skin.id)},")
            appendLine("  \"name\": ${quote(skin.name)},")
            skin.author?.let { appendLine("  \"author\": ${quote(it)},") }
            appendLine("  \"dark\": ${skin.dark},")
            appendLine("  \"colors\": {")
            COLOR_KEYS.forEachIndexed { index, key ->
                val comma = if (index == COLOR_KEYS.lastIndex) "" else ","
                appendLine("    ${quote(key)}: ${quote(formatColor(values.getValue(key)))}$comma")
            }
            appendLine("  },")
            appendLine("  \"shape\": {")
            appendLine("    \"small\": ${skin.shape.small},")
            appendLine("    \"medium\": ${skin.shape.medium},")
            appendLine("    \"large\": ${skin.shape.large}")
            appendLine("  },")
            appendLine("  \"type\": {")
            appendLine("    \"bodyScale\": ${skin.type.bodyScale},")
            appendLine("    \"monoScale\": ${skin.type.monoScale},")
            appendLine("    \"headingWeight\": ${skin.type.headingWeight},")
            appendLine("    \"tracking\": ${skin.type.tracking}")
            appendLine("  }")
            appendLine("}")
        }
    }

    /** Accepts `#RGB`, `#RRGGBB` and `#AARRGGBB`, in either case. Null if unparseable. */
    fun parseColor(value: String): Color? {
        val hex = value.trim().removePrefix("#")
        if (hex.isEmpty() || !hex.all { it.isHexDigit() }) return null

        val argb = when (hex.length) {
            3 -> buildString { append("FF"); hex.forEach { append(it).append(it) } }
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        return Color(argb.toLong(16))
    }

    /** Renders back to `#RRGGBB`, or `#AARRGGBB` when the colour is not fully opaque. */
    fun formatColor(color: Color): String {
        fun channel(value: Float): Int = ((value * 255f) + 0.5f).toInt().coerceIn(0, 255)

        val alpha = channel(color.alpha)
        val rgb = hex(channel(color.red)) + hex(channel(color.green)) + hex(channel(color.blue))
        return if (alpha == 255) "#$rgb" else "#${hex(alpha)}$rgb"
    }

    // String.format is locale-sensitive for digits; this never is.
    private fun hex(value: Int): String {
        val digits = "0123456789ABCDEF"
        return "${digits[value shr 4 and 0xF]}${digits[value and 0xF]}"
    }

    private fun SkinColors.asMap(): Map<String, Color> = mapOf(
        "canvas" to canvas, "surface" to surface,
        "surfaceRaised" to surfaceRaised, "surfaceSunken" to surfaceSunken,
        "accent" to accent, "onAccent" to onAccent, "accentSubtle" to accentSubtle,
        "textPrimary" to textPrimary, "textSecondary" to textSecondary, "textMuted" to textMuted,
        "border" to border, "divider" to divider,
        "link" to link, "linkPressed" to linkPressed,
        "code" to code, "codeBackground" to codeBackground,
        "quoteBar" to quoteBar, "quoteText" to quoteText,
        "tableHeader" to tableHeader, "tableBorder" to tableBorder,
        "danger" to danger, "success" to success, "selection" to selection,
    )

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char < ' ' -> append("\\u00").append(hex(char.code))
                else -> append(char)
            }
        }
        append('"')
    }

    private const val MAX_CORNER = 48

    // -- typed lookups over the parsed tree ------------------------------------------

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.boolean(key: String): Boolean? = this[key] as? Boolean

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(key: String): Map<String, Any?>? =
        this[key] as? Map<String, Any?>

    private fun Map<String, Any?>.int(key: String, fallback: Int): Int =
        (this[key] as? Double)?.toInt() ?: fallback

    private fun Map<String, Any?>.float(key: String, fallback: Float): Float =
        (this[key] as? Double)?.toFloat() ?: fallback

    /**
     * A recursive-descent reader for the JSON subset above.
     *
     * Bounded on every axis the file controls -- depth, node count and string length --
     * because this parses something the user was handed by somebody else.
     */
    private class JsonParser(private val text: String) {
        private var index = 0
        private var depth = 0
        private var nodes = 0

        fun parseDocument(): Map<String, Any?> {
            skipWhitespace()
            val value = readValue()
            skipWhitespace()
            if (index < text.length) fail()
            @Suppress("UNCHECKED_CAST")
            return value as? Map<String, Any?> ?: fail()
        }

        private fun readValue(): Any? {
            if (++nodes > MAX_NODES) tooLarge()
            return when (peek()) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> = nested {
            expect('{')
            val result = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return@nested result
            }
            var reading = true
            while (reading) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = readValue()
                skipWhitespace()
                when (next()) {
                    ',' -> Unit
                    '}' -> reading = false
                    else -> fail()
                }
            }
            result
        }

        /** Parsed so a later schema's arrays do not break today's build, then dropped. */
        private fun readArray(): List<Any?> = nested {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return@nested result
            }
            var reading = true
            while (reading) {
                skipWhitespace()
                result += readValue()
                skipWhitespace()
                when (next()) {
                    ',' -> Unit
                    ']' -> reading = false
                    else -> fail()
                }
            }
            result
        }

        private fun <T> nested(block: () -> T): T {
            if (++depth > MAX_DEPTH) tooLarge()
            try {
                return block()
            } finally {
                depth--
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (index >= text.length) fail()
                when (val char = text[index++]) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(readEscape())
                    // A raw control character is invalid JSON, and in practice means the
                    // file is not JSON at all -- something binary picked by mistake.
                    in '\u0000'..'\u001F' -> fail()
                    else -> builder.append(char)
                }
                if (builder.length > MAX_STRING) tooLarge()
            }
        }

        private fun readEscape(): Char {
            if (index >= text.length) fail()
            return when (text[index++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) fail()
                    val code = text.substring(index, index + 4)
                    if (!code.all { it.isHexDigit() }) fail()
                    index += 4
                    code.toInt(16).toChar()
                }

                else -> fail()
            }
        }

        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (index < text.length && (text[index].isDigit() || text[index] in ".eE+-")) {
                index++
            }
            // Deliberately not a bare toDoubleOrNull: that accepts "NaN", "Infinity" and
            // a leading "+", none of which are JSON.
            val literal = text.substring(start, index)
            if (literal.isEmpty()) fail()
            return literal.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fail()
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, index)) fail()
            index += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index] in WHITESPACE) index++
        }

        private fun peek(): Char = if (index < text.length) text[index] else fail()

        private fun next(): Char = if (index < text.length) text[index++] else fail()

        private fun expect(char: Char) {
            if (next() != char) fail()
        }

        private fun fail(): Nothing =
            throw InvalidSkinException(InvalidSkinException.Reason.Malformed)

        private fun tooLarge(): Nothing =
            throw InvalidSkinException(InvalidSkinException.Reason.TooLarge)

        private companion object {
            const val WHITESPACE = " \t\n\r"
        }
    }
}
