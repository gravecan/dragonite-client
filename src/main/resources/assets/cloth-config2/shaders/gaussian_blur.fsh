#version 150

uniform sampler2D Sampler0;
uniform vec2 Resolution;
uniform vec2 Direction;
uniform float Radius;

in vec2 texCoord;
out vec4 fragColor;

float gaussianWeight(float x) {
    return exp(-0.5 * x * x);
}

void main() {
    vec3 color = vec3(0.0);
    float weightSum = 0.0;

    // A fixed 17-tap separable kernel is predictable across GPUs and far cheaper than
    // evaluating a two-dimensional Gaussian for every pixel of a near-fullscreen GUI.
    for (int i = -8; i <= 8; i++) {
        float normalized = float(i) / 8.0;
        float weight = gaussianWeight(normalized * 2.5);
        vec2 sampleUv = texCoord + Direction * Resolution * Radius * normalized;
        color += texture(Sampler0, clamp(sampleUv, vec2(0.0), vec2(1.0))).rgb * weight;
        weightSum += weight;
    }

    fragColor = vec4(color / max(weightSum, 0.0001), 1.0);
}
