#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 BlurDir;
uniform float BlurRadius;
uniform float AberrationStrength;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 pixel = vec2(1.0) / max(InSize, vec2(1.0));
    vec2 stepOffset = BlurDir * pixel * BlurRadius;

    // Three bilinear samples form a compact separable Gaussian kernel. Running
    // it horizontally and vertically gives a visible blur with only two passes.
    vec4 blurred = texture(DiffuseSampler, texCoord) * 0.50;
    blurred += texture(DiffuseSampler,
        clamp(texCoord + stepOffset, vec2(0.0), vec2(1.0))) * 0.25;
    blurred += texture(DiffuseSampler,
        clamp(texCoord - stepOffset, vec2(0.0), vec2(1.0))) * 0.25;

    float edge = smoothstep(0.12, 0.72, distance(texCoord, vec2(0.5)) * 1.35);
    vec2 rgbOffset = vec2(pixel.x * (2.0 + edge * 4.0) * AberrationStrength, 0.0);
    float splitRed = texture(DiffuseSampler,
        clamp(texCoord + rgbOffset, vec2(0.0), vec2(1.0))).r;
    float splitBlue = texture(DiffuseSampler,
        clamp(texCoord - rgbOffset, vec2(0.0), vec2(1.0))).b;
    float mixAmount = 0.28 * AberrationStrength;
    fragColor = vec4(mix(blurred.r, splitRed, mixAmount), blurred.g,
        mix(blurred.b, splitBlue, mixAmount), blurred.a);
}