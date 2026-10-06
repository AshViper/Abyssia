#version 150

#moj_import <fog.glsl>

// The light a spotlight adds to the blocks it shines on (see SpotlightProjector). Ported from AshWarfare's flashlight shader.

uniform sampler2D Sampler0;
// the depth of the blocks as seen from the light
uniform sampler2D ShadowMap;

// from positions relative to the camera to the light's view
uniform mat4 LightViewProj;
// relative to the camera
uniform vec3 LightPosition;
uniform vec3 LightDirection;
// already as bright as the light is
uniform vec3 LightColor;
uniform float LightRange;
uniform float LightHalfAngle;
// where the light's view starts and ends, in blocks
uniform vec2 ShadowClip;
uniform float LightFogStart;
uniform float LightFogEnd;

in vec3 position;
in vec3 normal;
in vec4 vertexColor;
in vec2 texCoord0;
in float vertexDistance;

out vec4 fragColor;

// very bright up close, dimmer from about a quarter of the range, down to nothing at the range
float distanceFalloff(float fromLight) {
    float window = clamp(1.0 - pow(fromLight / LightRange, 4.0), 0.0, 1.0);
    float halfDistance = LightRange * 0.28;
    return window * window / (1.0 + (fromLight / halfDistance) * (fromLight / halfDistance));
}

// a bright hotspot in the middle within a dimmer spill that fades out to the edge
float coneFalloff(float offAxis) {
    if (offAxis >= 1.0) {
        return 0.0;
    }
    float hotspot = exp(-(offAxis / 0.3) * (offAxis / 0.3) / 2.0);
    float spill = 1.0 - smoothstep(0.4, 1.0, offAxis);
    return 0.7 * hotspot * spill + 0.3 * spill;
}

// from the depth buffer's 0..1 to blocks from the light
float lightDistance(float depth) {
    float z = depth * 2.0 - 1.0;
    return 2.0 * ShadowClip.x * ShadowClip.y / (ShadowClip.y + ShadowClip.x - z * (ShadowClip.y - ShadowClip.x));
}

// how much of the light reaches the point: 0 behind something closer to the light, softened over a few shadow-map pixels
float lightReaching(vec3 point, float fromLight) {
    vec4 clip = LightViewProj * vec4(point + normal * (0.02 + fromLight * 0.0015), 1.0);
    vec3 ndc = clip.xyz / clip.w;
    vec2 uv = ndc.xy * 0.5 + 0.5;
    float pointDistance = lightDistance(ndc.z * 0.5 + 0.5) - (0.02 + fromLight * 0.002);
    vec2 texel = 1.0 / vec2(textureSize(ShadowMap, 0));
    float reaching = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float blocker = lightDistance(texture(ShadowMap, uv + vec2(x, y) * texel * 1.5).r);
            reaching += pointDistance <= blocker ? 1.0 : 0.0;
        }
    }
    return reaching / 9.0;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    vec3 toPoint = position - LightPosition;
    float fromLight = length(toPoint);
    vec3 direction = toPoint / max(fromLight, 0.0001);
    float cone = coneFalloff(acos(clamp(dot(direction, LightDirection), -1.0, 1.0)) / LightHalfAngle);
    float facing = dot(normal, -direction);
    if (cone <= 0.0 || facing <= 0.0 || fromLight >= LightRange) {
        discard;
    }
    float light = cone * distanceFalloff(fromLight) * facing * lightReaching(position, fromLight)
            * linear_fog_fade(vertexDistance, LightFogStart, LightFogEnd);
    // added to what is already there
    fragColor = vec4(color.rgb * LightColor * light, 0.0);
}
