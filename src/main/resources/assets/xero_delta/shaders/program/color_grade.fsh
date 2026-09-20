#version 150

uniform sampler2D DiffuseSampler;
uniform float Saturation;
uniform float Contrast;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 desaturated = mix(vec3(luminance), source.rgb, Saturation);
    vec3 graded = (desaturated - vec3(0.5)) * Contrast + vec3(0.5);
    fragColor = vec4(clamp(graded, 0.0, 1.0), source.a);
}