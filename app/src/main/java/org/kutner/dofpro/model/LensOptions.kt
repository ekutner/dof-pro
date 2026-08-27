package org.kutner.dofpro.model

/** How the lens in use is being driven: aperture increments, and any teleconverter. */

/** Aperture scale subdivision, and the increments a depth of field limit snaps to. */
enum class ApertureStep(val label: String, val perStop: Int) {
    HALF("1/2", 2),
    THIRD("1/3", 3),
}

enum class Teleconverter(val label: String, val factor: Double) {
    NONE("1.0", 1.0),
    X14("1.4", 1.4),
    X2("2.0", 2.0),
}
