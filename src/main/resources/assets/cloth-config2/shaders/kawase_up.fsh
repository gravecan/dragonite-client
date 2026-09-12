#version 150

uniform sampler2D Sampler0;
uniform float Offset;
uniform vec2 Resolution;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // Both ping-pong targets cover the complete screen in normalized coordinates.
    vec2 uv = texCoord;
    vec2 halfpixel = Resolution * Offset;

    vec3 sum = vec3(0.0);
    sum += texture(Sampler0, uv + vec2(-halfpixel.x * 2.0, 0.0)).rgb;
    sum += texture(Sampler0, uv + vec2(-halfpixel.x, halfpixel.y)).rgb * 2.0;
    sum += texture(Sampler0, uv + vec2(0.0, halfpixel.y * 2.0)).rgb;
    sum += texture(Sampler0, uv + vec2(halfpixel.x, halfpixel.y)).rgb * 2.0;
    sum += texture(Sampler0, uv + vec2(halfpixel.x * 2.0, 0.0)).rgb;
    sum += texture(Sampler0, uv + vec2(halfpixel.x, -halfpixel.y)).rgb * 2.0;
    sum += texture(Sampler0, uv + vec2(0.0, -halfpixel.y * 2.0)).rgb;
    sum += texture(Sampler0, uv + vec2(-halfpixel.x, -halfpixel.y)).rgb * 2.0;

    fragColor = vec4(sum / 12.0, 1.0);
}
