#version 150

#moj_import <theladscore:world_upscale.glsl>

uniform sampler2D DiffuseSampler;
// 0 Smooth, 1 Sharp, 2 plain bilinear/nearest (the sampler's own filter)
uniform float Mode;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(DiffuseSampler, 0));
    if (Mode > 1.5) fragColor = texture(DiffuseSampler, texCoord0);
    else if (Mode > 0.5) fragColor = ladsSharp(DiffuseSampler, texCoord0, texel);
    else fragColor = ladsSmooth(DiffuseSampler, texCoord0, texel);
}
