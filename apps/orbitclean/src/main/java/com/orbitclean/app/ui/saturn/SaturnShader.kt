/**
 * [INPUT]: 依赖 android.graphics.RuntimeShader（API 33+），依赖 ./SaturnCamera 的坐标约定
 * [OUTPUT]: 对外提供 SaturnShader（AGSL 源码 + 每帧设置 uniform）
 * [POS]: ui/saturn 的高档位渲染器：一个片元着色器画出深空背景（漂移星点、流星）、扁球土星（差速自转、流动云带、风暴）、
 *        极冠与北极六边形、光环（开普勒流动、冰晶闪烁）及互投阴影、用量轨道（能量流动）、扫描光束、冲击波
 *        与 HTML 原型的 WebGL 版逐行对应；低档位与 API < 33 走 SaturnFallback
 *        性能：整帧不变的旋转/相机/光源在 update() 里算好以 uniform 传入；背景只在没打到星球的像素上计算
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.saturn

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@RequiresApi(33)
internal class SaturnShader {

    val shader = RuntimeShader(SOURCE)

    /**
     * 所有长度参数单位为像素；角度为弧度。
     * 对整帧不变的量（滚转/开合的 cos·sin、相机位置、光源方向）在这里算好再传入，
     * 片元着色器每个像素不再重复做 12 次三角函数和两次旋转
     */
    fun update(
        width: Float,
        height: Float,
        cam: SaturnCamera,
        time: Float,
        usage: Float,
        scan: Float,
        sweep: Float,
        shock: Float,
        pulse: Float,
    ) = with(shader) {
        setFloatUniform("uRes", width, height)
        setFloatUniform("uCenter", cam.cx, cam.cy)
        setFloatUniform("uRadius", cam.radius)
        val cr = cos(cam.roll)
        val sr = sin(cam.roll)
        val co = cos(cam.open)
        val so = sin(cam.open)
        setFloatUniform("uRot", cr, sr, co, so)
        // ro = toLocal(0, 0, D)
        setFloatUniform("uRo", 0f, -so * SaturnGeom.D, co * SaturnGeom.D)
        // L = toLocal(normalize(-0.55 + 0.08·sin(0.13t), 0.5, 0.65))
        val lx0 = -0.55f + 0.08f * sin(time * 0.13f)
        val inv = 1f / sqrt(lx0 * lx0 + 0.5f * 0.5f + 0.65f * 0.65f)
        val lx = lx0 * inv
        val ly = 0.5f * inv
        val lz = 0.65f * inv
        val y1 = sr * lx + cr * ly
        setFloatUniform("uL", cr * lx - sr * ly, co * y1 - so * lz, so * y1 + co * lz)
        setFloatUniform("uTime", time)
        setFloatUniform("uUsage", usage)
        setFloatUniform("uScan", scan)
        setFloatUniform("uSweep", sweep)
        setFloatUniform("uShock", shock)
        setFloatUniform("uPulse", pulse)
    }

    private companion object {
        val SOURCE = """
uniform float2 uRes;
uniform float2 uCenter;
uniform float uRadius;
uniform float4 uRot;   // (cos roll, sin roll, cos open, sin open)
uniform float3 uRo;    // 本体系里的相机位置
uniform float3 uL;     // 本体系里的光源方向
uniform float uTime;
uniform float uUsage;
uniform float uScan;
uniform float uSweep;
uniform float uShock;
uniform float uPulse;

const float D = 8.0;
const float OBL = 0.90;
const float TAU = 6.2831853;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm3(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) { v += a * noise(p); p = p * 2.03 + float2(1.7, 9.2); a *= 0.5; }
    return v;
}

float fbm4(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * noise(p); p = p * 2.03 + float2(1.7, 9.2); a *= 0.5; }
    return v;
}

// 世界系 → 本体系：local = Rx(open) · Rz(roll) · world
float3 toLocal(float3 v) {
    v = float3(uRot.x * v.x - uRot.y * v.y, uRot.y * v.x + uRot.x * v.y, v.z);
    return float3(v.x, uRot.z * v.y - uRot.w * v.z, uRot.w * v.y + uRot.z * v.z);
}

// 光环光学厚度：C 环 / B 环 / 卡西尼缝 / A 环 / 恩克缝 / F 环
float ringD(float r) {
    float d = 0.0;
    d += smoothstep(1.23, 1.26, r) * (1.0 - smoothstep(1.50, 1.53, r)) * 0.22;
    d += smoothstep(1.52, 1.57, r) * (1.0 - smoothstep(1.93, 1.95, r)) * 0.95;
    d += smoothstep(1.95, 1.97, r) * (1.0 - smoothstep(2.01, 2.03, r)) * 0.05;
    d += smoothstep(2.02, 2.05, r) * (1.0 - smoothstep(2.25, 2.27, r)) * 0.72;
    d *= 1.0 - 0.9 * smoothstep(2.206, 2.211, r) * (1.0 - smoothstep(2.219, 2.224, r));
    d *= 0.66 + 0.34 * (0.5 + 0.5 * sin(r * 120.0) * sin(r * 61.0 + 1.3));
    d += exp(-abs(r - 2.33) * 260.0) * 0.45;
    return clamp(d, 0.0, 1.0);
}

float angDiff(float a, float b) {
    return mod(a - b + TAU * 1.5, TAU) - TAU * 0.5;
}

// 背景：深空 + 星云 + 星点 + 流星（只在没被星球挡住的像素上计算，星球像素会整体覆盖它）
float3 sky(float2 sp) {
    float3 col = float3(0.010, 0.016, 0.034);
    col += float3(0.03, 0.10, 0.12) * smoothstep(0.9, 0.0, length(sp - float2(0.40, 0.78))) * 0.9;
    col += float3(0.09, 0.05, 0.13) * smoothstep(0.8, 0.0, length(sp - float2(0.05, 0.30))) * 0.7;
    col += float3(0.035, 0.04, 0.07) * fbm3(sp * 3.0 + float2(0.0, uTime * 0.01));
    float2 g = (sp + float2(uTime * 0.0025, uTime * 0.0008)) * 95.0;
    float2 id = floor(g);
    float h = hash(id);
    if (h > 0.962) {
        float2 off = (float2(hash(id + 3.1), hash(id + 7.7)) - 0.5) * 0.6;
        float dd = length(fract(g) - 0.5 - off);
        float tw = 0.6 + 0.4 * sin(uTime * 1.7 + h * 60.0);
        col += float3(0.82, 0.9, 1.0) * smoothstep(0.07, 0.0, dd) * tw * (h - 0.962) * 26.0;
    }

    // 流星：每 6.5 秒一颗，从右上划向左下，拖尾渐隐（在星球之后绘制，会被遮住）
    float cyc = floor(uTime / 6.5);
    float fm = fract(uTime / 6.5) / 0.16;
    if (fm < 1.0) {
        float aspect = uRes.x / uRes.y;
        float2 s0 = float2(aspect * (0.55 + 0.4 * hash(float2(cyc, 1.7))), 0.62 + 0.3 * hash(float2(cyc, 9.1)));
        float2 dir = normalize(float2(-0.9, -0.38 - 0.25 * hash(float2(cyc, 4.3))));
        float2 rel = sp - (s0 + dir * fm * 0.5);
        float along = dot(rel, -dir);
        float across = abs(rel.x * dir.y - rel.y * dir.x);
        float m = step(0.0, along) * (1.0 - smoothstep(0.0, 0.16, along)) * smoothstep(0.0028, 0.0, across) * sin(fm * 3.14159);
        col += float3(0.85, 0.95, 1.0) * m * 1.4;
    }
    return col;
}

half4 main(float2 fc) {
    float2 q = float2(fc.x - uCenter.x, uCenter.y - fc.y) / uRadius;
    float2 sp = float2(fc.x, uRes.y - fc.y) / uRes.y;
    float3 ro = uRo;
    float3 rd = toLocal(normalize(float3(q, -D)));
    float3 L = uL;
    float3 teal = float3(0.34, 0.88, 0.76);

    // 扁球体求交
    float3 sc = float3(1.0, 1.0 / OBL, 1.0);
    float3 oe = ro * sc;
    float3 de = rd * sc;
    float a = dot(de, de);
    float b = dot(oe, de);
    float c = dot(oe, oe) - 1.0;
    float disc = b * b - a * c;
    float tS = 1.0e9;
    if (disc > 0.0) {
        float t = (-b - sqrt(disc)) / a;
        if (t > 0.0) tS = t;
    }

    // 环平面求交
    float tR = -ro.y / rd.y;
    float3 pR = ro + rd * tR;
    float r = length(pR.xz);
    float ang = atan(pR.z, pR.x);
    bool front = tR > 0.0 && tR < tS;

    // 星球本体：云带 + 赤道亮带 + 极区灰蓝 + 光环投影 + 边缘暗化；没打到星球才画背景
    float3 col;
    if (tS < 1.0e8) {
        float3 p = ro + rd * tS;
        float3 n = normalize(p * float3(1.0, 1.0 / (OBL * OBL), 1.0));
        float lat = p.y / OBL;
        // 差速自转：赤道快、两极慢
        float lon = atan(p.z, p.x) + uTime * (0.28 + 0.14 * cos(lat * 3.0));
        float2 q0 = float2(lon * 2.4, lat * 9.0);
        // 域扭曲：云带边缘像流体一样卷动
        float2 warp = float2(fbm3(q0 * 0.8 + float2(uTime * 0.16, 0.0)), fbm3(q0 * 0.8 + float2(5.2, uTime * 0.12)));
        float turb = fbm4(q0 + warp * 1.6);
        float3 c1 = float3(0.88, 0.77, 0.56);
        float3 c2 = float3(0.70, 0.55, 0.35);
        float3 c3 = float3(0.96, 0.90, 0.76);
        float3 base = mix(c1, c2, 0.5 + 0.5 * sin(lat * 19.0 + turb * 2.4));
        base = mix(base, c3, smoothstep(0.3, 1.0, 0.5 + 0.5 * sin(lat * 47.0 + turb * 4.0)) * 0.35);
        base = mix(base, float3(0.96, 0.91, 0.80), smoothstep(0.16, 0.0, abs(lat - 0.04)) * 0.45);
        // 极冠：只占最顶上一小块，低饱和的冷米色，不再把上半球染灰
        base = mix(base, float3(0.72, 0.70, 0.64), smoothstep(0.80, 0.97, abs(lat)) * 0.75);
        // 北极六边形：极冠里一圈略暗的六边形纹路，随极区缓慢转动
        if (lat > 0.7) {
            float rho = length(p.xz);
            float seg = TAU / 6.0;
            float phi = mod(atan(p.z, p.x) + uTime * 0.15, seg);
            float hx = rho * cos(phi - seg * 0.5) / cos(seg * 0.5);
            // 边线加宽、压淡，并用云的湍流打散，看起来是气流而不是描边
            float wob = (turb - 0.5) * 0.05;
            float edge = exp(-(hx - 0.36 + wob) * (hx - 0.36 + wob) * 220.0);
            base *= 1.0 - edge * 0.13;
            base = mix(base, float3(0.66, 0.63, 0.57), smoothstep(0.38, 0.26, hx + wob) * 0.18);
        }
        // 风暴：北半球一团亮白漩涡，随云带转过来、再转到背面
        float2 sd = float2(angDiff(lon, 1.2), (lat - 0.33) * 2.4);
        float storm = exp(-dot(sd, sd) * 16.0);
        float swirl = 0.5 + 0.5 * sin(atan(sd.y, sd.x) * 2.0 + length(sd) * 22.0 - uTime * 3.2);
        base = mix(base, float3(0.98, 0.94, 0.84), storm * (0.55 + 0.25 * swirl));
        base *= 1.0 - exp(-dot(sd, sd) * 5.0) * 0.12 * (1.0 - storm);
        float diff = smoothstep(-0.10, 1.0, dot(n, L));
        float sh = 1.0;
        float tt = -p.y / L.y;
        if (tt > 0.0) sh = 1.0 - ringD(length((p + L * tt).xz)) * 0.82;
        float limb = pow(clamp(dot(n, -rd), 0.0, 1.0), 0.38);
        col = base * diff * sh * limb * 1.18 + base * float3(0.035, 0.045, 0.06);
        float fres = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 3.0);
        // 边缘光以暖金为主，只带一点青，避免和极冠混成灰色
        col += mix(float3(1.0, 0.82, 0.55), teal, 0.15) * fres * 0.5 * (diff + 0.15);
    } else {
        col = sky(sp);
    }

    // 大气光晕
    float tc = -b / a;
    float dc = length(oe + de * tc);
    if (dc > 1.0 && tc > 0.0) {
        col += mix(float3(1.0, 0.8, 0.5), teal, 0.25) * exp(-(dc - 1.0) * 13.0) * (0.38 + 0.12 * sin(uTime * 1.4));
    }

    // 光环：只在星球前方或无遮挡处可见；星球在环上投影，扫描光束沿环扫过
    if (front && r > 1.2 && r < 2.36) {
        // 内圈转得快、外圈慢（角速度 ∝ r^-2，比开普勒更夸张一点，肉眼能分出快慢），环上的明暗团块随之流动
        float flowA = ang + uTime * 1.4 / (r * r);
        float dens = ringD(r) * (0.82 + 0.18 * noise(float2(flowA * 28.0, r * 36.0)));
        float3 rc = mix(float3(0.60, 0.57, 0.52), float3(0.94, 0.86, 0.70), smoothstep(1.5, 2.0, r));
        rc = mix(rc, float3(0.86, 0.79, 0.65), smoothstep(2.02, 2.3, r));
        float3 oe2 = pR * sc;
        float3 de2 = L * sc;
        float a2 = dot(de2, de2);
        float b2 = dot(oe2, de2);
        float c2 = dot(oe2, oe2) - 1.0;
        float d2 = b2 * b2 - a2 * c2;
        float shp = b2 < 0.0 ? mix(1.0, 0.10, smoothstep(0.0, 0.03, d2)) : 1.0;
        float3 ringCol = rc * (0.35 + 0.65 * abs(L.y)) * shp * 1.15;
        ringCol *= 1.0 + uPulse * 0.45 * (0.5 + 0.5 * sin(uTime * 7.0 - r * 14.0));
        float da = angDiff(ang, uSweep);
        ringCol += teal * uScan * (da <= 0.0 ? exp(da * 2.6) : exp(-da * 45.0)) * 1.1;
        // 冰晶闪烁：零星亮点随环流动、明灭
        float2 cell = floor(float2(flowA * 160.0, r * 90.0));
        float hs = hash(cell);
        if (hs > 0.982) ringCol += float3(1.0, 0.97, 0.9) * pow(0.5 + 0.5 * sin(uTime * 3.0 + hs * 60.0), 10.0) * 0.9;
        col = mix(col, ringCol, dens * 0.93);
    }

    // 用量轨道 + 扫描光束 + 冲击波（都在环平面上）
    if (front) {
        float u = fract((ang + 1.9) / TAU);
        float on = step(u, uUsage);
        float flow = 0.7 + 0.3 * sin(u * 40.0 - uTime * 2.4);
        col += teal * exp(-abs(r - 2.48) * 95.0) * (on * 1.5 * flow + 0.10);
        col += teal * exp(-abs(r - 2.48) * 16.0) * on * 0.10;
        float da = angDiff(ang, uSweep);
        float beam = uScan * (da <= 0.0 ? exp(da * 3.0) : exp(-da * 60.0)) * smoothstep(3.2, 1.0, r) * step(1.0, r);
        col += teal * beam * 0.32;
        if (uShock > 0.0) {
            float fade = 1.0 - clamp((uShock - 1.0) / 3.4, 0.0, 1.0);
            col += float3(0.55, 1.0, 0.9) * exp(-abs(r - uShock) * 12.0) * fade * 1.3;
            col += float3(1.0, 0.85, 0.6) * exp(-abs(r - uShock * 0.8) * 20.0) * fade * 0.4;
        }
    }

    // 卫星：标记当前用量
    float ma = uUsage * TAU - 1.9;
    float3 M = float3(cos(ma), 0.0, sin(ma)) * 2.48;
    float3 w = M - ro;
    float tm = dot(w, rd);
    if (tm < tS) {
        float dm = length(w - rd * tm);
        col += teal * exp(-dm * 30.0) * (0.75 + 0.25 * sin(uTime * 2.4));
        col += float3(0.9, 1.0, 0.97) * smoothstep(0.055, 0.02, dm);
    }

    col = 1.0 - exp(-col * 1.3);
    return half4(half3(pow(col, float3(0.92))), 1.0);
}
"""
    }
}
