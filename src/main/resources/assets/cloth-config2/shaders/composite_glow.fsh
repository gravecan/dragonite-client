#version 150



uniform sampler2D ColorBeforeSampler;

uniform sampler2D ColorAfterSampler;

uniform sampler2D DepthBeforeSampler;

uniform sampler2D DepthAfterSampler;

uniform vec2 Resolution;

uniform vec3 GlowColor;

uniform float GlowIntensity;

uniform float OutlineWidth;

uniform float EdgeDirections;

uniform float DepthEpsilon;

uniform float ColorEpsilon;



in vec2 texCoord;

out vec4 fragColor;



const float TAU = 6.28318530718;



bool isHandPixel(vec2 uv) {

    float dBefore = texture(DepthBeforeSampler, uv).r;

    float dAfter = texture(DepthAfterSampler, uv).r;

    float delta = dAfter - dBefore;

    if (abs(delta) <= DepthEpsilon) {

        return false;

    }

    vec4 before = texture(ColorBeforeSampler, uv);

    vec4 after = texture(ColorAfterSampler, uv);

    return length(after.rgb - before.rgb) > ColorEpsilon;

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

        for (float r = 1.0; r <= OutlineWidth; r += 1.0) {

            if (!isHandPixel(texCoord + dir * texel * r * 1.1)) {

                edgeFactor = max(edgeFactor, 1.0 - (r - 1.0) / OutlineWidth);

                break;

            }

        }

    }



    edgeFactor = pow(clamp(edgeFactor, 0.0, 1.0), 2.6);



    if (edgeFactor <= 0.02) {

        fragColor = after;

        return;

    }



    float edge = smoothstep(0.25, 0.92, edgeFactor);

    vec3 outline = GlowColor * GlowIntensity;

    vec3 rgb = mix(after.rgb, outline, edge);

    fragColor = vec4(rgb, after.a);

}


