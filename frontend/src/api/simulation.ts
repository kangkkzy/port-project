import request from './request.ts';

// ================= 状态查询 =================
// 获取当前仿真快照 (设备坐标、状态等)
export const getSnapshot = () => request.get('/state/snapshot');

// ================= 指令控制 =================
// 单步推进下一个事件
export const stepNextEvent = () => request.post('/command/step/next-event');

// 复合推进：下发指令后推下一步
export const stepWithCommands = (data: any) => request.post('/command/stepWithCommands', data);

// ================= 后台管理 =================
// 重置仿真系统
export const resetSimulation = () => request.post('/admin/reset');

// 装载场景配置 (需要传入初始化的 JSON 数据)
export const loadScenario = (data: any) => request.post('/admin/load', data);

// ================= 日志查询 =================
// 查询事件流水
export const getEvents = (sinceSimTime: number = 0) => request.get('/events', { params: { since: sinceSimTime } });

// 查询错误/死循环日志
export const getErrors = (sinceSimTime: number = 0) => request.get('/errors', { params: { since: sinceSimTime } });
// 下发集卡移动指令
export const moveTruck = (data: any) => request.post('/command/truck/move', data);
// 下发桥吊/龙门吊移动指令
export const moveCrane = (data: any) => request.post('/command/crane/move', data);