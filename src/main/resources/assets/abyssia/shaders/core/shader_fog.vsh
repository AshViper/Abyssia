#version 150

in vec3 Position;

out vec2 texCoord;

// Full-screen quad given in clip space.
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
