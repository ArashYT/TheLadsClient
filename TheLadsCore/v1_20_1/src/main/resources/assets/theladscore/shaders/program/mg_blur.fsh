#version 150

// Separable box blur for the minigame fx "blur"; opaque output so the main target never goes transparent.
uniform sampler2D DiffuseSampler;
uniform vec2 BlurDir;
uniform float Radius;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec3 sum = vec3(0.0);
    float n = 0.0;
    for (float r = -Radius; r <= Radius; r += 1.0) {
        sum += texture(DiffuseSampler, texCoord + oneTexel * r * BlurDir).rgb;
        n += 1.0;
    }
    fragColor = vec4(sum / n, 1.0);
}
