import request from './request.js'

// 获取名言
export function getSaying(id) {
  return request({
    url: '/say/saying',
    params: { id }
  })
}
