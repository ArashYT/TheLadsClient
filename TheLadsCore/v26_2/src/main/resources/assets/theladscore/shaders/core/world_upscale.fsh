#version 330

#moj_import <theladscore:world_upscale.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
#ifdef LADS_SHARP
    fragColor = ladsSharp(InSampler, texCoord, texel);
#else
    fragColor = ladsSmooth(InSampler, texCoord, texel);
#endif
}
