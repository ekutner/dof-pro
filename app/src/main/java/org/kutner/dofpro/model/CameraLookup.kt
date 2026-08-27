package org.kutner.dofpro.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** A camera the user might have meant, and where to read its specifications from. */
data class CameraMatch(val title: String, val summary: String)

/** What an article yielded. Any of it may be missing; none of it is ever guessed. */
data class CameraSpecs(
    val name: String,
    val widthMm: Double?,
    val heightMm: Double?,
    val widthPx: Int?,
    val heightPx: Int?,
) {
    val hasFrame: Boolean get() = widthMm != null && heightMm != null
    val hasPixels: Boolean get() = widthPx != null && heightPx != null
}

/**
 * Looks a camera up on Wikipedia, so its frame size and resolution need not be copied off
 * a spec sheet by hand.
 *
 * Wikipedia rather than a table shipped inside the app, because a shipped table needs a new
 * build of the app every time a camera is released. Wikipedia rather than a search engine,
 * because a search engine returns links to pages in a hundred different shapes, while this
 * returns one article whose infobox is the same shape every time — under a licence that
 * invites reuse, through an interface meant to be called.
 */
object CameraLookup {

    private const val API = "https://en.wikipedia.org/w/api.php"

    /**
     * Wikimedia asks clients to say who they are, and is less generous with those that do
     * not. This is that introduction.
     */
    private const val AGENT = "DoF-Pro/1.0 (Android depth of field calculator)"

    /**
     * Candidate articles for what was typed, best first.
     *
     * Full text search, not the prefix-matching "opensearch" endpoint, and the difference
     * is not subtle: asked for "sony a7r v" the prefix search offers *Sony α7* — a
     * different camera, with a different sensor — while this one puts *Sony α7R V* first.
     * It also copes with how people actually type: "lumix s5ii" finds an article titled
     * *Panasonic Lumix DC-S5M2*, which nobody would have guessed at.
     *
     * Several are returned rather than one because the first hit is not always right, and
     * a calculator that quietly adopts the wrong camera's sensor is worse than one that
     * asks — every number on the screen would be wrong and nothing would say so.
     */
    suspend fun search(query: String, limit: Int = 8): List<CameraMatch> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val url = API + "?action=query&list=search&srsearch=" + enc(q) +
                "&srlimit=" + limit + "&format=json&formatversion=2"
            val hits = fetch(url)?.optJSONObject("query")?.optJSONArray("search")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until hits.length()) {
                    val o = hits.optJSONObject(i) ?: continue
                    val title = o.optString("title").takeIf { it.isNotBlank() } ?: continue
                    add(CameraMatch(title, stripMarkup(o.optString("snippet"))))
                }
            }
        }

    /** Reads one article's specifications, or null if it could not be fetched. */
    suspend fun specs(title: String): CameraSpecs? = withContext(Dispatchers.IO) {
        val url = API + "?action=parse&page=" + enc(title) +
            "&prop=wikitext&format=json&formatversion=2"
        val parse = fetch(url)?.optJSONObject("parse") ?: return@withContext null
        val text = parse.optString("wikitext").takeIf { it.isNotBlank() }
            ?: parse.optJSONObject("wikitext")?.optString("*")
            ?: return@withContext null
        parse(title, text)
    }

    // ---- Reading an article ----------------------------------------------------------

    private val MM = Regex(
        "(\\d{1,3}(?:\\.\\d+)?)\\s*(?:mm)?\\s*[x×]\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*mm",
        RegexOption.IGNORE_CASE,
    )
    private val PX = Regex("(\\d[\\d,]{2,5})\\s*[x×]\\s*(\\d[\\d,]{2,5})")

    /**
     * Pulls the frame size and the resolution out of an article's infobox.
     *
     * **Only ever explicit millimetres, from this camera's own article.** A sensor format
     * is a family, not a measurement, and the families are not tidy: Canon's APS-C is
     * 22.3 x 14.9 mm, Sony's 23.3 x 15.5, Nikon's 23.5 x 15.7, Fujifilm's 23.8 x 15.6.
     * Reading "APS-C" and filling in a nominal figure would be wrong for most cameras that
     * say it, and six per cent wrong for Canon — which moves the circle of confusion, and
     * every distance on the screen with it. Full frame is no better behaved: 35.6, 35.7,
     * 35.9 and 36 mm across four makers. So a format name yields nothing here, and the
     * user is asked instead.
     *
     * The infobox is read rather than the body for the same reason a spec table beats
     * prose: an article's body mentions all sorts of numbers. Scanning it turned up
     * 4096 x 2160 for an E-M1 Mark III, which is a video mode rather than the sensor.
     */
    internal fun parse(title: String, wiki: String): CameraSpecs {
        val size = field(wiki, "sensor_size", "sensorsize", "sensor size")
        val sensor = field(wiki, "sensor")
        val res = field(wiki, "res", "resolution")

        val mm = MM.find(size) ?: MM.find(sensor)
        val px = PX.find(res)

        var widthMm = mm?.groupValues?.get(1)?.toDoubleOrNull()
        var heightMm = mm?.groupValues?.get(2)?.toDoubleOrNull()
        var widthPx = px?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        var heightPx = px?.groupValues?.get(2)?.replace(",", "")?.toIntOrNull()

        if (widthMm != null && heightMm != null && (widthMm <= 0.0 || heightMm <= 0.0)) {
            widthMm = null
            heightMm = null
        }
        // Anything this small is a thumbnail or a screen, not a sensor's pixel count.
        if (widthPx != null && heightPx != null && (widthPx < 640 || heightPx < 480)) {
            widthPx = null
            heightPx = null
        }

        // Pixels are square, so the frame's shape and the image's shape are one shape. When
        // the two disagree, one reading is not what it claims to be — and the pixels are
        // the likelier culprit, being a video mode or a crop. Dropping them keeps the frame
        // size, which is the harder of the two to look up by hand.
        if (widthMm != null && heightMm != null && widthPx != null && heightPx != null) {
            val frame = widthMm / heightMm
            val image = widthPx.toDouble() / heightPx.toDouble()
            if (abs(frame - image) / image > ASPECT_TOLERANCE) {
                widthPx = null
                heightPx = null
            }
        }
        return CameraSpecs(title, widthMm, heightMm, widthPx, heightPx)
    }

    /**
     * How far the frame's shape and the image's shape may differ before the resolution is
     * disbelieved. Loose enough for the rounding in a published figure — 23.8 x 15.6 mm is
     * quoted for a 3:2 sensor — and tight enough to catch a 16:9 video mode.
     */
    private const val ASPECT_TOLERANCE = 0.06

    private fun field(wiki: String, vararg keys: String): String {
        for (key in keys) {
            val re = Regex(
                "^\\s*\\|\\s*" + Regex.escape(key) + "\\s*=\\s*(.+)$",
                RegexOption.MULTILINE,
            )
            val v = re.find(wiki)?.groupValues?.get(1)?.let { stripMarkup(it) }
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }

    private fun stripMarkup(s: String): String = s
        // A template's arguments are separated by pipes, so {{convert|36|x|24|mm}} states
        // the same fact as "36 x 24 mm" but hides it behind punctuation. Opening the
        // template out has to happen before the numbers are looked for.
        .replace(Regex("\\{\\{[^{}]*\\}\\}")) { it.value.replace('|', ' ') }
        .replace(Regex("\\[\\[(?:[^\\]|]*\\|)?"), "")
        .replace("]]", "")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("{{", " ")
        .replace("}}", " ")
        .trim()

    // ---- Plumbing --------------------------------------------------------------------

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun fetch(url: String): JSONObject? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        // Offline, blocked, timed out, or something unexpected came back. The caller shows
        // the user a plain "could not reach Wikipedia" rather than any of that.
        null
    }
}
