#version 330
#extension GL_ARB_separate_shader_objects : require

// Motion Blur (ScreenEffects26): this frame with BlendFactor of the last finished one kept.
uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform LadsMotion {
    float BlendFactor;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, BlendFactor), 1.0);
}
