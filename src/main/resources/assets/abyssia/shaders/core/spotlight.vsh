#version 150

#moj_import <fog.glsl>

// The blocks lit again by a spotlight (see SpotlightProjector), positions relative to the camera like the light's.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int LightFogShape;

out vec3 position;
out vec3 normal;
out vec4 vertexColor;
out vec2 texCoord0;
out float vertexDistance;

void main() {
    position = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    normal = Normal;
    vertexColor = Color;
    texCoord0 = UV0;
    vertexDistance = fog_distance(position, LightFogShape);
}
