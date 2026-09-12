#version 150

uniform sampler2D Sampler0;
uniform vec4 GlowColor;
uniform float GlowIntensity;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 srcColor = texture(Sampler0, texCoord0) * vertexColor;
    float a = srcColor.a;
    vec3 glow = GlowColor.rgb * GlowIntensity * a;
    fragColor = vec4(glow, a * GlowColor.a);
}
