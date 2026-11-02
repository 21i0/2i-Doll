#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 d = vec2(0.0, 1.0) / InSize;
    vec3 c = texture(DiffuseSampler, texCoord).rgb * 0.227027;
    c += texture(DiffuseSampler, texCoord + d * 1.384615).rgb * 0.316216;
    c += texture(DiffuseSampler, texCoord - d * 1.384615).rgb * 0.316216;
    c += texture(DiffuseSampler, texCoord + d * 3.230769).rgb * 0.070270;
    c += texture(DiffuseSampler, texCoord - d * 3.230769).rgb * 0.070270;
    fragColor = vec4(c, 1.0);
}
