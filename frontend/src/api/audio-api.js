const BASE_URL = import.meta.env.VITE_API_BASE_URL

/**
 * 拼接音频流播放地址。
 *
 * 对应后端接口 `GET /audio/stream?audio=<文件名>`（见 server/接口文档.md）：
 * 后端按 `audio` 参数在音乐根目录下找文件并返回音频流，支持 HTTP Range
 * 分段请求，因此可以直接把返回值交给 <audio> 的 src，浏览器会自动处理
 * 拖动进度条、断点续播等行为，无需前端手动分片。
 *
 * 注意：文件名含空格、中日文、括号等字符，必须 URL 编码后再拼接，
 * 否则会被浏览器或网关按非法字符处理。
 *
 * @param {string} audioFile 音频文件名，需带扩展名（如 `周杰伦 - 晴天.mp3`）
 * @returns {string} 可直接用于 <audio src> 的完整地址；audioFile 为空时返回 ''
 */
export function getAudioStreamUrl (audioFile) {
  if (!audioFile) return ''
  const query = new URLSearchParams({ audio: audioFile })
  return `${BASE_URL}/audio/stream?${query.toString()}`
}
