package com.thelads.ladsapi.api;

/** One point of a camera spline: position in blocks, yaw and pitch in degrees (PROTOCOL.md section 11). */
public record CameraPoint(double x, double y, double z, double yaw, double pitch) {}
