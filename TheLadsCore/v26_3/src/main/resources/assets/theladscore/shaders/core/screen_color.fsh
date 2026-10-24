#version 330
#extension GL_ARB_separate_shader_objects : require

// Color Saturation (ScreenEffects26): ColorGrade's 3x4 matrix, then vibrance (more saturation for dull colors).
uniform sampler2D InSampler;

layout(std140) uniform LadsColor {
    vec4 RedRow;
    vec4 GreenRow;
    vec4 BlueRow;
    float Vibrance;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = vec4(texture(InSampler, texCoord).rgb, 1.0);
    vec3 graded = vec3(dot(RedRow, color), dot(GreenRow, color), dot(BlueRow, color));
    float luma = dot(graded, vec3(0.213, 0.715, 0.072));
    float chroma = clamp(max(graded.r, max(graded.g, graded.b)) - min(graded.r, min(graded.g, graded.b)), 0.0, 1.0);
    graded = mix(vec3(luma), graded, 1.0 + Vibrance * (1.0 - chroma));
    fragColor = vec4(clamp(graded, 0.0, 1.0), 1.0);
}
