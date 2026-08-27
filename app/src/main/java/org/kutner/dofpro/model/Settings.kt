package org.kutner.dofpro.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The persisted state. DoF for Windows keeps this in dof.txt; here it lives in shared
 * preferences, written whenever the app is backgrounded.
 */
data class Settings(
    val cameras: List<Camera> = Camera.defaults(),
    val cameraIndex: Int = 0,
    val lenses: List<Lens> = Lens.defaults(),
    val targets: List<ViewingTarget> = ViewingTarget.defaults(),
    val targetIndex: Int = 0,
    val lensIndex: Int = 0,
    val units: UnitSystem = UnitSystem.METRIC,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val stacking: Boolean = false,
    /** Fraction of each frame's depth of field the next one doubles back over. */
    val stackOverlap: Double = DofState.DEFAULT_STACK_OVERLAP,
    val apertureStep: ApertureStep = ApertureStep.THIRD,
    val teleconverter: Teleconverter = Teleconverter.NONE,
    val focalLength: Double = 50.0,
    val fStop: Double = 4.0,
    val subject: Double = 4.0 * 304.8,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cameras", JSONArray().also { arr -> cameras.forEach { arr.put(it.toJson()) } })
        put("cameraIndex", cameraIndex)
        put("lenses", JSONArray().also { arr -> lenses.forEach { arr.put(it.toJson()) } })
        put("targets", JSONArray().also { arr -> targets.forEach { arr.put(it.toJson()) } })
        put("targetIndex", targetIndex)
        put("lensIndex", lensIndex)
        put("units", units.name)
        put("theme", theme.name)
        put("stacking", stacking)
        put("stackOverlap", stackOverlap)
        put("apertureStep", apertureStep.name)
        put("teleconverter", teleconverter.name)
        put("focalLength", focalLength)
        put("fStop", fStop)
        put("subject", subject)
    }

    companion object {
        private const val PREFS = "dof"
        private const val KEY = "settings"

        fun load(context: Context): Settings {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return Settings()
            return runCatching { fromJson(JSONObject(raw)) }.getOrElse { Settings() }
        }

        fun save(context: Context, settings: Settings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, settings.toJson().toString())
                .apply()
        }

        private fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            val camArr = o.optJSONArray("cameras")
            val cams = buildList {
                if (camArr != null) {
                    for (i in 0 until camArr.length()) add(cameraFromJson(camArr.getJSONObject(i)))
                }
            }.ifEmpty { Camera.defaults() }
            val lensArr = o.optJSONArray("lenses")
            val lenses = buildList {
                if (lensArr != null) {
                    for (i in 0 until lensArr.length()) add(lensFromJson(lensArr.getJSONObject(i)))
                }
            }.ifEmpty { Lens.defaults() }

            val targetArr = o.optJSONArray("targets")
            val targets = buildList {
                if (targetArr != null) {
                    for (i in 0 until targetArr.length()) {
                        add(targetFromJson(targetArr.getJSONObject(i)))
                    }
                }
            }.ifEmpty { ViewingTarget.defaults() }

            return Settings(
                targets = targets,
                targetIndex = if (targetArr != null) {
                    o.optInt("targetIndex", 0).coerceIn(0, targets.lastIndex)
                } else {
                    startingTarget(o, targets)
                },
                cameras = cams,
                cameraIndex = o.optInt("cameraIndex", 0).coerceIn(0, cams.lastIndex),
                lenses = lenses,
                lensIndex = o.optInt("lensIndex", 0).coerceIn(0, lenses.lastIndex),
                units = UnitSystem.parse(o.optString("units"), d.units),
                theme = enumOr(o.optString("theme"), d.theme),
                stacking = o.optBoolean("stacking", d.stacking),
                stackOverlap = o.optDouble("stackOverlap", d.stackOverlap)
                    .coerceIn(0.0, DofState.MAX_STACK_OVERLAP),
                apertureStep = enumOr(o.optString("apertureStep"), d.apertureStep),
                teleconverter = enumOr(o.optString("teleconverter"), d.teleconverter),
                focalLength = o.optDouble("focalLength", d.focalLength),
                fStop = o.optDouble("fStop", d.fStop),
                subject = o.optDouble("subject", d.subject),
            )
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback


        private fun lensFromJson(o: JSONObject): Lens {
            val d = Lens()
            return Lens(
                name = o.optString("name", d.name),
                minFocal = o.optDouble("minFocal", d.minFocal),
                maxFocal = o.optDouble("maxFocal", d.maxFocal),
                minFStop = o.optDouble("minFStop", d.minFStop),
                maxFStop = o.optDouble("maxFStop", d.maxFStop),
            )
        }

        private fun targetFromJson(o: JSONObject): ViewingTarget {
            val d = ViewingTarget()
            return ViewingTarget(
                name = o.optString("name", d.name),
                kind = enumOr(o.optString("kind"), d.kind),
                widthMm = o.optDouble("widthMm", d.widthMm),
                viewingDistanceMm = o.optDouble("viewingDistanceMm", d.viewingDistanceMm),
                pixelsAcross = o.optInt("pixelsAcross", d.pixelsAcross),
                visualResolution = o.optDouble("visualResolution", d.visualResolution),
                allowableBlur = o.optDouble("allowableBlur", d.allowableBlur),
                customCoc = o.optDouble("customCoc", d.customCoc),
            )
        }

        /**
         * Which target a setting written before targets existed should start on.
         *
         * How a picture was to be judged used to live on the camera, as a "sharp image" or
         * "sharp print" method. The camera in use says which it was, and that maps onto a
         * target of the same kind — so an upgrade lands the user where they already were
         * rather than on whatever happens to be first in the list.
         */
        private fun startingTarget(o: JSONObject, targets: List<ViewingTarget>): Int {
            val method = o.optJSONArray("cameras")
                ?.optJSONObject(o.optInt("cameraIndex", 0))
                ?.optString("method")
            val want = when (method) {
                "SHARP_IMAGE" -> TargetKind.PIXELS
                "CUSTOM" -> TargetKind.CUSTOM
                else -> TargetKind.PRINT
            }
            return targets.indexOfFirst { it.kind == want }.coerceAtLeast(0)
        }

        private fun cameraFromJson(o: JSONObject): Camera {
            val d = Camera()
            return Camera(
                name = o.optString("name", d.name),
                type = enumOr(o.optString("type"), d.type),
                focalMode = enumOr(o.optString("focalMode"), d.focalMode),
                frameWidthMm = o.optDouble("frameWidthMm", d.frameWidthMm),
                frameHeightMm = o.optDouble("frameHeightMm", d.frameHeightMm),
                frameWidthPx = o.optInt("frameWidthPx", d.frameWidthPx),
                frameHeightPx = o.optInt("frameHeightPx", d.frameHeightPx),
                filmResolution = o.optDouble("filmResolution", d.filmResolution),
                wavelengthNm = o.optDouble("wavelengthNm", d.wavelengthNm),
            )
        }
    }
}

private fun Lens.toJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("minFocal", minFocal)
    put("maxFocal", maxFocal)
    put("minFStop", minFStop)
    put("maxFStop", maxFStop)
}

private fun Camera.toJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("type", type.name)
    put("focalMode", focalMode.name)
    put("frameWidthMm", frameWidthMm)
    put("frameHeightMm", frameHeightMm)
    put("frameWidthPx", frameWidthPx)
    put("frameHeightPx", frameHeightPx)
    put("filmResolution", filmResolution)
    put("wavelengthNm", wavelengthNm)
}

private fun ViewingTarget.toJson(): JSONObject = JSONObject().apply {
    put("name", name)
    put("kind", kind.name)
    put("widthMm", widthMm)
    put("viewingDistanceMm", viewingDistanceMm)
    put("pixelsAcross", pixelsAcross)
    put("visualResolution", visualResolution)
    put("allowableBlur", allowableBlur)
    put("customCoc", customCoc)
}
