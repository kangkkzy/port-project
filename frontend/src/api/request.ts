/**
 * request.ts
 *
 * 基于 axios 封装的 HTTP 请求模块，用于与后端 API 通信。
 * 配置了基础 URL、超时时间，并添加了响应拦截器以统一处理后端 Result 格式的数据。
 */

import axios from 'axios';

// 创建 axios 实例，配置基础 URL 和超时时间
const service = axios.create({
    baseURL: 'http://localhost:8080/sim', // 对应后端的 @RequestMapping("/sim/...")
    timeout: 10000 // 请求超时时间：10秒
});

/**
 * 响应拦截器
 * 处理后端返回的统一 Result 包装格式，提取实际数据或抛出错误。
 *
 * 预期后端返回格式：
 * {
 *   code: number,   // 200 表示成功，其他表示失败
 *   msg: string,    // 提示信息
 *   data: any       // 实际数据（可选）
 * }
 */
service.interceptors.response.use(
    (response) => {
        const res = response.data;
        if (res.code === 200) {
            // 成功：优先返回 data 字段，若不存在则返回 msg 或整个响应
            return res.data ?? res.msg ?? res;
        } else {
            // 业务错误：打印错误信息并 reject
            console.error('API 报错啦:', res.msg);
            return Promise.reject(new Error(res.msg || 'Error'));
        }
    },
    (error) => {
        // 网络错误或请求被取消等
        console.error('网络连接出错了:', error);
        return Promise.reject(error);
    }
);

export default service;