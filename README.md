# DoF Pro — depth of field calculator for Android

An Android reimplementation of the DoF 4.0 desktop calculator described at
<https://www.dl-c.com/DoF/>, matching its camera-like interface and its optics.

Unlike ordinary depth of field calculators, this one accounts for **diffraction as well as
focus blur**, and lets you choose the circle of confusion from the actual resolution of
your sensor rather than a print-viewing convention from the film era.

## The interface

Four scales fill the screen, as on the desktop app:

| Scale | Layout | Who moves it |
|---|---|---|
| **lens** (focal length in mm) | harmonic | the user, only |
| **aperture** | logarithmic, whole stops evenly spaced | the user, or calculated from the limits |
| **distance** | logarithmic, auto-fitted to the depth of field | see below |
| **blur** | harmonic, marker centred | calculated, only |

Every marker you can drag travels with your finger. It stops a fifth in from either end of
its column, and from there the same movement scrolls the scale past it instead — so a drag
never dies against an edge, and it never feels like only the graduations are moving. On
the lens scale that means dragging the marker *down* lands it on the smaller focal lengths
printed below it, which is the opposite of pulling a fixed scale past a fixed marker, and
the only reading that makes sense once the marker is the thing that moves.

The distance scale carries four markers:

- a **blue** line at the subject, which only the user moves;
- two **red** lines at the near and far edges of the acceptable depth of field, which
  follow the aperture — or which the user can drag to choose the aperture instead;
- a **green** line at the hyperfocal distance, which is only ever calculated.

The hyperfocal distance is also written above the scale, in the green of its own line. The
scale zooms to the depth of field, so at ordinary apertures the green line is somewhere off
the top of the visible window and the number is the only way to read it. It is written in
whatever unit suits its own size rather than the column’s: it is a distance in its own
right, not one of the three being compared against each other.

Above the lens scale is the teleconverter, written as **1.0X** rather than 1.0 — a bare
number reads as a setting of some kind, and the X says what it actually is. The aperture
scale has no control of its own: whether the ring moves in half or third stops is a
preference set once in Settings, not a per-shot decision, so the row above it is left empty
to keep all four scales starting on the same line.

Aperture and depth of field are two views of one relationship, so either can be the input.
Change the aperture and the red lines move. Drag a red line and the aperture moves, landing
only on real f stops, which is why the limits jump from one position to the next rather
than sliding.

The apertures on offer are the ones engraved on lenses, not the exact powers of √2 — the
arithmetic value of a whole stop above f/4 is 5.657, but every lens in the world says 5.6,
and 2^3.5 is 11.3 where every lens says 11. `FStops` holds the conventional half and third
stop series, and both the scale and any calculated aperture come from it, so the app never
shows you an f/7.13 you could not dial in.

Down the left of the distance scale runs a second set of graduations: how blurred a subject
at that distance would be, as a multiple of the circle of confusion, coloured from white
through yellow and red to blue as it grows. **One is always among them.** Blur of one
circle of confusion *is* the sharpness criterion, so those two graduations land exactly
level with the red lines and give the number the whole axis is read against — every other
graduation says how many times worse than acceptable that distance is. It is laid out
before the rest and never dropped to make room: laid out in scale order it would lose its
label to whichever neighbour came first, and which neighbour that was flipped either side
of the subject. There is always room, in any case, because the scale is fitted so that the
depth of field — which is exactly the stretch between those two graduations — fills a
quarter of the height. Past the hyperfocal distance the far one is gone, the far limit
being infinite, and only the near one is drawn.

The blur read-out is the blur at the subject. Focus blur is zero there, so what is left is
pure diffraction — the sharpest the picture can be at this aperture. When it reaches 1.0,
diffraction alone equals the circle of confusion, nothing is critically sharp, and the red
lines disappear.

**What counts as sharp** is the circle of confusion of the selected camera. The red lines
sit exactly where total blur — focus and diffraction added in quadrature — equals one
circle of confusion. For the default 35mm film camera that is 0.0314 mm, which comes from
a 12 inch print viewed at 18 inches by an observer resolving 1 arc minute, times an
allowable-blur factor of 2. Every one of those numbers is editable in the camera dialog,
and switching to a Sharp Image camera derives it from sensor pixel pitch instead.

**Past the diffraction limit there is no depth of field, and the app says so.** Blur from
a small aperture alone can exceed the circle of confusion, in which case no distance meets
the sharpness criterion and the limits genuinely do not exist — the reference app drops
its markers here too, and the manual devotes a section to it. High resolution bodies reach
this early: a 61 MP full frame sensor judged by the Sharp Image criterion is diffraction
limited at **f/5.6**, so anything beyond it shows no limits at all. Bare dashes read like a
fault, so a notice names the last aperture that still works — the engraved stop below the
limit, not the limit itself — and offers the documented remedy: a larger allowable blur in
the camera, which is to say accepting a slightly softer result in exchange for some depth
of field.

The notice floats over the scales rather than sitting above them, and that is not
cosmetic. Anything that reflows the column mid-drag moves the canvas under a still finger,
and a scale measuring pointer positions takes that for a drag of the same size: the notice
appearing the instant the aperture crossed its limit shifted the canvas 208 px and sent
f/6.3 straight to f/13. Scales now measure movement rather than position, which localises
such a shift to a single event, and the notice no longer causes one at all.

**The subject may not stand closer than the focal length.** No lens forms an image of
anything inside it — the rays leave parallel and blur is infinite — and pushing past it
flips the sign of `(A - L)`, which turns the depth of field equations inside out and puts
the near limit *behind* the subject. The subject is held at 1.05 times the focal length or
beyond, and choosing a longer lens pushes a too-close subject out ahead of it.

**The distance scale zooms itself, to a fixed rule: the depth of field always occupies a
quarter of its height.** A scale with fixed ends spanning arm's length to infinity cannot
resolve the couple of inches that are sharp in a close-up — at 50 mm and f/4 on a subject
4 ft away the near, subject and far lines land 7 px apart, closer together than a
fingertip. Since the scale is logarithmic, holding the sharp zone at a constant quarter of
the height just means spanning `ln(far / near) / 0.25`, centred on the subject. That also
puts the blue line at the exact middle of the scale every time, with the same amount of
out-of-focus context above and below it.

At rest the rule holds however the settings change: stopping down widens the *view* rather
than spreading the red lines apart, so the gap between them stays put — measured on a phone
it is 444 px at f/3.5, f/4 and f/9 alike.

**But a quarter cannot be both guaranteed and draggable**, and that is why the share is a
band rather than a single number. Nailed to exactly a quarter the red lines cannot move:
the view grows in step with the depth of field, so dragging one changes the numbers and
nothing else, which is precisely the complaint. So the span becomes the user’s the moment
a limit marker is picked up, and from then on the depth of field may occupy anywhere from a
quarter of the height to four fifths of it. Inside that band the scale holds still and the
markers travel with the finger; at either end the scale takes over — exactly how the
subject marker’s anchor band works. Dragging the near limit down a phone screen:

| | share of the height |
|---|---|
| at rest | 0.25 |
| dragging out | 0.31 → 0.38 → 0.47 → 0.57 → 0.69 |
| held at the far end | 0.74, 0.74, 0.74 … |
| dragging back in | 0.69 → 0.61 → 0.53 → 0.43 → 0.34 → 0.28 |

**Pinching the distance scale zooms it**, within that same band: out until the depth of
field is back to its guaranteed quarter, in until it fills four fifths of the height and
one of the limits is about to leave the screen. The band is the whole zoom range, because
both of its ends are things the app has promised — the quarter, and a marker you can still
see to drag.

The quarter is a floor on the markers, so there is no floor on the zoom: however shallow
the depth of field, the view closes right down around it rather than letting the markers
bunch up against the subject line. A 61 MP full frame body at f/5.6 has 3.5 mm of depth at
two metres, and it still gets its quarter.

That does leave the whole view a few millimetres wide, and **the drag rate accelerates
with how far the finger has already travelled** to cope. A constant rate cannot serve both
ends of it: matching a view that narrow crawls, and outrunning it throws away the
precision close-up work needs. So the first stretch runs at the visible scale's own rate —
within a few per cent of it over the first tenth of a screen, so the graduations keep pace
with the finger — and from there it builds, until a full screen of travel covers three
decades. Measured on the phone at two metres: 60 px moves the subject 1 mm, 200 px moves
8 mm, 500 px moves 10 cm, 900 px moves half a metre.

The marker travels with the finger the whole time, and the view re-scales underneath it.
Both can happen at once because the view is positioned *from* the value and the marker's
anchor, so any pairing of the two is consistent by construction — the marker never stops
pointing at its own reading however fast the numbers run.

**Speed has a say as well.** Travel alone decides the rate for a slow, deliberate drag,
which is what keeps fine placement possible; a flick is a request to cover ground, and it
is answered by up to a further factor of four. The same 600 px drag moves 2 m to 1.8 m
taken slowly and to 1.58 m flicked. Speed is measured from the event clock rather than
inferred from the size of each delta, which would make the same physical gesture mean
different things on devices reporting pointer movement at different rates.

The curve is cubic in travelled distance, so near zero it is indistinguishable from the
plain rate rather than accelerating out from under the finger immediately, and the rate
the drag actually applies is that curve differentiated — there is a test that integrating
one reproduces the other. The graduations carry enough decimals to tell each other apart,
which significant figures alone will not do at that zoom: 1.9985 and 1.9990 both round to
1.999, and the scale reads as though it had stopped.

**Nothing about the subject distance is rounded.** It is a continuous quantity placed by
eye, and rounding it only ever caused trouble — mid-drag it quantised the number the scale
is drawn from and the graduations stepped backwards at every snap; on release it moved the
line away from where it had just been let go, which is no way for a control to behave.

**The window is never clamped to the scale's absolute limits either.** Sliding it back
inside them left the subject drawn somewhere other than its anchor — at four kilometres
the line sat a tenth of the way down while the anchor said half way — so a drag went on
nudging an anchor that could not take effect, and as the clamp eased off further down the
line crept back. Only the subject needs bounding; the graduations can read whatever they
need to.

It cannot hold everywhere, though, and where it gives way matters. As the subject
approaches the hyperfocal distance the far limit runs away to infinity, so an exact
quarter share would need an infinitely wide view — the span really does diverge. Rather
than special-case infinity, which put a visible jump at the hyperfocal distance, the span
is simply **capped at four decades**. It grows towards that cap as the far limit recedes
and stays there once the limit is infinite, so the view passes through the hyperfocal
distance without a step. Past the cap the sharp zone occupies more than its quarter, which
is honest — it really is that deep. The other exception is when nothing is sharp at all:
there is no depth of field to scale from, so the view settles at one decade around the
subject.

Beyond the hyperfocal distance the far limit is infinite and simply off the top of the
view, so no far line is drawn — pinning one to the top edge would put a red line at a
distance it does not apply to. The near limit is the one that still means something, and
the header reads the far limit out as ∞.

Measured on a phone, a marker sits at exactly the same offset from the finger on every
frame of a drag, and a 300 px drag moves it 298 px until it reaches the band, after which
it holds still and the values keep running.

**Values are only rounded once the finger lifts.** Rounding mid-drag quantises the very
number the scale is drawn from while the marker keeps moving smoothly, so between snaps
the graduations translate with the finger and at each snap they jump back to keep the
marker on its anchor — a sawtooth against the direction of travel, ±15 px per whole
millimetre on the lens scale. Letting the drag run unrounded and settling on release takes
the direction changes over a sample drag from 26 to none. So the read-out shows 44.2 mm
while you drag and 44 mm when you let go.

Because the view re-scales as the subject moves, a drag cannot read the finger's absolute
position — it would be measuring against a mapping that had already moved, and the marker
would chase its own tail. Instead a drag carries its own unsnapped value and steps it
along by each movement, at the rate of the scale as currently drawn — exactly the visible
scale, so line and graduations move together.

That also makes the drag accelerate on its own. A step is a fixed fraction of the visible
scale, and the scale widens as the subject recedes and its depth of field grows, so equal
swipes cover more ground the further out you go — 4 ft to 4.5 to 5.1 to 5.9 to 7 to 8.5 to
11 to 16 to 26 to 44 across ten identical drags, and smoothly on through the hyperfocal
distance from there.

Zoomed this close, three distances rounded to three significant figures can print as the
same number, so the near/subject/far read-outs use however many digits it takes to tell
them apart.

Dragging is by zone rather than by aiming at a line, since the lines can still be close
together: a band on the subject drags the subject, anywhere nearer drags the near limit,
anywhere beyond drags the far limit. Only a tap inside the subject band moves the subject —
letting any tap move it meant a drag that started in a limit zone and barely travelled
registered as a tap and teleported the blue line to the finger, which read as the subject
wandering on its own.

And stopping down does not widen the depth of field indefinitely. Past a certain aperture
diffraction takes more than the extra focus range gives back, so dragging a red limit
outward stops moving — there is genuinely no aperture that reaches further.

Every value a drag produces is derived from the finger's position relative to where the
gesture started, never accumulated event by event. Accumulating breaks twice over: a slow
drag whose per-event step is smaller than one snap increment rounds back to where it
started and the scale sticks, and any stray event creeps the value along permanently.

On the lens, aperture and blur scales a **red** marker is something you set and a **white**
one is something the app computes. The double cone between the two axes of the distance
scale is the blurred image of a very narrow vertical line: its width is proportional to
blur and its colour follows the manual's colour coding (white → yellow → red → magenta →
blue).

## Focus stacking

Turned on from the kebab menu, and it changes what the middle of the screen means.

The subject distance stops being where the lens is focused and becomes **the closest thing
that must come out sharp**. The far end is infinity by definition. Between them the app
works out the fewest frames that hold the whole range, and marks in yellow where to focus
each one:

```
closest    images    far
 0.5 m        3       ∞          24 mm at f/8

  3 ──────────────  2.46 m       ← the hyperfocal distance
  2 ──────────────  1.03 m
  1 ──────────────  0.623 m
  ▶ ──────────────  0.5 m        ← the closest point, yours to drag
```

Tapping the count lists the focus distances. The read-outs that describe a *single* frame
stand down while stacking — the blur cone, the blur graduations and the two red limits all
belong to one focus setting rather than to the set, so they would be answering a question
nobody asked.

`Dof.stackToInfinity` is the inverse of the existing `focusStack`, which is handed a frame
count and finds the aperture it needs. Here the aperture is the photographer's and the
count falls out of it. Each frame is placed so its *near* limit lands where the previous
frame's coverage ran out, which is minimal by construction — no frame could start further
out without leaving a gap. The last is focused at the hyperfocal distance, the closest
focus that still holds infinity and therefore the one reaching back furthest while doing so.

**Focus stack overlap**, in Settings, is how far each frame doubles back over the one
before it, so a stack has no hairline seams to go wrong in the blend. It defaults to 20%
and stops at 50%. It is measured in *reciprocal* distance, because that is the space a
stack is uniform in — equal steps there are equal rotations of the focus ring — so the
figure means the same thing at the near end of a stack as at the far end. Measured on a
phone, 50 mm at f/8 from one metre:

| overlap | frames |
|---|---|
| 0% | 6 |
| 20% | 7 |
| 50% | 10 |

A stack is capped at 999 frames. That is not a real photographic limit but a runaway
guard: a fast lens focused close needs hundreds of frames to reach infinity, and the
arithmetic should stop rather than the app.

## Equipment

One row sits above the scales: which **camera** and which **lens** the numbers are for, and
a kebab menu for everything that is not a per-shot decision — Settings, Details (the panel
the Windows version keeps in a fifth column), and Help.

Choosing a camera is really choosing a circle of confusion. Choosing a lens confines the
scales to settings that lens actually has: the focal length scale is limited to the range
it covers, and becomes a read-out with a white marker for a prime; the aperture scale
offers only the stops between its widest and narrowest, and a depth of field limit dragged
beyond what the lens can deliver stops at the stop that can.

**A zoom lens draws its own range and nothing else**, the way its barrel is engraved. The
marker travels the full column and the graduations hold still — the aperture scale already
behaves that way — because the focal length cannot leave the lens, so there is nothing
beyond its ends worth scrolling to. Both ends are always graduated and labelled, and the
ordinary round numbers fill in between; any of them that would collide with an end gives up
its label and keeps its tick, since two numbers on top of each other read as neither.

This replaced a scale that placed itself around the current focal length, which could not
keep both ends on screen. That layout is harmonic — linear in 1/value — and reaches much
further above the marker than below it: from a little over half its value to nearly six
times it. So any zoom wider than about 1.8:1 lost its short end whenever the marker went
near the long one, and the 100-400 at 400 mm showed 219–2353 mm with 100 mm off the bottom.
No amount of zooming out fixed it; with the marker mid-column nothing below half its value
can be drawn at all.

Two lenses keep the moving view, because neither is a barrel to engrave. A **prime** has a
single focal length rather than a stretch, so its scale stays a read-out placed around that
value, with the focal length labelled and the marker locked. And the nominal **Any lens**,
covering 1 mm to 3 metres, would crush every real focal length into the last few pixels if
drawn end to end. `Lens.WIDEST_DRAWN` is set at 40:1, well past the widest superzoom ever
built at about 22:1, so only a placeholder like that falls through.

### Looking a camera up by name

A camera's frame size and resolution can be fetched instead of typed. The search goes to
Wikipedia, and the choice of source and endpoint both took some finding.

Not a table shipped inside the app: that needs a new build every time a camera is released.
Not a search engine: those return links to pages in a hundred different shapes, and
scraping Google is against its terms and actively defended besides. Wikipedia's API is
free, unauthenticated, meant to be called, and returns articles whose infobox is the same
shape every time.

**Full text search, not the prefix endpoint.** Asked for "sony a7r v", `action=opensearch`
offers *Sony α7* — a different camera with a different sensor — while
`action=query&list=search` puts *Sony α7R V* first. It also copes with how people type:
"lumix s5ii" finds an article titled *Panasonic Lumix DC-S5M2*, which nobody would guess.

**The results are a list to choose from, never one answer taken on trust.** Search is good
but not certain, and a calculator that quietly adopted the wrong sensor would put every
distance on screen slightly out with nothing to show for it.

**Only explicit millimetres, from that camera's own article.** A sensor format is a family,
not a measurement, and the families are not tidy:

| "APS-C" means | |
|---|---|
| Canon EOS R7, 90D | 22.3 × 14.9 mm |
| Sony α6700 | 23.3 × 15.5 mm |
| Nikon Z50 | 23.5 × 15.7 mm |
| Fujifilm X-T5 | 23.8 × 15.6 mm |

Reading "APS-C" and filling in a nominal figure would be wrong for three of those four, and
six per cent wrong for Canon — which moves the circle of confusion and every distance with
it. Full frame is no better behaved: 35.6, 35.7, 35.9 and 36 mm across four makers. So a
format name yields nothing and the user is asked.

**The frame's shape and the image's shape must agree.** Pixels are square, so a sensor's
aspect ratio and its resolution's aspect ratio are one number. When they disagree by more
than a few per cent the resolution is not the sensor's — scanning an E-M1 Mark III turned
up 4096 × 2160, a video mode — and it is dropped, keeping the frame size, which is the
harder of the two to look up by hand.

Everything the lookup fills in is an ordinary editable field afterwards. Nothing about it
is load-bearing: with no network, or for a camera Wikipedia has never heard of, the form
works exactly as it did before.

Settings holds the units of measure, the aperture increments, and the camera and lens
collections. All of it is written to storage as soon as it changes, not only when the app
is backgrounded — a force-stop should not lose a camera somebody just spent a minute
describing.

**The unit is not a setting; only the family is.** A calculator that may be looking at
forty millimetres of depth or forty metres of it has no one unit that suits both, and
asking the reader to switch by hand as they pan is asking them to do the app’s job. So the
choice is metric or imperial, and the unit and its precision come from the distance:

| metric | | imperial | |
|---|---|---|---|
| over 10 m | metres, no decimals | over 10 m | feet, no decimals |
| 0.5 m to 10 m | metres | 2 ft to 10 m | feet |
| 20 cm to 0.5 m | centimetres, whole | under 2 ft | inches |
| under 20 cm | centimetres, tenths | | |

The two families change over at different places on purpose. Metric leaves centimetres at
half a metre; imperial holds on to inches until two feet, because a foot and a half is
spoken as eighteen inches and only past a couple of feet does anyone reach for feet.

Two things are shared rather than mirrored. Where a cell says nothing about decimals the
precision is open, and the read-outs take as many digits as it costs to tell the near
limit, the subject and the far limit apart — a shallow depth of field printing the same
number three times hides exactly what the reader came for. And the decimals stop at ten
metres in *both* families, about 33 ft, not at a rounder 30 ft: how precisely a distance
is worth quoting is a fact about the distance rather than about the units, and a rounder
threshold would leave a stretch where an imperial reader was told less than a metric one.
Inches keep their precision throughout for the same reason — rounding them whole would be a
quantum two and a half times coarser than the whole centimetres metric uses over the same
ground, and a close-up depth of field would disappear inside it.

One unit serves a whole scale, taken from the geometric mean of the window — the midpoint
of a logarithmic axis — so the three read-outs and every graduation below them are in the
same terms and can be compared at a glance. Graduations keep whatever decimals it takes to
tell one from the next, since a window a few millimetres wide would otherwise lose all its
labels to the read-outs’ rounding.

An older installation stored one fixed unit rather than a family; `UnitSystem.parse` maps
each of them to the family it belonged to, so upgrading does not quietly move an imperial
user to metric.

## Light and dark

Both, following the phone unless Settings says otherwise — System, Light or Dark. The
change takes effect live; nothing needs restarting.

The chrome is two Material 3 schemes and needs no explanation. The instrument is the
interesting half, because its colours carry meaning rather than style. Three things had to
be decided rather than converted:

- **The manual's "sharp" is white**, which only says anything against a dark ground. On a
  light one white is the absence of ink, so the light theme reads the blur scale as ink
  instead: sharp is near-black, and yellow, red, magenta and blue are each darkened enough
  to hold their contrast on white. The order and the meaning are untouched — only what
  "nothing" looks like.
- **The marker colours are darkened, not swapped.** Red, blue, green and the stack's yellow
  all mean something specific, so the light theme keeps every hue and only takes the
  brightness down far enough to read on white. A pure yellow stack line would have been
  invisible.
- **The blur cone is pixels, not a draw call.** Its colours are baked into a bitmap when it
  is built, so `Palette.dark` is one of the keys it is remembered against; without that it
  would survive a change of theme and sit there in the wrong colours.

`Palette` holds the theme as one snapshot-state flag rather than passing a palette down.
These colours are read from inside `Canvas` draw lambdas throughout the instrument, and
threading an argument through every one of them would be noise for no gain. Snapshot state
is what makes it correct: everything drawn from it redraws when the flag changes, which is
why it is not a plain field.

### The settings and equipment screens

Grouped into cards by subject rather than run together down one column. The grouping is
the work: a settings screen is a list of decisions, and a flat run of fields makes the
reader work out for themselves which lines belong to which decision.

**Settings fits on one screen**, which took some restraint. Theme, units and aperture
increments share a single card ruled apart, rather than taking a card and a heading each —
three cards in a row spend more height on the gaps between them than on the settings. And
none of the three is explained, because a segmented button reading *Metric | Imperial* has
already said everything a sentence underneath could add. Only the focus stack overlap keeps
its note, being the one figure here whose effect is not written on its face.

Three things fell out of thinking about what each screen actually is:

- **The equipment lists are cards, not list rows.** These are things you own rather than
  settings you set, and which one is in use is worth seeing from across the room. A card in
  the accent colour with "In use" on it says that; a small radio button in a column of
  look-alike rows has to be hunted for. The add button is an extended FAB, so it says what
  it adds. A camera row carries its name and nothing else: the circle of confusion it works
  out to is a consequence rather than a way of telling one camera from another, and it
  belongs in the editor where it can be changed.
- **Settings and the two equipment screens share one window.** They used to have a window
  each, and moving between them tore the first down before the second was up — for that
  frame nothing covered the scales, which read as the main screen flashing every time you
  came back from editing a camera. A dialog *is* a window, so the fix was to keep one and
  swap only its contents: `FullScreenDialog` now wraps all three, and each is a plain
  screen rather than a dialog of its own. Verified by window identity rather than by
  watching for a flash — the same window token survives Settings → Cameras → Settings, so
  there is no longer a gap to see through.
- **Long press starts a selection**, and while one is running a tap adds to it rather than
  choosing a camera to shoot with — two jobs for one gesture, told apart by whether a
  selection is already under way, which is how every list on the phone behaves. The app bar
  becomes a count with a delete beside it. Deleting several at once removes them in
  descending order, because taking one out shifts everything after it and an ascending walk
  would take the wrong ones; and the last one can never be deleted, an app with no camera
  having no circle of confusion and so nothing to compute with.
- **The camera form leads with the search.** It is how most of the form gets filled in, so
  it goes above everything it fills, with a rule under it — what follows is the same
  information by hand. Then **Camera** (name, type, focal length entry), **Sensor** (frame
  size and resolution), **What counts as sharp** (criterion and its terms), and the
  computed figures last. Type sits above the sharpness criterion because film or digital
  decides what the rest of the form even asks for — pixels or line pairs — and a question
  belongs above the questions it changes.
- **The two pickers on the main screen are filled tonal blocks, not outlined chips.** An
  outline is a hairline among hairlines — the panels below are outlined too — so a chip
  read as one more division of the screen rather than as something to press. A tinted block
  reads as a control at a glance, and a small caption says which of the two it is without
  the reader having to know a camera name from a lens name. The scales themselves are
  untouched.

## Two kinds of surface

The chrome and the instrument are deliberately different things, and they follow different
rules.

Everything you configure — the pickers, Settings, the camera and lens managers — is
**Material 3**: a dark colour scheme, app bars with a back affordance, segmented buttons
for single choices, list items with radio selection and supporting text, exposed dropdown
fields, a floating action button to add, and a confirmation dialog before anything is
deleted. Each collection is a screen of its own with a form behind each member, which
replaced a Prev/Next/New/Delete button row — that is a desktop dialog idiom, and on a
phone the collection itself should be the screen.

The four scales are **not** themed. They are a drawn instrument rather than a set of
components, and their colours carry meaning no theme has a say over: the blur colour
coding from the manual, and red, blue and green for the depth of field markers. Those stay
in `Palette` while the chrome takes `MaterialTheme`.

Values always round off to readable numbers when a drag ends; there is no setting for it.

The desktop app splits this behaviour across five modes — depth of field, best f stop,
focus stacking, blur and macro. Here the two-way binding between aperture and the depth of
field limits covers the first two on one screen, so there are no modes. The equations for
focus stacking (`Dof.focusStack`) and macro (`Dof.macroDof`) are still in the calculation
engine and still under test, but nothing in the interface reaches them.

## The optics

`calc/Dof.kt` implements Appendix C of the reference manual verbatim. Two points worth
recording, because the manual states them loosely:

- **Blur combines in quadrature**, `B = √(Bd² + Bf²)`. The manual calls the combination a
  geometric mean, but a geometric mean is constant in f and so has no minimum; quadrature
  is the definition that makes the manual's own best-f-stop equation the true minimum of
  the combined curve, and it reproduces the published screenshots exactly.
- **The allowable-blur factor applies to both CoC methods** — pixels for Sharp Image,
  visually resolvable details for Sharp Print. Only then do the manual's two quoted
  values fall out (0.01 mm and 0.03 mm for a Nikon D810).
- **Stopping down stops helping**, and then starts hurting. The limits are placed by the
  product `f·Bf`, and the focus blur budget `Bf = √(c² − Bd²)` shrinks as diffraction
  grows, so the product peaks at `f = c·750/√2` — about f/17 on 35mm film, f/4 on a 61 MP
  full frame — and falls away after that. Dragging a depth of field limit therefore
  chooses only from the stops up to that peak (`dofDragStops`). Past it the fall-off
  mirrors the climb and lands stops on top of each other: on 35mm film at 100 mm and 10 m
  the near limit at f/22 is 8.04 m, which falls between f/8's 8.10 m and f/9's 7.95 m, so
  a nearest-match search over the whole series jumped from f/8 to f/22 and then back to
  f/9. Those stops are never the right answer to "put the limit here" anyway — whatever
  depth they reach, a wider aperture already reached it with less diffraction. They
  remain available to set by hand on the aperture scale, where the photographer may want
  one for reasons the depth of field cannot see.

Both diffraction walls are placed by the circle of confusion alone, and both are announced
along the bottom of the scales — at most one at a time, chosen by `noticeFor` rather than
by the order of two branches in the layout, since every aperture past the limit is past the
peak as well:

| | at | notice on 35mm film |
|---|---|---|
| depth of field stops growing | `f = c·750/√2` = f/16.6 | *Most depth of field at f/16* |
| nothing is acceptably sharp | `f = c·750` = f/23.6 | *Reduced sharpness past f/22* |

Both notices name an **engraved stop**, never the exact f number the equation gives. The
limits themselves are irrational and no lens has an f/23.6 to be set to, so quoting one on
the main screen names a place the photographer cannot go: `bestDepthFStop` and
`lastSharpFStop` both pick from `apertureStops()`, and so both follow the half or third
stop subdivision in settings. The exact figures stay in the details panel and the camera
editor, where they describe the body rather than offering a setting. A lens whose widest
aperture is already past the limit has no stop to name at all, and the notice says so.

Both offer the same way out, because both move together: ask for less sharpness and they
slide down the scale. On 35mm film at 100 mm and 10 m the depth peaks at f/16 (7.32–15.8 m)
and is back to its f/8 width by f/22; at f/25 the blur read-out passes 1 and the limits are
dropped, exactly where diffraction alone has spent the whole circle of confusion.

The scale layouts were measured off the published screenshots rather than guessed: the
focal length and blur scales fit a harmonic (linear in 1/value) model to within 1.6 px over
645 px, and the aperture scale fits a logarithmic one at 43.1 px per stop.

## Verification

`DofTest.kt` reproduces both published screenshots and the identities in Appendix C;
`StateTest.kt` pins the interaction contract — that focal length and subject distance are
the user's alone, that aperture and the depth of field limits each drive the other, and
that a dragged limit always lands on a selectable f stop and within the selected lens, that the limits straddle the subject at every distance the scale can reach, and that the auto-fitted window gives the depth of field its quarter of the height across every focal length, aperture and distance. The 35 mm film case (50 mm, f/4, focused at 4 ft) returns
near 3.78 ft, far 4.25 ft, hyperfocal 66.4 ft and diffraction blur 0.17; the Olympus OM-D
case (50 mm, f/4, focused at 10 m) returns 9.23 m and 10.9 m. Every figure in the details
panel matches the desktop screenshot digit for digit.

```
gradlew :app:testDebugUnitTest      # 100 tests
gradlew :app:assembleDebug
```

## Building

Requires the Android SDK (compileSdk 36) and a JDK 21 — the one bundled with Android
Studio works. `local.properties` points at the SDK.

## Credits

Depth of field equations from the DoF 4.0 reference manual by Jonathan M. Sachs,
Digital Light & Color. This is an independent reimplementation, not affiliated with or
endorsed by the author; it carries its own application id (`org.kutner.dofpro`).
