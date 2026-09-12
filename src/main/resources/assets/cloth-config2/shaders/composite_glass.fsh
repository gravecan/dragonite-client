#version 150



uniform sampler2D ColorBeforeSampler;

uniform sampler2D ColorAfterSampler;

uniform sampler2D DepthBeforeSampler;

uniform sampler2D DepthAfterSampler;

uniform sampler2D BlurSampler;

uniform vec2 Resolution;

uniform vec3 Tint;

uniform float DistortStrength;

uniform float GlassBrightness;

uniform float EdgeRadius;

uniform float EdgeDirections;

uniform float FresnelPower;

uniform float EdgeGlow;

uniform float DepthEpsilon;

uniform float ColorEpsilon;



in vec2 texCoord;

out vec4 fragColor;



const float TAU = 6.28318530718;



bool isHandPixel(vec2 uv) {

    vec4 before = texture(ColorBeforeSampler, uv);

    vec4 after = texture(ColorAfterSampler, uv);

    float dBefore = texture(DepthBeforeSampler, uv).r;

    float dAfter = texture(DepthAfterSampler, uv).r;

    float delta = dAfter - dBefore;

    bool depthHand = abs(delta) > DepthEpsilon;

    bool colorHand = length(after.rgb - before.rgb) > ColorEpsilon;

    return depthHand && colorHand;

}



void main() {

    vec4 after = texture(ColorAfterSampler, texCoord);



    if (!isHandPixel(texCoord)) {

        fragColor = after;

        return;

    }



    vec2 texel = 1.0 / Resolution;

    float edgeFactor = 0.0;



    for (float a = 0.0; a < TAU; a += TAU / EdgeDirections) {

        vec2 dir = vec2(cos(a), sin(a));

        for (float r = 1.0; r <= EdgeRadius; r += 1.0) {

            if (!isHandPixel(texCoord + dir * texel * r * 1.5)) {

                edgeFactor = max(edgeFactor, 1.0 - (r - 1.0) / EdgeRadius);

                break;

            }

        }

    }

    edgeFactor /= EdgeDirections;



    float fresnel = pow(clamp(edgeFactor, 0.0, 1.0), max(FresnelPower, 0.35));

    fresnel = clamp(fresnel, 0.0, 1.0);



    vec2 screenCenter = vec2(0.5);

    vec2 distDir = normalize(texCoord - screenCenter);

    vec2 distortedUV = clamp(texCoord + distDir * fresnel * DistortStrength, 0.0, 1.0);



    vec3 glassBg = texture(BlurSampler, distortedUV).rgb * Tint * GlassBrightness;

    vec3 highlight = vec3(EdgeGlow) * fresnel;

    vec3 rgb = mix(after.rgb, glassBg + highlight, fresnel * 0.88);



    fragColor = vec4(rgb, after.a);

}


