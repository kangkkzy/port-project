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
            // 兼容两类成功返回：
            // 1) Result.success(msg, data) -> data 为对象/数组（如 snapshot）
            // 2) Result.success(msg) -> data 为空，msg 才是有效信息（如 test 接口）
            return res.data ?? res.msg ?? res;
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