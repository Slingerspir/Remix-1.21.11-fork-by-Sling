#version 120

uniform vec2 u_size;
uniform float u_radius;
uniform float u_border_size;
uniform vec4 u_color;

varying vec2 v_texCoord;

float roundedRectSDF(vec2 pos, vec2 size, float radius) {
    vec2 halfSize = size * 0.5;
    vec2 d = abs(pos) - halfSize + radius;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
}

void main() {
    vec2 pos = v_texCoord * u_size - u_size * 0.5;
    float maxRadius = min(u_size.x, u_size.y) * 0.5;
    float r = min(u_radius, maxRadius);
    float dist = roundedRectSDF(pos, u_size, r);
    float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
    float borderDist = roundedRectSDF(pos, u_size, r + u_border_size);
    float borderAlpha = 1.0 - smoothstep(0.0, 1.0, borderDist);
    float finalAlpha = borderAlpha - alpha;
    if (finalAlpha <= 0.005) discard;
    gl_FragColor = vec4(u_color.rgb, u_color.a * finalAlpha);
}