#version 150

uniform sampler2D DepthSampler;
uniform mat4 InvProjMat;
uniform float FogNear;
uniform float FogFar;
uniform vec4 FogTint;

in vec2 texCoord;

out vec4 fragColor;

// Vanilla's spherical linear fog, rebuilt from the depth buffer: colour plus how much of it covers the pixel.
void main() {
    float depth = texture(DepthSampler, texCoord).r;
    vec4 view = InvProjMat * vec4(vec3(texCoord, depth) * 2.0 - 1.0, 1.0);
    float dist = depth >= 1.0 ? FogFar : length(view.xyz / view.w);
    float fog = dist <= FogNear ? 0.0 : dist < FogFar ? smoothstep(FogNear, FogFar, dist) : 1.0;
    fragColor = vec4(FogTint.rgb, fog * FogTint.a);
}
