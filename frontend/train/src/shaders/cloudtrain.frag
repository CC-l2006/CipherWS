#version 300 es
precision highp float;

// ============================================================
// 云间列车 · Up in the CloudSea
// 片元着色器（Fragment Shader）
// 基于 mdb 在 ShaderToy 的作品《up in the cloud sea》(Ndc3zl)
// 并经 Tianxiu Zhou 合并为单 pass 版本后二次开发：
//   - 将 iChannel0 蓝噪声纹理替换为完全程序化的 hash/值噪声
//   - 新增可实时调节的 uniform 参数
//   - 全部画面由数学公式实时生成，不使用任何图片或模型素材
// ============================================================

out vec4 outColor;

uniform vec2  uResolution;    // 画布分辨率（像素）
uniform float uTime;          // 自页面加载起经过的时间（秒）
uniform float uOpeningDur;    // 开场时长（秒）
uniform float uSpeed;         // 行进速度（云/桥视差滚动速度倍数）
uniform float uUndulation;    // 云层起伏（云层高度起伏幅度倍数）
uniform float uNoiseDetail;   // 噪声细节（fbm 迭代层数, 1..8）
uniform float uExposure;      // 曝光亮度
uniform float uZoom;          // 视角缩放
uniform vec2  uMouse;         // 鼠标（归一化 0..1，未按下时为 (-1,-1)）

#define PI 3.141592653589793

// ---------- 程序化噪声（替代蓝噪声纹理） ----------

// 2D 哈希，生成 [0,1) 随机值
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 值噪声：对四个角点做五次 Hermite 插值
float noise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    // 五次 Hermite 平滑（C2 连续）
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);

    float a = hash21(i + vec2(0.0, 0.0));
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    return a + (b - a) * u.x + (c - a) * u.y + (a - b - c + d) * u.x * u.y;
}

// 分形布朗运动（fbm）——分层叠加噪声，衰减 0.7
float fbm(vec2 x, int octaves) {
    float amp   = 1.0;
    float tot   = 1.0;
    float sum   = noise(x);
    for (int i = 1; i < 8; i++) {
        if (i >= octaves) break;
        x   *= 2.0;
        amp *= 0.7;
        tot += amp;
        sum += amp * noise(x);
    }
    return sum / tot;
}

// 另一种 fbm（衰减 0.9，用于机车烟雾）
float fbm2(vec2 x, int octaves) {
    float amp   = 1.0;
    float tot   = 1.0;
    float sum   = noise(x);
    for (int i = 1; i < 8; i++) {
        if (i >= octaves) break;
        x   *= 2.0;
        amp *= 0.9;
        tot += amp;
        sum += amp * noise(x);
    }
    return sum / tot;
}

float box(vec2 uv, float x1, float x2, float y1, float y2) {
    float c = 1.0;
    c *= 1.0 - step(x1, uv.x);
    c *= step(x2, uv.x);
    c *= 1.0 - step(y1, uv.y);
    c *= step(y2, uv.y);
    return c;
}

#define dot2(v) dot(v, v)

// 前景云层
vec4 foreground(vec2 uv, float t, int detail) {
    float midlevel, h, disp, dist;
    vec2 uv2;

    uv.y -= 0.2;

    // c14
    midlevel = -0.1;
    disp = 1.7 * uUndulation;
    dist = 1.0;
    uv2 = uv + vec2(t / dist + 40.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.12) return vec4(0.43, 0.32, 0.31, 1.0);
    if (uv.y < h + midlevel - 0.08) return vec4(0.55, 0.42, 0.41, 1.0);
    if (uv.y < h + midlevel - 0.04) return vec4(0.66, 0.42, 0.40, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.77, 0.48, 0.46, 1.0);

    // c13
    midlevel = 0.05;
    disp = 1.7 * uUndulation;
    dist = 2.0;
    uv2 = uv + vec2(t / dist + 38.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.10) return vec4(0.95, 0.66, 0.48, 1.0);
    if (uv.y < h + midlevel - 0.04) return vec4(0.98, 0.76, 0.64, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.95, 0.80, 0.77, 1.0);

    return vec4(0.95, 0.80, 0.77, 0.0);
}

// 背景云层（由远及近若干层，视差不同）
vec4 background(vec2 uv, float t, int detail) {
    float midlevel, h, disp, dist;
    vec2 uv2;

    // c12
    midlevel = 0.30; disp = 0.9 * uUndulation;  dist = 10.0;
    uv2 = uv + vec2(t / dist + 32.5, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.14) return vec4(0.48, 0.19, 0.20, 1.0);
    if (uv.y < h + midlevel - 0.10) return vec4(0.68, 0.28, 0.19, 1.0);
    if (uv.y < h + midlevel - 0.07) return vec4(0.88, 0.38, 0.24, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.95, 0.45, 0.30, 1.0);

    // c11
    midlevel = 0.35; disp = 1.0 * uUndulation;  dist = 15.0;
    uv2 = uv + vec2(t / dist + 30.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.04) return vec4(0.98, 0.76, 0.64, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.95, 0.80, 0.77, 1.0);

    // c10
    midlevel = 0.35; disp = 3.5 * uUndulation;  dist = 20.0;
    uv2 = uv + vec2(t / dist + 27.5, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.12) return vec4(0.43, 0.32, 0.31, 1.0);
    if (uv.y < h + midlevel - 0.08) return vec4(0.55, 0.42, 0.41, 1.0);
    if (uv.y < h + midlevel - 0.04) return vec4(0.66, 0.42, 0.40, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.77, 0.48, 0.46, 1.0);

    // c9
    midlevel = 0.45; disp = 2.0 * uUndulation;  dist = 25.0;
    uv2 = uv + vec2(t / dist + 23.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.04) return vec4(0.98, 0.57, 0.36, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(1.00, 0.62, 0.44, 1.0);

    // c8
    midlevel = 0.50; disp = 2.3 * uUndulation;  dist = 30.0;
    uv2 = uv + vec2(t / dist + 20.5, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.12) return vec4(0.41, 0.27, 0.27, 1.0);
    if (uv.y < h + midlevel - 0.08) return vec4(0.53, 0.35, 0.32, 1.0);
    if (uv.y < h + midlevel - 0.04) return vec4(0.80, 0.24, 0.17, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.99, 0.29, 0.20, 1.0);

    // c7
    midlevel = 0.50; disp = 2.5 * uUndulation;  dist = 35.0;
    uv2 = uv + vec2(t / dist + 18.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.10) return vec4(0.88, 0.38, 0.24, 1.0);
    if (uv.y < h + midlevel - 0.05) return vec4(0.98, 0.42, 0.28, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(1.00, 0.48, 0.35, 1.0);

    // c6
    midlevel = 0.60; disp = 2.0 * uUndulation;  dist = 40.0;
    uv2 = uv + vec2(t / dist + 18.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.10) return vec4(0.95, 0.66, 0.48, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(1.00, 0.76, 0.60, 1.0);

    // c5
    midlevel = 0.75; disp = 3.5 * uUndulation;  dist = 45.0;
    uv2 = uv + vec2(t / dist + 15.5, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.20) return vec4(1.00, 0.55, 0.33, 1.0);
    if (uv.y < h + midlevel - 0.15) return vec4(0.98, 0.50, 0.24, 1.0);
    if (uv.y < h + midlevel - 0.10) return vec4(0.90, 0.55, 0.40, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(1.00, 0.62, 0.44, 1.0);

    // c4
    midlevel = 0.70; disp = 2.7 * uUndulation;  dist = 50.0;
    uv2 = uv + vec2(t / dist + 12.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.04) return vec4(0.73, 0.36, 0.30, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.80, 0.40, 0.34, 1.0);

    // c3
    midlevel = 0.80; disp = 2.7 * uUndulation;  dist = 60.0;
    uv2 = uv + vec2(t / dist + 9.5, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.10) return vec4(0.93, 0.58, 0.35, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(1.00, 0.76, 0.60, 1.0);

    // c2
    midlevel = 0.90; disp = 3.0 * uUndulation; dist = 70.0;
    uv2 = uv + vec2(t / dist + 7.0, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.10) return vec4(0.56, 0.25, 0.22, 1.0);
    if (uv.y < h + midlevel - 0.05) return vec4(0.60, 0.30, 0.27, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(0.74, 0.35, 0.30, 1.0);

    // c1（远山）
    midlevel = 1.00; disp = 5.0 * uUndulation; dist = 100.0;
    uv2 = uv + vec2(t / dist + 3.5, 0.0);
    h = (fbm(uv2, detail) - 0.5) * disp;
    if (uv.y < h + midlevel - 0.10) return vec4(0.92, 0.85, 0.82, 1.0);
    if (uv.y < h + midlevel - 0.00) return vec4(1.00, 0.94, 0.91, 1.0);

    return vec4(0.58, 0.70, 1.0, 1.0); // 天空
}

// 主渲染：拼合云海、列车、桥、烟雾
vec3 genRaster(vec2 uv, float t, int detail) {
    vec4 bg = background(uv, t, detail);

    vec4 fg = vec4(0.0);
    int n = 5;
    if (uv.y < 0.5) {
        for (int i = 0; i < 5; i++) {
            fg += foreground(uv, t + 4.0 * float(i) / 5.0 / 60.0, detail) / 5.0;
        }
    }

    vec3 col = bg.rgb;

    float k, h, disp, dist;
    vec2 uv2;

    uv.y -= 0.2;

    // ---- 列车本体（矢量转位图，静止于画面右侧，视差造成行进感） ----
    uv2 = fract(uv * 9.0);

    // 车厢
    float wagon = 1.0;
    wagon *= 1.0 - step(0.45, uv.x);
    wagon *= 1.0 - step(0.115, uv.y);
    wagon *= step(0.103, uv.y);
    wagon *= step(0.05, 1.0 - abs(uv2.x * 2.0 - 1.0));

    // 车厢连接处
    float join = 1.0;
    join *= 1.0 - step(0.45, uv.x);
    join *= 1.0 - step(0.11, uv.y);
    join *= step(0.107, uv.y);

    // 车顶
    float roof = 1.0;
    roof *= 1.0 - step(0.45, uv.x);
    roof *= 1.0 - step(0.117, uv.y);
    roof *= step(0.11, uv.y);
    roof *= step(0.15, 1.0 - abs(uv2.x * 2.0 - 1.0));

    // 机车细节
    float loco = box(uv, 0.45, 0.50, 0.103, 0.112);
    float chem1 = box(uv, 0.49, 0.495, 0.103, 0.12);
    float chem2 = box(uv, 0.488, 0.496, 0.12, 0.123);
    float locoRoof = box(uv, 0.443, 0.47, 0.11, 0.117);

    // 车轮
    float wheel = 1.0 - step(0.00004, dot2(uv - vec2(0.457, 0.106)));
    wheel += 1.0 - step(0.00002, dot2(uv - vec2(0.487, 0.105)));
    wheel += 1.0 - step(0.00002, dot2(uv - vec2(0.497, 0.105)));

    if (uv.x < 0.45 && uv.y > 0.025 && uv.y < 0.2) {
        wheel += 1.0 - step(0.002, dot2(uv2 - vec2(0.2, 0.95)));
        wheel += 1.0 - step(0.002, dot2(uv2 - vec2(0.8, 0.95)));
    }

    col = mix(col, vec3(0.18, 0.12, 0.15), join);
    col = mix(col, vec3(0.48, 0.19, 0.20), wagon);
    col = mix(col, vec3(0.18, 0.12, 0.15), roof);
    col = mix(col, vec3(0.38, 0.19, 0.20), loco);
    col = mix(col, vec3(0.38, 0.19, 0.20), chem1);
    col = mix(col, vec3(0.18, 0.12, 0.15), locoRoof);
    col = mix(col, vec3(0.18, 0.12, 0.15), chem2 + wheel);

    // ---- 机车烟雾 ----
    dist = 5.0;
    uv2 = uv + vec2(t / dist + 3.5, 0.0);
    uv2.x -= t / dist * 0.2;
    h = fbm2(uv2, detail) - 0.55;

    if (uv.x < 0.49) {
        float x = -uv.x + 0.49;
        // 烟雾自机车烟囱顶部稳定升起：起点用 smoothstep 将起伏（h*0.4）在起点处压到 0，
        // 并把基准抬高到烟囱上方（-0.13），避免起点下探到列车下方、遮挡最前方的云。
        float y = abs(uv.y + 0.4 * h * smoothstep(0.0, 0.08, x)
                      - 0.16 * sqrt(x) - 0.13) - 0.8 * x * exp(-x * 10.0);
        if (y < 0.0)   col = vec3(1.00, 0.94, 0.91);
        if (y < -0.02) col = vec3(0.92, 0.85, 0.82);
    }

    // ---- 云上大桥 ----
    dist = 5.0;
    uv2 = uv + vec2(t / dist + 32.5, 0.0);
    uv2.x = fract(uv2.x * 3.0);
    k = 1.0;
    k *= smoothstep(0.001, 0.003, abs(uv2.y - (uv2.x - 0.5) * (uv2.x - 0.5) * 0.15 - 0.12));
    k *= min(step(0.05, 1.0 - abs(uv2.x * 2.0 - 1.0)) + step(0.17, uv2.y), 1.0);
    k *= min(smoothstep(0.02, 0.05, 1.0 - abs(uv2.x * 2.0 - 1.0)) + step(0.177, uv2.y), 1.0);
    k *= min(step(0.1, uv2.y) + smoothstep(-0.09, -0.085, -uv2.y - 0.001 / (1.0 - abs(uv2.x * 2.0 - 1.0))), 1.0);
    k *= min(smoothstep(0.05, 0.2, 1.0 - abs(fract(uv2.x * 16.0) * 2.0 - 1.0))
        + step(0.12, uv2.y - pow(uv2.x - 0.5, 2.0) * 0.15)
        + step(-0.1, -uv2.y), 1.0);
    col = mix(vec3(0.29, 0.09, 0.08) * smoothstep(-0.08, 0.08, uv.y), col, k);

    // 前景云层
    col = mix(col, fg.rgb, fg.a);

    return col;
}

// 暗角 + 曝光 + 亮度波动
vec3 vignettCorrection(vec3 col, vec2 uv) {
    col *= 0.5 + 0.5 * pow(16.0 * uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y), 0.2);
    col *= 1.3 + 0.4 * cos(0.5 * (uTime + 3.14));
    return col;
}

void main() {
    vec2 fragCoord = gl_FragCoord.xy;

    // 开场淡入曲线（开场时长由 uOpeningDur 控制）
    float intro = smoothstep(0.0, max(uOpeningDur, 0.001), uTime);

    // 基础时间，乘行进速度；开场阶段速度从慢到快
    float t = (sin(1.2 * uTime) + 4.0 * uTime) * uSpeed * mix(0.45, 1.0, intro);

    // 视角缩放：围绕画面中心缩放（分量为宽高比修正，避免拉伸）
    vec2 res = uResolution;
    float aspect = res.x / res.y;
    vec2 uv = fragCoord / res.y;              // 以高度归一化（保持比例）
    vec2 center = vec2(aspect * 0.5, 0.5);
    uv = (uv - center) / uZoom + center;

    // 噪声细节（层数，夹取到 1..8）
    int detail = int(clamp(uNoiseDetail + 0.5, 1.0, 8.0));

    vec3 col = genRaster(uv, t, detail);

    // 暗角校正用独立归一化坐标（0..1）
    vec2 uvVignett = fragCoord / res;
    col = vignettCorrection(col, uvVignett);

    // 曝光亮度 + 开场淡入
    col *= uExposure;
    col *= intro;

    outColor = vec4(col, 1.0);
}
