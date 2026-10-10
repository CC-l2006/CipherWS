// useShaderRenderer.js — WebGL2 着色器渲染引擎（Vue 3 Composable）
import { onMounted, onUnmounted, shallowRef } from 'vue'
import fragSrc from '../shaders/cloudtrain.frag?raw'
import vertSrc from '../shaders/cloudtrain.vert?raw'

// TEMP DEBUG
window.__dbg = { moduleLoaded: true, step: 'module' }
console.log('[shader] module loaded, frag len', fragSrc.length)

/**
 * 渲染参数（全部响应式，修改后下一帧自动生效）
 */
export function createDefaultParams() {
  return {
    openingDur: 3.0,    // 开场时长（秒）
    speed: 1.0,         // 行进速度 (0..3)
    undulation: 1.0,    // 云层起伏 (0..2.5)
    noiseDetail: 6,     // 噪声细节层数 (1..8)
    exposure: 1.0,      // 曝光亮度 (0.2..2.5)
    zoom: 1.0,          // 视角缩放 (0.5..2)
  }
}

/**
 * 编译着色器
 */
function compileShader(gl, type, source) {
  const shader = gl.createShader(type)
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(shader)
    gl.deleteShader(shader)
    throw new Error(`Shader compile error: ${log}`)
  }
  return shader
}

/**
 * 链接着色器程序，返回程序对象和 uniform 位置映射
 */
function createProgram(gl, vertSrc, fragSrc) {
  const vs = compileShader(gl, gl.VERTEX_SHADER, vertSrc)
  const fs = compileShader(gl, gl.FRAGMENT_SHADER, fragSrc)
  const prog = gl.createProgram()
  gl.attachShader(prog, vs)
  gl.attachShader(prog, fs)
  gl.linkProgram(prog)
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    const log = gl.getProgramInfoLog(prog)
    gl.deleteProgram(prog)
    throw new Error(`Program link error: ${log}`)
  }
  // 获取所有 uniform 位置
  const uniforms = {}
  const names = [
    'uResolution', 'uTime', 'uOpeningDur', 'uSpeed', 'uUndulation',
    'uNoiseDetail', 'uExposure', 'uZoom', 'uMouse',
  ]
  for (const name of names) {
    uniforms[name] = gl.getUniformLocation(prog, name)
  }
  return { program: prog, uniforms }
}

/**
 * 创建全屏四边形（两个三角形）
 */
function createFullscreenQuad(gl) {
  //  [-1, 1]  [1, 1]
  //     +------+
  //     |\     |
  //     | \    |
  //     +------+
  //  [-1,-1]  [1,-1]
  const vertices = new Float32Array([
    -1, -1,   1, -1,  -1, 1,   // 左下, 右下, 左上
    -1,  1,   1, -1,   1, 1,   // 左上, 右下, 右上
  ])
  const vbo = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, vbo)
  gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW)
  return vbo
}

/**
 * useShaderRenderer
 * @param {import('vue').Ref<HTMLCanvasElement|null>} canvasRef
 * @param {import('vue').Ref<Object>} paramsRef — 当前参数对象的 ref
 * @returns {{ startTime: Ref<number>, replay: Function }}
 */
export function useShaderRenderer(canvasRef, paramsRef) {
  const startTime = shallowRef(0)

  let gl = null
  let progInfo = null
  let animId = null
  let resizeObserver = null
  let mousePos = [-1, -1] // 归一化 0..1 或 -1 表示未按下

  /**
   * 主渲染帧
   */
  function render(now) {
    window.__dbg.renderCalls = (window.__dbg.renderCalls || 0) + 1
    window.__dbg.lastNow = now
    if (window.__dbg.renderCalls <= 3) console.log('[shader] render called', window.__dbg.renderCalls, 'now', now, 'gl?', !!gl, 'prog?', !!progInfo)
    if (!gl || !progInfo) return

    const canvas = canvasRef.value
    if (!canvas) return

    const dpr = window.devicePixelRatio || 1
    const w = canvas.clientWidth * dpr
    const h = canvas.clientHeight * dpr
    console.log('[shader] clientW/H', canvas.clientWidth, canvas.clientHeight, 'target', w, h)
    window.__dbg.lastW = w; window.__dbg.lastH = h

    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w
      canvas.height = h
      gl.viewport(0, 0, w, h)
    }

    gl.useProgram(progInfo.program)

    const elapsed = (now - startTime.value) / 1000.0
    // 兼容两种传参：真实 ref（.value）或 getter 函数（() => props.params）
    const p = (typeof paramsRef === 'function') ? paramsRef() : paramsRef.value

    // 设置所有 uniform
    const u = progInfo.uniforms
    gl.uniform2f(u.uResolution, w, h)
    gl.uniform1f(u.uTime, elapsed)
    gl.uniform1f(u.uOpeningDur, p.openingDur)
    gl.uniform1f(u.uSpeed, p.speed)
    gl.uniform1f(u.uUndulation, p.undulation)
    gl.uniform1f(u.uNoiseDetail, p.noiseDetail)
    gl.uniform1f(u.uExposure, p.exposure)
    gl.uniform1f(u.uZoom, p.zoom)
    gl.uniform2f(u.uMouse, mousePos[0], mousePos[1])

    // 绑定顶点属性并绘制
    gl.enableVertexAttribArray(0)
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0)
    gl.drawArrays(gl.TRIANGLES, 0, 6)

    animId = requestAnimationFrame(render)
  }

  /**
   * 鼠标/触摸移动
   */
  function onPointerMove(e) {
    const c = canvasRef.value
    if (!c) return
    const rect = c.getBoundingClientRect()
    mousePos = [
      (e.clientX - rect.left) / rect.width,
      (e.clientY - rect.top) / rect.height,
    ]
  }
  function onPointerLeave() {
    mousePos = [-1, -1]
  }

  /**
   * 初始化
   */
  function init() {
    const canvas = canvasRef.value
    window.__dbg.step = 'init-start'
    window.__dbg.hasCanvas = !!canvas
    console.log('[shader] init() called, canvas?', !!canvas)
    if (!canvas) return

    gl = canvas.getContext('webgl2', {
      antialias: false,
      alpha: false,
      preserveDrawingBuffer: false,
    })
    window.__dbg.gl = !!gl
    console.log('[shader] gl created?', !!gl)
    if (!gl) {
      throw new Error('WebGL2 not available')
    }

    progInfo = createProgram(gl, vertSrc, fragSrc)
    window.__dbg.progInfo = !!progInfo
    console.log('[shader] program created?', !!progInfo)
    createFullscreenQuad(gl)

    gl.clearColor(0, 0, 0, 1)
    gl.disable(gl.DEPTH_TEST)
    gl.disable(gl.CULL_FACE)

    // 鼠标/触摸跟踪
    canvas.addEventListener('pointermove', onPointerMove)
    canvas.addEventListener('pointerleave', onPointerLeave)

    // 响应式尺寸变化（非 Retina 可视尺寸）
    resizeObserver = new ResizeObserver(() => {
      // 尺寸更新在下一帧 render 中处理
    })
    resizeObserver.observe(canvas)

    // 记录起始时间
    startTime.value = performance.now()

    // 开始渲染循环
    animId = requestAnimationFrame(render)
  }

  /**
   * 销毁
   */
  function destroy() {
    if (animId) cancelAnimationFrame(animId)
    if (resizeObserver) resizeObserver.disconnect()
    const canvas = canvasRef.value
    if (canvas) {
      canvas.removeEventListener('pointermove', onPointerMove)
      canvas.removeEventListener('pointerleave', onPointerLeave)
    }
    if (gl && progInfo) {
      gl.deleteProgram(progInfo.program)
    }
    gl = null
    progInfo = null
  }

  onMounted(init)
  onUnmounted(destroy)

  /** 重播开场（重置起始时间） */
  function replay() {
    startTime.value = performance.now()
  }

  return { startTime, replay }
}