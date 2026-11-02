#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 d = 0.75 / InSize;
    vec3 c = (texture(DiffuseSampler, texCoord + d).rgb
            + texture(DiffuseSampler, texCoord - d).rgb
            + texture(DiffuseSampler, texCoord + vec2(d.x, -d.y)).rgb
            + texture(DiffuseSampler, texCoord + vec2(-d.x, d.y)).rgb) * 0.25;
    float brightness = max(c.r, max(c.g, c.b));
    float chroma = brightness - min(c.r, min(c.g, c.b));
    float gate = smoothstep(0.59, 0.96, brightness) * (0.3 + smoothstep(0.1, 0.4, chroma) * 0.7);
    fragColor = vec4(c * gate, 1.0);
}
