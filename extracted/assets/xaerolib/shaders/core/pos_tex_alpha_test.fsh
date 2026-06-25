#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(std140) uniform DiscardAlphaBlock {
    uniform float DiscardAlpha;
};

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord0) * ColorModulator;
    if (fragColor.a <= DiscardAlpha) {
        discard;
    }
}
