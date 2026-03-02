import request from './request.ts';

// ================= 状态查询 =================
// 获取当前仿真快照 (设备坐标、状态等)
export const getSnapshot = () => request.get('/state/snapshot');

// ================= 地图配置 =================
// 获取所有路径配置
export const getMapPaths = () => request.get('/map/paths');

// 获取路径配置Map
export const getMapPathsMap = () => request.get('/map/paths/map');

// 验证位置是否在有效路径上
export const validatePosition = (deviceType: string, x: number, y: number) =>
    request.get('/map/validate', { params: { deviceType, x, y } });

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

// ================= 设备控制 =================
// 下发集卡移动指令
export const moveTruck = (data: any) => request.post('/command/truck/move', data);

// 下发桥吊/龙门吊移动指令
export const moveCrane = (data: any) => request.post('/command/crane/move', data);

// 桥吊操作（起吊/放箱）
export const operateCrane = (data: any) => request.post('/command/crane/operate', data);

// 围栏控制
export const controlFence = (data: any) => request.post('/command/fence', data);

// 集卡充电
export const chargeTruck = (data: any) => request.post('/command/truck/charge', data);

// 任务分配
export const assignTask = (data: any) => request.post('/command/assign', data);

// ================= 错误日志 =================
// 查询错误日志
export const getErrors = (sinceSimTime: number = 0) => request.get('/errors', { params: { since: sinceSimTime } });

// 查询所有错误日志
export const getAllErrors = () => request.get('/errors/all');

// 查询暂停的事件链
export const getSuspendedChains = () => request.get('/errors/suspended-chains');