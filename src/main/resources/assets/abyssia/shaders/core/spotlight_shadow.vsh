#version 150

// The blocks as seen from a spotlight, for its shadow map (see SpotlightProjector)

in vec3 Position;
in vec2 UV0;

uniform vec3 ChunkOffset;
uniform mat4 LightViewProj;

out vec2 texCoord0;

void main() {
    gl_Position = LightViewProj * vec4(Position + ChunkOffset, 1.0);
    texCoord0 = UV0;
}
