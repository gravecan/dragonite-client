#version 150

uniform sampler2D Sampler0;
uniform float Saturation;
uniform vec2 Size;
uniform float CornerRadius;
uniform vec4 TintColor;

in vec2 texCoord;
in vec2 localCoord;
out vec4 fragColor;

float rdist(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec3 color = texture(Sampler0, clamp(texCoord, vec2(0.0), vec2(1.0))).rgb;

    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(lum), color, Saturation);
    color = mix(color, TintColor.rgb, TintColor.a);

    float alpha = 1.0;
    if (CornerRadius > 0.0 && Size.x > 0.0 && Size.y > 0.0) {
        // localCoord is 0..1 across the drawn panel quad (not the sample UVs).
        vec2 p = (localCoord - 0.5) * Size;
        float d = rdist(p, Size * 0.5, CornerRadius);
        float aa = max(fwidth(d), 0.75);
        alpha = 1.0 - smoothstep(-aa, aa, d);
    }

    fragColor = vec4(color, alpha);
}
