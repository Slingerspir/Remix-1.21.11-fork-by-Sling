#version 150

uniform vec2 u_viewport;
uniform vec2 u_center;
uniform vec2 u_size;
uniform vec4 u_color;
uniform float u_radius;
uniform float u_feather;

out vec4 fragColor;

void main() {
    // gl_FragCoord 是像素坐标 -> NDC
    vec2 ndc = gl_FragCoord.xy / u_viewport * 2.0 - 1.0;

    vec2 uv = ndc - u_center;
    vec2 halfSize = u_size * 0.5;

    // 到矩形边缘的有符号距离（内部负、外部正）
    vec2 d = abs(uv) - halfSize;
    float dist = length(max(d, vec2(0.0))) + min(max(d.x, d.y), 0.0);

    // 环形羽化：边缘 (dist≈0) 最亮，向内/向外按高斯衰减
    float glow = exp(-dist * dist / (2.0 * u_radius * u_radius));
    float feather = smoothstep(u_feather, 0.0, dist);

    float alpha = glow * feather * u_color.a;
    if (alpha < 0.003) discard;

    fragColor = vec4(u_color.rgb, alpha);
}
