/**
 * 患者端统一请求封装（迭代17 K5）。
 * 网关 28080 → /api/**；Result 结构 { code, message, data }，code===0 视为成功。
 */
const BASE_URL = 'http://localhost:28080/api'

const TOKEN_KEY = 'hospital_token'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY)
}

export function request({ url, method = 'GET', data = {}, needAuth = true }) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    if (needAuth && getToken()) {
      header.Authorization = `Bearer ${getToken()}`
    }
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      success: (res) => {
        const body = res.data || {}
        if (body.code === 0) {
          resolve(body.data)
        } else if (body.code === 1401 || body.code === 1403) {
          clearToken()
          uni.reLaunch({ url: '/pages/profile/index' })
          reject(new Error(body.message || '登录已过期'))
        } else {
          reject(new Error(body.message || `请求失败(${body.code})`))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '网络异常')),
    })
  })
}

/** ---------- 患者端常用接口 ---------- */
/** 登录 */
export const login = (phone, password) =>
  request({ url: '/auth/login', method: 'POST', data: { phone, password }, needAuth: false })

/** 科室列表 */
export const listDepartments = () => request({ url: '/clinic/departments', method: 'GET' })

/** 某科室医生列表 */
export const listDoctors = (departmentId) =>
  request({ url: `/clinic/doctors?departmentId=${departmentId}`, method: 'GET' })

/** 医生某周排班 */
export const listSchedules = (doctorId, weekStart) =>
  request({ url: `/clinic/schedules?doctorId=${doctorId}&weekStart=${weekStart}`, method: 'GET' })

/** 挂号下单 */
export const createAppointment = (data) => request({ url: '/clinic/appointments', method: 'POST', data })

/** 我的预约列表 */
export const myAppointments = (patientId) =>
  request({ url: `/clinic/appointments/list?patientId=${patientId}&pageNo=1&pageSize=20`, method: 'GET' })

/** 支付下单（迭代4 收费） */
export const createPayOrder = (data) => request({ url: '/payment/orders', method: 'POST', data })

/** 院内公告（迭代13） */
export const listNotices = () => request({ url: '/clinic/notices/list?pageNo=1&pageSize=10', method: 'GET' })
