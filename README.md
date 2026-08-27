# DoF Pro

A depth of field calculator for Android, for photographers who need the answer to be true
rather than merely conventional.

Most calculators still work the way the film era did: they ignore diffraction, and they
judge sharpness against a fixed circle of confusion derived from an 8×10 print. That was a
reasonable simplification when film resolved less than the lens did. On a 61 MP sensor it
is wrong often enough to cost you frames — it will happily tell you that f/22 buys more
depth of field when on your camera it buys less.

DoF Pro accounts for **diffraction as well as focus blur**, and works out what counts as
sharp from **your sensor** and **where the picture will actually be seen**.

## Setting up

Tap the camera icon at the top left. Three things to choose:

- **Camera** — how much detail was recorded: sensor size and pixel count.
- **Lens** — confines the focal length and aperture scales to what that lens actually has.
- **Viewed on** — where the picture will be seen, and from how far.

The last one is separate from the camera on purpose, because they are separate questions.
A camera settles how much detail exists; the viewing conditions settle how much of it
anyone can see. The same frame is critically sharp on a phone and visibly soft as a metre
wide print, and one body shoots both. Choose a print size, a screen, *Pixel level* to judge
at 100%, or enter a circle of confusion outright.

## The screen

Three scales: **focal length**, **aperture**, and **distance**. The distance scale carries
two sets of graduations — blur down its left side, distance down its right.

| Marker | Means |
|---|---|
| **blue** | your subject |
| **red** (two) | the near and far limits of acceptable sharpness |
| **green** | the hyperfocal distance |

The hyperfocal distance is also written above the scale, since at ordinary apertures the
green line is off the top of the view. The eye beside it opens the full figures: limits,
field of view, magnification, circle of confusion, diffraction limit.

## Working with it

- **Drag the blue line** to your subject, or tap the distance to type it.
- **Change the aperture** and the red lines move.
- **Drag a red line** to where you need it and the aperture is chosen for you. It lands
  only on stops your lens actually has, so the limits jump rather than slide.
- **Tap the hyperfocal reading** to focus there.
- **Pinch the distance scale** to zoom.

Every marker you can drag travels with your finger, and hands over to the scale once it
reaches the end of its travel.

## Reading the blur scale

The left side of the distance scale counts just-resolvable details, so **1.0 is the finest
detail your viewing target can show at all**. The red lines sit at that target's allowable
blur — two details by default, which is why the limits usually read 2.0.

The blur figure above the subject is the blur *at* the subject, which is pure diffraction:
focus blur is zero exactly where you focused. It is the sharpest the picture can be at
this aperture.

## When the limits disappear

Two notices can appear along the bottom, and neither is an error.

**"Most depth of field at f/x"** — you have reached the aperture that gives the most depth
of field. Stopping down past it costs depth rather than buying it, because diffraction is
now growing faster than focus blur is shrinking. This is the number to reach for when
depth of field is what matters.

**"Reduced sharpness past f/x"** — diffraction alone has now spent the whole circle of
confusion, so nothing meets the sharpness criterion and there are genuinely no limits to
draw. High resolution bodies reach this early: a 61 MP full frame judged at pixel level is
diffraction limited around f/5.6.

Both notices offer a way out through **Viewing**, because both walls are placed by the
circle of confusion. Asking for slightly less sharpness — a larger allowable blur, or a
viewing target that is not being judged at 100% — moves both of them further down the
scale.

## Focus stacking

The stacked-squares button in the header turns it on. The distance column then counts the
frames needed to cover everything from the closest sharp point out to infinity; tap that
count to list where to focus each one, nearest first. How far consecutive frames overlap
is set in Settings.

## Your gear

Cameras, lenses and viewing targets are three editable lists, reached from Settings or from
the *Add or edit these lists* link in Setup. Everything shipped is a starting point: rename
it, retune it, or delete it. Long-press an entry to select several and remove them together.

A camera can also be **looked up by name** rather than typed in, which fills in its real
sensor size and resolution — worth doing, because APS-C is not one size and a full frame
sensor is rarely exactly 36×24 mm.

## Settings

Light or dark theme, metric or imperial, and whether the aperture ring moves in third or
half stops. Within either unit system the unit suits the distance, so metres give way to
centimetres as the subject comes closer.

## Credits

Both the user interface and logic of this app were heavily inspired by the work of 
Jonathan Sachs and his excellent **DoF 4.0 for Windows** available at 
Digital Light & Color <https://www.dl-c.com/DoF/>.  
This is an independent reimplementation, not affiliated with or endorsed by the original author.
