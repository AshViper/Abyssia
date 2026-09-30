#version 150

uniform sampler2D FogSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(FogSampler, texCoord);
}
