# 生成 CipherWS 分层架构图（自上而下）
# 用 Pillow 手绘，避免依赖 graphviz/中文字体配置问题
import os
from PIL import Image, ImageDraw, ImageFont

FONT_DIR = r"C:\Windows\Fonts"
FONT_REG = os.path.join(FONT_DIR, "msyh.ttc")      # 微软雅黑
FONT_BOLD = os.path.join(FONT_DIR, "msyhbd.ttc")   # 微软雅黑粗体
OUT = r"D:\GraduationProject\CipherWS\docs\architecture.png"

SCALE = 1.5
W = int(1560 * SCALE)
H = int(2280 * SCALE)
PAD = int(60 * SCALE)
X0 = PAD
X1 = W - PAD


def f(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, int(size * SCALE))


img = Image.new("RGB", (W, H), (255, 255, 255))
d = ImageDraw.Draw(img)

# 配色
C_TITLE = (30, 41, 59)
C_SUB = (100, 116, 139)
C_ARROW = (120, 130, 145)
LAYERS = {
    "L1": ((219, 234, 254), (59, 130, 246), (30, 64, 175)),
    "L2": ((224, 231, 255), (99, 102, 241), (49, 46, 129)),
    "L3": ((237, 233, 254), (139, 92, 246), (76, 29, 149)),
    "L4": ((209, 250, 229), (16, 185, 129), (6, 78, 59)),
    "L5": ((254, 249, 195), (234, 179, 8), (113, 63, 18)),
    "L6": ((226, 232, 240), (100, 116, 139), (30, 41, 59)),
}


def wrap(text, font, max_w):
    """按像素宽度折行（中文逐字，英文按空格）"""
    lines, cur = [], ""
    for ch in text:
        trial = cur + ch
        if d.textlength(trial, font=font) <= max_w or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    return lines


def layer_box(tag, top, height, title, body, width_span=(X0, X1)):
    bx0, bx1 = width_span
    bg, border, fg = LAYERS[tag]
    d.rounded_rectangle([bx0, top, bx1, top + height], radius=int(14 * SCALE),
                        fill=bg, outline=border, width=int(3 * SCALE))
    # 左侧层号胶囊
    tag_font = f(20, bold=True)
    tag_w = d.textlength(tag, font=tag_font)
    pill_w = tag_w + int(28 * SCALE)
    pill_h = int(40 * SCALE)
    d.rounded_rectangle([bx0 + int(18 * SCALE), top + int(16 * SCALE),
                         bx0 + int(18 * SCALE) + pill_w, top + int(16 * SCALE) + pill_h],
                        radius=int(20 * SCALE), fill=border)
    d.text((bx0 + int(18 * SCALE) + pill_w / 2, top + int(16 * SCALE) + pill_h / 2),
           tag, font=tag_font, fill=(255, 255, 255), anchor="mm")
    # 标题
    tx = bx0 + int(18 * SCALE) + pill_w + int(18 * SCALE)
    d.text((tx, top + int(16 * SCALE) + pill_h / 2), title, font=f(27, bold=True),
           fill=fg, anchor="lm")
    # 正文
    body_font = f(19)
    y = top + int(16 * SCALE) + pill_h + int(14 * SCALE)
    line_h = int(30 * SCALE)
    inner_w = (bx1 - bx0) - int(60 * SCALE)
    for line in body:
        for seg in wrap(line, body_font, inner_w):
            d.text((bx0 + int(30 * SCALE), y), seg, font=body_font, fill=C_TITLE)
            y += line_h
    return y


def arrow(x, y_from, y_to, label=None):
    d.line([x, y_from, x, y_to], fill=C_ARROW, width=int(4 * SCALE))
    a = int(11 * SCALE)
    d.polygon([(x - a, y_to - a), (x + a, y_to - a), (x, y_to)], fill=C_ARROW)
    if label:
        d.text((x + int(14 * SCALE), (y_from + y_to) / 2), label, font=f(17),
               fill=C_SUB, anchor="lm")


def gap(y_from, y_to, label=None, x=None):
    arrow(x if x else (X0 + X1) // 2, y_from, y_to, label)


def box_height(lines, extra=0):
    rows = 0
    inner_w = (X1 - X0) - int(60 * SCALE)
    body_font = f(19)
    for line in lines:
        rows += len(wrap(line, body_font, inner_w))
    return int(16 * SCALE) + int(40 * SCALE) + int(14 * SCALE) + rows * int(30 * SCALE) + int(18 * SCALE) + extra


# ============ 标题 ============
d.text((X0, PAD), "CipherWS 项目分层架构（自上而下）", font=f(42, bold=True), fill=C_TITLE)
d.text((X0, PAD + int(58 * SCALE)), "从用户接入到基础设施，共 6 层", font=f(22), fill=C_SUB)

y = PAD + int(120 * SCALE)

# ============ L1 ============
L1 = [
    "使用者：访客 · 答辩评委 · 开发者",
    "终端：Microsoft Edge / Chrome / 手机浏览器",
    "协议：HTTP（IP 访问）→ HTTPS（备案后）",
]
h1 = box_height(L1, extra=int(10 * SCALE))
layer_box("L1", y, h1, "用户接入层", L1)
y += h1

# ============ L2 ============
gap(y, y + int(58 * SCALE))
y += int(58 * SCALE)
L2 = [
    "域名：cipherws.icu · design.cipherws.icu",
    "DNS 解析 ──▶ 阿里云安全组（端口白名单）──▶ 分流组件（nginx / 多端口）",
    "职责：按 Host 把请求分发到对应站点，终结 HTTPS，收敛跨域",
]
h2 = box_height(L2, extra=int(10 * SCALE))
layer_box("L2", y, h2, "网络接入与路由层", L2)
y += h2

# ============ L3 ============
gap(y, y + int(58 * SCALE))
y += int(58 * SCALE)
gap_y = y
L3_TITLE_H = int(72 * SCALE)
L3A = [
    "Vue 3 + Vite 5 + vue-router 4",
    "DefaultLayout：头 / 导航 / 主体 / 尾",
    "页面：首页 · 链接页",
    "全局：开场动画 · 左右滑动切换 · 悬浮控件",
]
L3B = [
    "独立前端工程（技术栈待定）",
    "复用：布局骨架 · 滑动动画方案",
    "复用：开场动画 loader · server.js",
    "页面：系统设计 等",
]
mid = (X0 + X1) // 2
col_gap = int(40 * SCALE)
left_span = (X0, mid - col_gap // 2)
right_span = (mid + col_gap // 2, X1)

# L3 标题条（整层框）
h3_inner = max(box_height(L3A, extra=int(10 * SCALE)), box_height(L3B, extra=int(10 * SCALE)))
d.rounded_rectangle([X0, y, X1, y + L3_TITLE_H + h3_inner + int(26 * SCALE)],
                    radius=int(14 * SCALE), fill=(237, 233, 254),
                    outline=(139, 92, 246), width=int(3 * SCALE))
tag_font = f(20, bold=True)
tag = "L3"
pill_w = d.textlength(tag, font=tag_font) + int(28 * SCALE)
pill_h = int(40 * SCALE)
d.rounded_rectangle([X0 + int(18 * SCALE), y + int(16 * SCALE),
                     X0 + int(18 * SCALE) + pill_w, y + int(16 * SCALE) + pill_h],
                    radius=int(20 * SCALE), fill=(139, 92, 246))
d.text((X0 + int(18 * SCALE) + pill_w / 2, y + int(16 * SCALE) + pill_h / 2),
       tag, font=tag_font, fill=(255, 255, 255), anchor="mm")
d.text((X0 + int(18 * SCALE) + pill_w + int(18 * SCALE), y + int(16 * SCALE) + pill_h / 2),
       "前端应用层", font=f(27, bold=True), fill=(76, 29, 149), anchor="lm")

sub_y = y + L3_TITLE_H + int(4 * SCALE)
for (span, name, lines, color) in [
    (left_span, "主站  cipherws.icu", L3A, (59, 130, 246)),
    (right_span, "子系统  design.cipherws.icu", L3B, (236, 72, 153)),
]:
    bx0, bx1 = span
    bh = box_height(lines, extra=int(6 * SCALE))
    d.rounded_rectangle([bx0, sub_y, bx1, sub_y + bh], radius=int(10 * SCALE),
                        fill=(255, 255, 255), outline=color, width=int(2 * SCALE))
    d.text((bx0 + int(20 * SCALE), sub_y + int(18 * SCALE)), name,
           font=f(22, bold=True), fill=color, anchor="lm")
    bf = f(19)
    yy = sub_y + int(48 * SCALE)
    for line in lines:
        for seg in wrap(line, bf, (bx1 - bx0) - int(40 * SCALE)):
            d.text((bx0 + int(20 * SCALE), yy), seg, font=bf, fill=C_TITLE)
            yy += int(30 * SCALE)
y += L3_TITLE_H + h3_inner + int(26 * SCALE)

# ============ L4 ============
gap(y, y + int(58 * SCALE), "同源反代 /say/** · /design/**")
y += int(58 * SCALE)
L4 = [
    "Spring Boot 3.5.16 + JDK 17 + Maven ｜ 模块化单体：单 jar / 单进程 / 单端口",
    "common 公共能力：Result 统一响应 · 异常处理 · Config（CORS）",
    "modules.say 主站模块：Controller · Service · Entity",
    "modules.design 子系统模块：Controller · Service · Entity",
    "现有接口：GET /say/saying?id=N",
]
h4 = box_height(L4, extra=int(10 * SCALE))
layer_box("L4", y, h4, "后端服务层", L4)
y += h4

# ============ L5 ============
gap(y, y + int(58 * SCALE), "JDBC（规划中）")
y += int(58 * SCALE)
L5 = [
    "现状：无数据库。名言数据来自 saying.json 静态文件（SayingComponent 读取）",
    "规划：引入关系型数据库（MySQL / PostgreSQL）",
    "关键约束：若主站与子系统共用库，必须先划分表归属 —— 哪张表归谁写、谁只读",
]
h5 = box_height(L5, extra=int(10 * SCALE))
layer_box("L5", y, h5, "数据层", L5)
y += h5

# ============ L6 ============
gap(y, y + int(58 * SCALE))
y += int(58 * SCALE)
L6 = [
    "云端（阿里云 ECS · Ubuntu · 47.104.253.198）：",
    "    systemd 托管（开机自启 / 崩溃重启）· Node server.js 静态服务 · 安全组 · Spring Boot 进程",
    "本地（笔记本）：编码与预览（Vite Dev Server）· 可选构建 · Git 提交推送",
    "安全约定：后端 8080 接入 nginx 后不再对外暴露，仅监听 127.0.0.1",
]
h6 = box_height(L6, extra=int(10 * SCALE))
layer_box("L6", y, h6, "基础设施与运行环境层", L6)
y += h6

# ============ 底部说明 ============
y += int(30 * SCALE)
d.text((X0, y), "说明：L1–L5 均运行在 L6 提供的基础设施之上；L2 的分流组件在阶段一用「多端口」替代，阶段二切换为 nginx。",
       font=f(18), fill=C_SUB)

img.save(OUT, "PNG")
print("已生成:", OUT)
print("尺寸:", img.size)

# 画布预留高度可能大于实际内容，裁掉底部空白，让图更紧凑、便于插入论文
bbox = img.point(lambda p: 255 - p).getbbox()
if bbox:
    margin = int(30 * SCALE)
    right = min(W, bbox[2] + margin)
    bottom = min(H, bbox[3] + margin)
    img = img.crop((0, 0, right, bottom))
    img.save(OUT, "PNG")
    print("裁剪后尺寸:", img.size)
