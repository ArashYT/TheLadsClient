#version 330

#moj_import <fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator;
}
