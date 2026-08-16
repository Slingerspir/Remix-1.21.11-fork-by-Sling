#version 120

uniform vec2 u_size;
uniform float u_radius;
uniform vec4 u_first_color;
uniform vec4 u_second_color;
uniform int u_direction;
uniform vec4 u_edges;

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
    if (alpha <= 0.005) discard;

    float t = u_direction == 1 ? v_texCoord.x : v_texCoord.y;
    vec4 color = mix(u_first_color, u_second_color, t);
    gl_FragColor = vec4(color.rgb, color.a * alpha);
}