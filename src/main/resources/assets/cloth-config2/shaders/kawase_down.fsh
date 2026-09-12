#version 150

uniform sampler2D Sampler0;
uniform float Offset;
uniform vec2 Resolution;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // The destination framebuffer is already half-resolution. Texture coordinates stay in
    // the normalized 0..1 domain; scaling them here sampled outside the source image.
    vec2 uv = texCoord;
    vec2 halfpixel = Resolution * Offset;
    vec3 sum = texture(Sampler0, uv).rgb * 4.0;
    sum += texture(Sampler0, uv - halfpixel).rgb;
    sum += texture(Sampler0, uv + halfpixel).rgb;
    sum += texture(Sampler0, uv + vec2(halfpixel.x, -halfpixel.y)).rgb;
    sum += texture(Sampler0, uv - vec2(halfpixel.x, -halfpixel.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
