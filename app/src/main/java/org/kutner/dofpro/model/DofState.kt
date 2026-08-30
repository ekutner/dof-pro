package org.kutner.dofpro.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.kutner.dofpro.calc.Dof
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Everything the user has dialled in, plus the derived depth of field.
 *
 * Three quantities drive the whole display: focal length, aperture, and the subject
 * distance. Focal length and subject distance are the user's alone. The aperture and the
 * depth of field limits are two views of one relationship — set the aperture and the
 * limits follow; drag a limit and the aperture follows — so either can be the input.
 *
 * Distances are in millimetres. Focal length and f stop are stored as marked on the lens;
 * the teleconverter is applied on the way into the equations.
 */
class DofState(settings: Settings = Settings()) {

    val cameras = mutableStateListOf<Camera>().also { it.addAll(settings.cameras) }
    var cameraIndex by mutableIntStateOf(settings.cameraIndex)

    val camera: Camera
        get() = cameras.getOrElse(cameraIndex) { cameras.firstOrNull() ?: Camera() }

    val lenses = mutableStateListOf<Lens>().also { it.addAll(settings.lenses) }
    var lensIndex by mutableIntStateOf(settings.lensIndex)
        private set

    val lens: Lens
        get() = lenses.getOrElse(lensIndex) { lenses.firstOrNull() ?: Lens.ANY }

    val targets = mutableStateListOf<ViewingTarget>().also { it.addAll(settings.targets) }
    var targetIndex by mutableIntStateOf(settings.targetIndex)

    val target: ViewingTarget
        get() = targets.getOrElse(targetIndex) { targets.firstOrNull() ?: ViewingTarget() }

    /**
     * The blur that still counts as sharp, in mm at the sensor. Needs the camera and the
     * viewing target together — neither alone can say what will be noticed.
     */
    val coc: Double get() = circleOfConfusion(camera, target)

    /**
     * What one unit on the blur scale is worth: one just-resolvable detail, before the
     * allowance for how many of them are acceptable.
     */
    val blurUnit: Double
        get() = if (target.kind == TargetKind.CUSTOM) coc
        else (coc / target.allowableBlur.coerceAtLeast(1e-9)).coerceAtLeast(1e-6)

    /** The f stop at which diffraction blur alone reaches [coc]. */
    /**
     * The wavelength the optics answer to, or zero when diffraction is being ignored.
     *
     * Zero rather than a flag threaded through every equation, because it is what the
     * physics already says: Bd = f*(wl/550)/750, so a wavelength of nothing diffracts by
     * nothing, the focus blur budget becomes the whole circle of confusion, and the
     * aperture at which diffraction spends it recedes to infinity. Every formula keeps
     * working without knowing the setting exists.
     */
    val wavelengthNm: Double get() = if (ignoreDiffraction) 0.0 else camera.wavelengthNm

    val diffractionLimit: Double get() = Dof.fStopForDiffraction(coc, wavelengthNm)

    /** Deletes several viewing targets at once, never all of them. */
    fun removeTargets(indices: Set<Int>) {
        val gone = indices.filter { it in targets.indices }.sortedDescending()
        if (gone.size >= targets.size) return
        gone.forEach { targets.removeAt(it) }
        targetIndex = targetIndex.coerceIn(0, targets.lastIndex)
    }

    /**
     * Picks a lens and brings the current settings inside what it can do — a prime pins
     * the focal length to its one value, and an aperture beyond the lens's range is
     * wound back to the nearest stop it actually has.
     */
    fun selectLens(index: Int) {
        lensIndex = index.coerceIn(0, lenses.lastIndex)
        applyLensLimits()
    }

    /** Re-applies the current lens's limits, after selecting or editing one. */
    fun applyLensLimits() {
        val l = lens
        focalLength = if (l.isZoom) l.clampFocal(focalLength) else l.minFocal
        fStop = snapFStop(l.clampFStop(fStop))
        if (subject < minSubject) subject = minSubject
    }

    var theme by mutableStateOf(settings.theme)
    /**
     * Deletes several cameras at once, keeping the one in use pointing at something.
     *
     * Descending, because removing an item shifts everything after it and an ascending
     * walk would take the wrong ones out. Never empties the collection: an app with no
     * camera has no circle of confusion, and so nothing to compute with.
     */
    fun removeCameras(indices: Set<Int>) {
        val gone = indices.filter { it in cameras.indices }.sortedDescending()
        if (gone.size >= cameras.size) return
        gone.forEach { cameras.removeAt(it) }
        cameraIndex = cameraIndex.coerceIn(0, cameras.lastIndex)
    }

    /** As [removeCameras], for lenses — and the surviving lens's limits are re-applied. */
    fun removeLenses(indices: Set<Int>) {
        val gone = indices.filter { it in lenses.indices }.sortedDescending()
        if (gone.size >= lenses.size) return
        gone.forEach { lenses.removeAt(it) }
        selectLens(lensIndex.coerceIn(0, lenses.lastIndex))
    }

    var units by mutableStateOf(settings.units)

    /**
     * Focus stacking. The subject distance stops meaning "where the lens is focused" and
     * starts meaning "the closest thing that must come out sharp"; the far end is always
     * infinity, and the app works out how many frames it takes to get there.
     */
    var stacking by mutableStateOf(settings.stacking)

    /** Leaves diffraction out of the arithmetic. See [wavelengthNm]. */
    var ignoreDiffraction by mutableStateOf(settings.ignoreDiffraction)

    /** Fraction of each frame's depth of field the next one doubles back over. */
    var stackOverlap by mutableDoubleStateOf(settings.stackOverlap)
    var apertureStep by mutableStateOf(settings.apertureStep)
    var teleconverter by mutableStateOf(settings.teleconverter)
    var showDetails by mutableStateOf(false)

    /**
     * Writes the configuration out. Set by the activity; called whenever equipment or a
     * preference changes rather than only when the app is backgrounded, so a force-stop
     * or a crash cannot lose a camera the user just spent a minute describing.
     */
    var onPersist: (() -> Unit)? = null

    fun persist() {
        onPersist?.invoke()
    }

    /** Focal length as marked on the lens, in mm. Set by the user only. */
    var focalLength by mutableDoubleStateOf(settings.focalLength)
        private set

    /** f stop as marked on the lens. Set by the user, or computed from the limits. */
    var fStop by mutableDoubleStateOf(settings.fStop)

    /** Distance to the subject — the blue line. Set by the user only. */
    var subject by mutableDoubleStateOf(settings.subject)
        private set

    /**
     * No lens can form an image of anything closer than its own focal length — at that
     * distance the rays leave parallel and blur is infinite. Push past it and (A - L)
     * changes sign, which turns the depth of field equations inside out and puts the near
     * limit behind the subject. So the subject is not allowed within reach of it.
     *
     * The margin leaves 20:1 magnification as the closest the subject can get, which is
     * already far beyond what any real lens focuses to.
     *
     * Never nearer than [MIN_DISTANCE] either, which is where the scale stops being able
     * to draw. A focal length under a millimetre — the nominal "any lens" at 1 mm, entered
     * as a 35mm equivalent on a phone, works out to a fifth of one — would otherwise put
     * the subject closer than its own scale reaches, and the blue line would sit pinned to
     * the edge pointing at a distance the graduations never show.
     */
    val minSubject: Double
        get() = maxOf(effectiveFocal * SUBJECT_FOCAL_MARGIN, MIN_DISTANCE)

    /**
      * Moves the subject.
      *
      * Never rounded. A subject distance is a continuous quantity that the user places by
      * eye, and rounding it has nothing to recommend it: mid-drag it quantises the very
      * number the scale is drawn from and the graduations step backwards at every snap,
      * and on release it moves the line away from where it was let go. The read-outs
      * carry as many digits as it takes to tell the limits apart instead.
      */
    fun moveSubject(mm: Double) {
        subject = mm.coerceIn(minSubject, MAX_DISTANCE)
    }

    /**
     * Sets the focal length. A longer lens can push its own minimum past where the
     * subject is standing, in which case the subject has to give way — the alternative is
     * a distance the lens cannot focus at.
     */
    fun changeFocalLength(mm: Double, settle: Boolean = true) {
        // Whole millimetres, always. No lens is marked in tenths and no photographer
        // thinks in them, so a scale that reads 47.3 mm is offering precision that does
        // not exist. The lens's own ends are exempt, since those are whatever the lens
        // says they are.
        val wanted =
            if (settle) snapFocal(mm)
            else mm.roundToInt().toDouble().coerceIn(MIN_FOCAL, MAX_FOCAL)
        focalLength = lens.clampFocal(wanted)
        if (subject < minSubject) subject = minSubject
    }

    /** Rounds the focal length off to a readable value at the end of a drag. */
    fun settleFocalLength() = changeFocalLength(focalLength)


    // ---- The distance scale's window ----------------------------------------------

    /**
     * Where the subject is drawn on the distance scale, as a fraction from the top.
     *
     * Dragging the subject moves this with the finger, so the blue line actually travels
     * rather than sitting still while the graduations slide past it. It is held away from
     * the ends of the scale: once the line reaches the edge band the anchor stops and the
     * view takes over the movement, which is the point at which a drag turns into a
     * scroll.
     */
    var subjectAnchor by mutableDoubleStateOf(0.5)
        private set

    /** Moves the subject's resting place by a fraction of the scale's height. */
    fun nudgeSubjectAnchor(fraction: Double) {
        subjectAnchor = (subjectAnchor + fraction).coerceIn(MIN_ANCHOR, MAX_ANCHOR)
    }

    /** Where the focal length marker is drawn, as a fraction from the top of its scale. */
    var focalAnchor by mutableDoubleStateOf(0.5)
        private set

    /** Moves the focal length marker by a fraction of the scale's height. */
    fun nudgeFocalAnchor(fraction: Double) {
        focalAnchor = (focalAnchor + fraction).coerceIn(MIN_ANCHOR, MAX_ANCHOR)
    }

    /**
     * The stretch of distance the scale is currently showing. Refitted from the result
     * every time, including part way through a drag — a drag carries its own unsnapped
     * value along rather than reading the finger's absolute position, so the view is free
     * to scroll and re-scale underneath it without the drag chasing its own tail.
     */
    fun distanceWindow(result: DofResult): DistanceWindow = fitWindow(result)

    /**
     * How wide a stretch of distance the scale shows, in natural logs, or 0 to size it to
     * the depth of field.
     *
     * Zero is the resting state and gives the depth of field exactly its
     * [DOF_SHARE_OF_SCALE] of the height. Once the user pinches, or takes hold of a limit
     * marker, the span becomes theirs and stops chasing the depth of field — which is what
     * lets the red lines travel with a finger instead of standing still while the whole
     * scale rescales around them.
     */
    var distanceSpan by mutableDoubleStateOf(0.0)
        private set

    /**
     * Takes the span as it stands, so that whatever happens next moves the markers rather
     * than the scale. Does nothing if the span is already the user's.
     */
    fun holdDistanceSpan(lnSpan: Double) {
        if (distanceSpan <= 0.0 && lnSpan > 0.0 && lnSpan.isFinite()) distanceSpan = lnSpan
    }

    /**
     * True once the user has pinched, which frees the window from the band [fitWindow]
     * otherwise holds it in.
     *
     * That band exists so the depth of field keeps roughly its quarter of the height and
     * the limit markers have room to move. It is right for a view that sizes itself, and
     * wrong for one the user has taken hold of: within it the widest the scale can ever be
     * is its resting width, so pinching outwards did nothing whatever and pinching inwards
     * ran out after 3.2 times. A deliberate two finger gesture outranks the automatic fit.
     */
    var zoomed by mutableStateOf(false)
        private set

    /** Pinch. A [factor] above 1 is fingers spreading apart, which narrows the span. */
    fun zoomDistance(factor: Double, lnSpan: Double) {
        if (factor <= 0.0 || !factor.isFinite()) return
        holdDistanceSpan(lnSpan)
        zoomed = true
        distanceSpan = (distanceSpan / factor).coerceIn(MIN_WINDOW_SPAN, MAX_WINDOW_SPAN)
    }

    /** Hands the window back to the automatic fit. */
    fun resetZoom() {
        zoomed = false
        distanceSpan = 0.0
    }

    /**
     * Sizes the scale so the depth of field occupies between [DOF_SHARE_OF_SCALE] and
     * [MAX_DOF_SHARE] of its height, centred on the subject at its anchor.
     *
     * At rest the share is exactly the quarter: the scale is logarithmic, so that means a
     * span of ln(far/near) divided by that share, which also puts the blue line at the
     * middle of the scale with the same amount of out-of-focus context above and below.
     *
     * The share is a band rather than a single value because the quarter cannot be both
     * guaranteed and draggable. Held at exactly a quarter the red lines are nailed to the
     * screen — stopping down widens the view in step with the depth of field and the gap
     * between them never changes — so dragging one moves only the numbers. Within the band
     * the span stays put and the markers move, and the ends of the band are where the
     * scale takes over, exactly as the subject anchor's band works.
     */
    private fun fitWindow(r: DofResult): DistanceWindow {
        val subject = r.subject.coerceIn(MIN_DISTANCE, MAX_DISTANCE)

        // Stacking has no single depth of field to size the view by. What matters is the
        // stretch the frames cover — from the closest point out to the hyperfocal
        // distance, where the last one sits — so the view is fitted to that instead, with
        // the closest point low on the scale and room above the last frame for the
        // infinity it reaches.
        if (stacking) {
            val last = r.hyperfocal.takeIf { it.isFinite() && it > subject } ?: (subject * 50.0)
            val span = ln(last / subject) / STACK_SHARE_OF_SCALE
            val lnLo = ln(subject) - span * (1.0 - STACK_SHARE_OF_SCALE) * 0.35
            return DistanceWindow(exp(lnLo), exp(lnLo + span))
        }
        val near = r.near?.takeIf { it.isFinite() && it > 0.0 }
        val far = r.far

        // Nothing is sharp, so there is no depth of field to scale from.
        if (near == null || far == null) {
            val span = (if (distanceSpan > 0.0) distanceSpan else FALLBACK_SPAN)
                .coerceIn(MIN_WINDOW_SPAN, MAX_WINDOW_SPAN)
            return windowFor(subject, span)
        }

        // The far limit runs away to infinity as the subject approaches the hyperfocal
        // distance, so an exact quarter share would need a view of unbounded width. The
        // span is capped instead of special-cased: it grows to the cap as the far limit
        // recedes and simply stays there once it reaches infinity, which keeps the view
        // continuous through the hyperfocal distance. Past that point the sharp zone
        // occupies more than its quarter, which is honest — it really is deeper.
        val lnDepth = if (far.isFinite()) ln(far / near) else Double.MAX_VALUE
        if (lnDepth <= 0.0) return windowFor(subject, FALLBACK_SPAN)

        // [widest] is the guaranteed quarter share and has the last word. A shallow enough
        // depth of field needs a window narrower than [MIN_WINDOW_SPAN] to give it that
        // quarter — a Micro 4/3 sensor gets there easily, at about one per cent end to end
        // — so the pinch-in floor is applied inside that bound rather than against it.
        // Taking the floor first left the band inverted and coerceIn threw.
        val widest = minOf(lnDepth / DOF_SHARE_OF_SCALE, MAX_WINDOW_SPAN)
        val tightest = minOf(maxOf(lnDepth / MAX_DOF_SHARE, MIN_WINDOW_SPAN), widest)
        // A pinched view answers to the scale's absolute limits instead of the band, so
        // the gesture can actually reach somewhere the automatic fit would not have gone.
        val lo = if (zoomed) MIN_WINDOW_SPAN else tightest
        val hi = if (zoomed) MAX_WINDOW_SPAN else widest
        val span = if (distanceSpan > 0.0) distanceSpan.coerceIn(lo, hi) else widest
        return windowFor(subject, separated(span, subject, near, far))
    }

    /**
     * Tightens [span] until neither limit crowds the subject line off the screen.
     *
     * The quarter rule sizes the sharp zone as a whole, which says nothing about how that
     * quarter is divided, and it is rarely divided evenly: beyond the close-up range the
     * far limit takes almost all of it. At ten metres with a metre in front and ninety
     * behind, the near line lands about one per cent of the height from the subject —
     * a third of a fingertip — and there is no way to take hold of one without the other.
     * Zooming in is the only thing that separates them, so the view does it itself.
     *
     * Only when the view is sizing itself. A pinch is the user saying where they want the
     * view, and a floor that fought them there would make a shallow depth of field
     * impossible to zoom out of.
     */
    private fun separated(span: Double, subject: Double, near: Double, far: Double): Double {
        if (zoomed) return span
        val gaps = listOfNotNull(
            ln(subject / near).takeIf { it > 0.0 && it.isFinite() },
            if (far.isFinite()) ln(far / subject).takeIf { it > 0.0 } else null,
        )
        val room = gaps.minOrNull() ?: return span
        return minOf(span, room / MIN_MARKER_GAP)
    }

    /**
      * Places the window so the subject falls at [subjectAnchor] down the scale — exactly
      * there, with no clamping.
      *
      * Bounding the window instead used to slide it back inside the scale's absolute
      * limits, which left the subject drawn somewhere other than its anchor: at four
      * kilometres the line sat a tenth of the way down while the anchor said half way,
      * and a drag went on nudging an anchor that could not take effect. As the clamp
      * eased off further down the line crept back, which read as the scale wobbling.
      * Only the subject needs bounding; the graduations can say whatever they need to.
      */
    private fun windowFor(subject: Double, span: Double): DistanceWindow {
        val lnLo = ln(subject) - (1.0 - subjectAnchor) * span
        return DistanceWindow(exp(lnLo), exp(lnLo + span))
    }

    // ---- Snapping ----------------------------------------------------------------

    /** Rounds to roughly three significant digits, so 15.02 lands cleanly on 15.0. */
    private fun snapNice(v: Double, minStep: Double = 0.0): Double {
        if (v <= 0.0 || !v.isFinite()) return v
        val step = maxOf(10.0.pow(floor(log10(v))) / 10.0, minStep)
        return (v / step).roundToInt() * step
    }

    /** A whole number of millimetres; below 10 mm the tenths step would be finer. */
    fun snapFocal(v: Double): Double = snapNice(v, 1.0).coerceIn(MIN_FOCAL, MAX_FOCAL)

    /** The aperture always lands on a real f stop, dragged or calculated. */
    fun snapFStop(v: Double): Double = nearestFStop(v.coerceIn(MIN_F, MAX_F))

    // ---- Aperture ----------------------------------------------------------------

    /**
     * The f stops the aperture ring can actually be set to, at the current half or third
     * stop subdivision — the numbers engraved on real lenses. Dragging a depth of field
     * limit picks from exactly this list, which is why the limits jump between positions
     * rather than sliding.
     */
    fun apertureStops(): List<Double> {
        val l = lens
        return FStops.series(apertureStep)
            .filter { it >= MIN_F && it <= MAX_F }
            .filter { it >= l.minFStop - 1e-9 && it <= l.maxFStop + 1e-9 }
            .ifEmpty { listOf(l.clampFStop(MIN_F)) }
    }

    private fun nearestFStop(v: Double): Double =
        apertureStops().minByOrNull { abs(ln(it) - ln(v)) } ?: v

    /**
     * The stops a depth of field drag may choose from: the widest aperture up to the one
     * that gives the most depth, and no further.
     *
     * Stopping down widens the depth of field only while diffraction stays out of the way.
     * The limits are set by the product f·Bf, and Bf shrinks as f grows, so the product
     * peaks at f = c·750/√2 — about f/17 on 35mm film — and falls away after that. Past
     * the peak, stopping down gives *less* depth and more diffraction with it, so those
     * stops are never the right answer to "put the limit here": whatever depth they reach,
     * a wider aperture already reached it more sharply.
     *
     * Leaving them in the search made the marker hop about, because the fall-off mirrors
     * the climb and lands stops on top of each other. On 35mm film at 100 mm the near
     * limit at f/22 (8.04 m) falls between f/8 (8.10 m) and f/9 (7.95 m), so easing the
     * near limit down from f/8 jumped to f/22, and the next nudge came back to f/9.
     *
     * Cut at the peak and the limit moves monotonically with the aperture again, which is
     * what lets a drag walk the series one stop at a time.
     */
    private fun dofDragStops(): List<Double> {
        val stops = apertureStops()

        // How far each stop reaches: f·Bf, the term that places both limits.
        val reach = stops.map { stop ->
            val f = stop * teleconverter.factor
            Dof.focusBlurBudget(f, coc, 1.0, wavelengthNm)
                ?.let { f * it } ?: Double.NEGATIVE_INFINITY
        }
        val peak = reach.indexOf(reach.max())
        return stops.subList(0, peak + 1)
    }

    /**
     * The stop on this lens that gives this camera the most depth of field. Stopping past
     * it trades depth away for diffraction, so it is where a depth of field drag stops —
     * which is worth saying out loud, or the drag just appears to jam.
     */
    val bestDepthFStop: Double get() = dofDragStops().last()

    /** True once stopping down has stopped buying depth of field. */
    val atBestDepth: Boolean get() = fStop >= bestDepthFStop - 1e-9

    /**
     * The last stop on this lens that still has a depth of field, or null if none has.
     *
     * The camera's [Camera.diffractionLimit] is the exact f number where diffraction alone
     * spends the whole circle of confusion — f/23.6 on 35mm film. It belongs in the details
     * and in the camera editor, where it describes the body. It does not belong in a notice
     * on the main screen: no lens has an f/23.6 to be set to, so quoting it there names a
     * place the photographer cannot go and leaves them to work out which real stop it means.
     * The engraved stop below it is the same fact in a form that can be acted on.
     */
    val lastSharpFStop: Double? get() = apertureStops().lastOrNull { computeFor(it).near != null }

    /**
     * Which of the two diffraction notices belongs on screen, at most one.
     *
     * They are two readings of the same fact and their conditions overlap: everything past
     * the diffraction limit is also past the peak. Losing the depth of field entirely is
     * the more urgent thing to say, so it wins — and saying so here rather than leaving it
     * to the order of two branches in the layout is what makes it something a test can hold.
     */
    fun noticeFor(result: DofResult): Notice = when {
        result.near == null -> Notice.NOTHING_SHARP
        atBestDepth -> Notice.BEST_DEPTH
        else -> Notice.NONE
    }

    // ---- Derived values ----------------------------------------------------------

    /** Focal length fed to the equations: 35mm-equivalent corrected, teleconverted. */
    val effectiveFocal: Double
        get() = camera.actualFocalLength(focalLength) * teleconverter.factor

    /** f stop fed to the equations: a 2X teleconverter turns f/16 into f/32. */
    val effectiveFStop: Double
        get() = fStop * teleconverter.factor

    /** Image size over subject size at the sensor. */
    val magnification: Double
        get() {
            val a = subject
            val l = effectiveFocal
            return if (a.isFinite() && a > l) l / (a - l) else 0.0
        }

    fun compute(): DofResult = computeFor(fStop)

    /**
     * Where to focus each frame of a stack running from the subject distance out to
     * infinity, or nothing when the aperture leaves no depth of field to stack.
     */
    fun frames(result: DofResult): Dof.Frames = Dof.stackToInfinity(
        l = result.effectiveFocal,
        h = result.hyperfocal,
        near = result.subject,
        overlap = stackOverlap,
    )

    /** The depth of field a given marked f stop would produce, everything else unchanged. */
    private fun computeFor(marked: Double): DofResult {
        val cam = camera
        val c = coc
        val wl = wavelengthNm
        val l = effectiveFocal
        val f = marked * teleconverter.factor

        // Diffraction sets a floor on blur that focusing cannot undo. Whatever is left
        // over is the focus blur the depth of field limits are allowed to reach.
        val budget = Dof.focusBlurBudget(f, c, 1.0, wl)
            // Outside the lens's range there is no depth of field to speak of, and the
            // equations would hand back a near limit sitting behind the subject.
            ?.takeIf { subject > l }

        return DofResult(
            effectiveFocal = l,
            effectiveF = f,
            markedF = marked,
            subject = subject,
            near = budget?.let { Dof.nearLimit(l, f, subject, it) },
            far = budget?.let { Dof.farLimit(l, f, subject, it) },
            hyperfocal = budget?.let { Dof.hyperfocal(l, f, it) } ?: Dof.INF,
            blurAtSubject = Dof.diffractionBlur(f, wl) / blurUnit,
            coc = c,
            blurUnit = blurUnit,
        )
    }

    /**
     * Adopts the f stop whose near limit lands closest to [requested]. Distances are
     * compared logarithmically, to match the scale the line is being dragged on.
     */
    fun dragNearLimit(requested: Double) = adoptStopFor(requested) { it.near }

    /** As [dragNearLimit], for the far limit. */
    fun dragFarLimit(requested: Double) = adoptStopFor(requested) { it.far }

    private fun adoptStopFor(requested: Double, limit: (DofResult) -> Double?) {
        val target = ln(requested.coerceIn(MIN_DISTANCE, MAX_DISTANCE))
        var best: Double? = null
        var bestError = Double.MAX_VALUE
        for (stop in dofDragStops()) {
            val d = limit(computeFor(stop)) ?: continue
            if (d <= 0.0) continue
            // An infinite limit is drawn at the top of the scale, level with the longest
            // distance it can show, so clamping makes them compare equal — and the widest
            // aperture that reaches infinity wins, which is the one with least diffraction.
            val error = abs(ln(d.coerceIn(MIN_DISTANCE, MAX_DISTANCE)) - target)
            if (error < bestError - 1e-12) {
                bestError = error
                best = stop
            }
        }
        best?.let { fStop = it }
    }

    fun toSettings() = Settings(
        cameras = cameras.toList(),
        cameraIndex = cameraIndex,
        lenses = lenses.toList(),
        lensIndex = lensIndex,
        targets = targets.toList(),
        targetIndex = targetIndex,
        theme = theme,
        units = units,
        stacking = stacking,
        ignoreDiffraction = ignoreDiffraction,
        stackOverlap = stackOverlap,
        apertureStep = apertureStep,
        teleconverter = teleconverter,
        focalLength = focalLength,
        fStop = fStop,
        subject = subject,
    )

    companion object {
        const val MIN_FOCAL = 1.0
        const val MAX_FOCAL = 3000.0
        const val MIN_F = 0.5
        const val MAX_F = 256.0

        /**
         * Absolute limits on the distance scale. These are only a sanity backstop now
         * that the window is sized from the depth of field rather than fixed — wide
         * enough that the backstop almost never binds and cheats the sharp zone out of
         * its quarter of the height. 10 km stands in for infinity.
         */
        const val MIN_DISTANCE = 1.0
        const val MAX_DISTANCE = 10_000_000.0

        /** How far outside the focal length the subject must stay. */
        const val SUBJECT_FOCAL_MARGIN = 1.05

        /** How much of the distance scale's height the depth of field takes up. */
        /**
         * How far a limit line has to sit from the subject line, as a fraction of the
         * scale's height, before the view stops zooming in to separate them.
         *
         * A shade above the grab band, so the nearer of two lines is the one a fingertip
         * actually lands on.
         */
        const val MIN_MARKER_GAP = 0.06

        const val DOF_SHARE_OF_SCALE = 0.25

        /**
         * The most of the height the depth of field may take. Zooming in past this puts
         * one of the red lines off the screen, and a marker that cannot be seen cannot be
         * dragged — the same reason the subject anchor stops at [MAX_ANCHOR].
         */
        const val MAX_DOF_SHARE = 0.8

        /**
         * How close to the ends of the scale the subject line may be carried before the
         * view starts scrolling instead. A fifth in from either end leaves the middle
         * three fifths for the line to move through under the finger.
         */
        const val DEFAULT_STACK_OVERLAP = 0.2
        const val MAX_STACK_OVERLAP = 0.5

        /** Share of the height the stack's own range takes when stacking. */
        const val STACK_SHARE_OF_SCALE = 0.7

        const val MIN_ANCHOR = 0.2
        const val MAX_ANCHOR = 0.8

        /** Span used when there is no depth of field to scale from, in natural logs. */
        val FALLBACK_SPAN: Double = ln(10.0)

        /**
         * Widest the distance scale will ever open, in natural logs — four decades. The
         * quarter-height rule holds exactly while the far limit is within ten times the
         * near limit, and gives way gracefully beyond that rather than running away to an
         * infinitely wide view as the subject nears the hyperfocal distance.
         */
        val MAX_WINDOW_SPAN: Double = ln(10_000.0)

        /** The narrowest view the scale will show: a couple of per cent, end to end. */
        val MIN_WINDOW_SPAN: Double = ln(1.02)


    }
}

/**
 * The standing note along the bottom of the scales, if any.
 *
 * Both are consequences of diffraction and both are answered by the same lever, the
 * circle of confusion: [BEST_DEPTH] is where stopping down stops buying depth of field
 * (f = c*750/sqrt(2)) and [NOTHING_SHARP] is where it has taken all of it (f = c*750).
 */
enum class Notice { NONE, NOTHING_SHARP, BEST_DEPTH }

/** The stretch of distance the scale is showing, near end to far end. */
data class DistanceWindow(val lo: Double, val hi: Double)

/** The numbers DoF puts on screen. */
data class DofResult(
    val effectiveFocal: Double,
    val effectiveF: Double,
    /** f stop as it appears on the lens dial, i.e. before the teleconverter. */
    val markedF: Double,
    /** Distance to the subject — the blue line. */
    val subject: Double,
    /** Near limit; null when diffraction alone already exceeds the circle of confusion. */
    val near: Double?,
    val far: Double?,
    val hyperfocal: Double,
    /**
     * Blur at the subject in units of [blurUnit] — pixels, or resolvable details on the
     * print. Focus blur is zero there, so this is pure diffraction: the sharpest the image
     * can be at this aperture. Once it reaches [sharpBlur], nothing in the frame is
     * critically sharp and the limits vanish.
     */
    val blurAtSubject: Double,
    /** The blur that counts as sharp, in mm. */
    val coc: Double,
    /** What one unit on the blur scale is worth, in mm. */
    val blurUnit: Double,
) {
    /**
     * The blur reading at the depth of field limits — the camera's allowable blur, since
     * that is how many units of blur it is willing to call sharp. This is the number the
     * red lines sit on, and it is 1 only when the allowance is one unit.
     */
    val sharpBlur: Double get() = if (blurUnit > 0) coc / blurUnit else 1.0

    /** Depth of the sharp zone in front of the subject. */
    val front: Double? get() = near?.let { if (it.isFinite()) subject - it else null }

    /** Depth of the sharp zone behind the subject. */
    val back: Double? get() = far?.let { if (it.isFinite()) it - subject else null }

    val range: Double?
        get() {
            val n = near ?: return null
            val f = far ?: return null
            return if (n.isFinite() && f.isFinite()) f - n else Dof.INF
        }

    /** Where the subject sits in the sharp range, as a percentage from the front. */
    val focusPercent: Double?
        get() {
            val fr = front ?: return null
            val rg = range ?: return null
            return if (rg.isFinite() && rg > 0) 100.0 * fr / rg else null
        }
}
