import axios from 'axios';

// 告诉前端，后端的地址在哪里
const service = axios.create({
    baseURL: 'http://localhost:8080/sim', // 对应你后端的 @RequestMapping("/sim/...")
    timeout: 10000 // 请求超时时间：10秒
});

// 拦截器：专门处理后端 Result.java 返回的数据格式
service.interceptors.response.use(
    (response) => {
        const res = response.data;
        if (res.code === 200) {
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