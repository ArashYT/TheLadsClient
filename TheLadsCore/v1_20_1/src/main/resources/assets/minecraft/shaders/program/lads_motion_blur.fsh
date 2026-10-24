#version 150

// Motion Blur (ScreenEffects1201): this frame with BlendFactor of the last one kept.
uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;
uniform float BlendFactor;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 current = texture(DiffuseSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, BlendFactor), 1.0);
}
