#version 150

// Color Saturation (ScreenEffects1201): ColorGrade's 3x4 matrix, then vibrance (more saturation for dull colors).
uniform sampler2D DiffuseSampler;
uniform vec4 RedRow;
uniform vec4 GreenRow;
uniform vec4 BlueRow;
uniform float Vibrance;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = vec4(texture(DiffuseSampler, texCoord).rgb, 1.0);
    vec3 graded = vec3(dot(RedRow, color), dot(GreenRow, color), dot(BlueRow, color));
    float luma = dot(graded, vec3(0.213, 0.715, 0.072));
    float chroma = clamp(max(graded.r, max(graded.g, graded.b)) - min(graded.r, min(graded.g, graded.b)), 0.0, 1.0);
    graded = mix(vec3(luma), graded, 1.0 + Vibrance * (1.0 - chroma));
    fragColor = vec4(clamp(graded, 0.0, 1.0), 1.0);
}
