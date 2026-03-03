import axios from 'axios';

// 告诉前端，后端的地址在哪里
const service = axios.create({
    baseURL: 'http://localhost:8080/sim', // 对应你后端的 @RequestMapping("/sim/...")
    timeout: 10000 // 请求超时时间：10秒
});

// 拦截器：专门处理你后端 Result.java 返回的数据格式
service.interceptors.response.use(
    (response) => {
        const res = response.data;
        // 根据你后端的 Result 对象，code 为 200 代表成功
        if (res.code === 200) {
            // 返回完整响应，保留 msg 字段供调用方使用
            // 调用方可通过 .msg 获取成功提示，通过 .data 获取业务数据
            return res;
        } else {
            console.error('API 报错啦:', res.msg);
            return Promise.reject(new Error(res.msg || 'Error'));
        }
    },
    (error) => {
        console.error('网络连接出错了:', error);
        return Promise.reject(error);
    }
);

export default service;