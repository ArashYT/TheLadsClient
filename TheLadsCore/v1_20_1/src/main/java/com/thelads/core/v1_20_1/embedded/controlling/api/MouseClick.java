// Adapted from Controlling 26.3.3 by Jaredlll08 (MIT); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v1_20_1.embedded.controlling.api;

/** A mouse press or release on a key entry: position and button, which 1.20.1 hands to widgets as plain numbers. */
public record MouseClick(double x, double y, int button) {
}
