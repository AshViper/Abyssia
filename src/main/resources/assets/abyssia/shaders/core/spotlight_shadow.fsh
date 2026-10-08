#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

// only the depth is kept; see-through pixels of cutout blocks let the light through
void main() {
    if (texture(Sampler0, texCoord0).a < 0.5) {
        discard;
    }
    fragColor = vec4(1.0);
}
