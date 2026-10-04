// Lads Better Resolution: rebuilds the world image (rendered at its own resolution) at native size.
// Imported by the 26.2 and 26.3 world_upscale.fsh, and by 1.8.9's RenderScale189 (GLSL 1.20, texture defined as texture2D).
// world: the world image, sampled bilinearly; uv: this pixel; texel: one world pixel in uv units.

float ladsLuma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

// One row of the Catmull-Rom filter: the middle two weights share one bilinear tap.
vec4 ladsRow(sampler2D world, float y, vec2 x0, vec2 x12, vec2 x3, vec3 w) {
    return texture(world, vec2(x0.x, y)) * w.x + texture(world, vec2(x12.x, y)) * w.y + texture(world, vec2(x3.x, y)) * w.z;
}

// Smooth: Catmull-Rom (bicubic) reconstruction, kept within the four world pixels around this one so it never rings:
// continuous like Linear without its softness. Along strong edges two more taps blend along (never across) the edge,
// softening the stair steps of an upscaled edge and keeping lines steady as the view moves.
vec4 ladsSmooth(sampler2D world, vec2 uv, vec2 texel) {
    vec2 p = uv / texel - 0.5;
    vec2 f = fract(p);
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 base = floor(p) + 0.5;
    vec2 t0 = (base - 1.0) * texel, t12 = (base + w2 / w12) * texel, t3 = (base + 2.0) * texel;
    vec3 wx = vec3(w0.x, w12.x, w3.x);
    vec4 color = ladsRow(world, t0.y, t0, t12, t3, wx) * w0.y + ladsRow(world, t12.y, t0, t12, t3, wx) * w12.y
        + ladsRow(world, t3.y, t0, t12, t3, wx) * w3.y;
    vec4 a = texture(world, base * texel);
    vec4 b = texture(world, (base + vec2(1.0, 0.0)) * texel);
    vec4 c = texture(world, (base + vec2(0.0, 1.0)) * texel);
    vec4 d = texture(world, (base + 1.0) * texel);
    color = clamp(color, min(min(a, b), min(c, d)), max(max(a, b), max(c, d)));
    float la = ladsLuma(a.rgb), lb = ladsLuma(b.rgb), lc = ladsLuma(c.rgb), ld = ladsLuma(d.rgb);
    vec2 gradient = vec2(lb + ld - la - lc, lc + ld - la - lb);
    float edge = length(gradient);
    if (edge < 0.15) return color;
    vec2 along = vec2(-gradient.y, gradient.x) / edge * texel * 0.75;
    vec4 line = texture(world, uv + along) + texture(world, uv - along);
    return mix(color, (color + line) / 3.0, min((edge - 0.15) * 2.0, 0.5));
}

// Sharp: world pixels held flat with a narrow blend between them (no blur, none of Nearest's stepping),
// then sharpened by how much detail is left: flat areas and hard edges get little, so no halos or boosted noise.
// Clamped to the neighbourhood, so it never overshoots.
vec4 ladsSharp(sampler2D world, vec2 uv, vec2 texel) {
    vec2 p = uv / texel - 0.5;
    vec2 f = fract(p);
    vec4 center = texture(world, (floor(p) + 0.5 + f * f * (3.0 - 2.0 * f)) * texel);
    vec3 n = texture(world, uv + vec2(0.0, texel.y)).rgb;
    vec3 s = texture(world, uv - vec2(0.0, texel.y)).rgb;
    vec3 e = texture(world, uv + vec2(texel.x, 0.0)).rgb;
    vec3 w = texture(world, uv - vec2(texel.x, 0.0)).rgb;
    vec3 lo = min(center.rgb, min(min(n, s), min(e, w)));
    vec3 hi = max(center.rgb, max(max(n, s), max(e, w)));
    float amount = 0.8 * (1.0 - ladsLuma(hi - lo));
    vec3 sharpened = center.rgb + amount * (center.rgb - (n + s + e + w) * 0.25);
    return vec4(clamp(sharpened, lo, hi), center.a);
}
