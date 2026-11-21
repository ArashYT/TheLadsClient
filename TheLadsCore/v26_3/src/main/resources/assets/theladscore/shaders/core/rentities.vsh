#version 330
#extension GL_ARB_separate_shader_objects : require

// Rentities: vanilla core/entity.vsh for one instanced draw of a whole entity model. The model part (bone) matrices of every
// instance sit in a texel buffer, 7 texels per bone: 4 for the pose matrix and 3 for the normal matrix (columns, w = 0).

#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include <minecraft:light.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec3 Normal;
layout(location = 3) in int Bone;
layout(location = 4) in vec4 Color;
layout(location = 5) in ivec2 UV1;
layout(location = 6) in ivec2 UV2;
layout(location = 7) in int InstBase;

uniform samplerBuffer RentitiesBones;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
layout(location = 2) out vec4 vertexPerFaceColorBack;
layout(location = 3) out vec4 vertexPerFaceColorFront;
#else
layout(location = 2) out vec4 vertexColor;
#endif

layout(location = 4) out vec4 lightMapColor;
layout(location = 5) out vec4 overlayColor;
layout(location = 6) out vec2 texCoord0;

void main() {
    int b = (InstBase + Bone) * 7;
    mat4 pose = mat4(texelFetch(RentitiesBones, b), texelFetch(RentitiesBones, b + 1),
                     texelFetch(RentitiesBones, b + 2), texelFetch(RentitiesBones, b + 3));
    mat3 normalMat = mat3(texelFetch(RentitiesBones, b + 4).xyz, texelFetch(RentitiesBones, b + 5).xyz,
                          texelFetch(RentitiesBones, b + 6).xyz);
    vec3 world = (pose * vec4(Position, 1.0)).xyz;
    vec3 n = normalMat * Normal;
    float nl = length(n);
    vec3 normal = nl > 0.0 ? n / nl : vec3(0.0, 1.0, 0.0);

    gl_Position = ProjMat * ModelViewMat * vec4(world, 1.0);

    sphericalVertexDistance = fog_spherical_distance(world);
    cylindricalVertexDistance = fog_cylindrical_distance(world);

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, Color);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, Color);
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, Color);
#endif

    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
