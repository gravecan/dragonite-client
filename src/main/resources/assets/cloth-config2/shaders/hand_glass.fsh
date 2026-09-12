#version 150

uniform sampler2D Sampler0;
uniform sampler2D BlurredSampler;
uniform vec4 Multiplier;
uniform vec2 ViewOffset;
uniform vec2 Resolution;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 srcColor = texture(Sampler0, texCoord0) * vertexColor;
    vec2 pos = gl_FragCoord.xy + ViewOffset;
    vec2 blurredUv = vec2(pos.x / Resolution.x, 1.0 - (pos.y / Resolution.y));
    fragColor = texture(BlurredSampler, blurredUv) * Multiplier * srcColor.a;
}
