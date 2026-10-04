#version 330
#extension GL_ARB_separate_shader_objects : require

#include <theladscore:world_upscale.glsl>

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
#ifdef LADS_SHARP
    fragColor = ladsSharp(InSampler, texCoord, texel);
#else
    fragColor = ladsSmooth(InSampler, texCoord, texel);
#endif
}
