import request from './request';

// ================= 状态查询 =================
export const getSnapshot = () => request.get('/state/snapshot');

// ================= 地图配置 =================
export const getMapPaths = () => request.get('/map/paths');
export const getMapPathsMap = () => request.get('/map/paths/map');
export const getTransferZones = () => request.get('/map/transfer-zones');
export const validatePosition = (deviceType: string, x: number, y: number) =>
    request.get('/map/validate', { params: { deviceType, x, y } });

// ================= 指令控制 =================
export const stepNextEvent = () => request.post('/command/step/next-event');
export const tick = (deltaMs: number = 100) => request.post('/command/tick', { deltaMs });
export const stepWithCommands = (data: any) => request.post('/command/stepWithCommands', data);

// ================= 后台管理 (已修复404路径问题) =================
// 对应后端 SimAdminController 的 @RequestMapping("/sim/engine")
export const resetSimulation = () => request.post('/engine/reset');
export const initSimulation = () => request.post('/engine/init');
export const loadScenario = (data: any) => request.post('/engine/load', data);

// ================= 场景加载 (JSON文件驱动) =================
// 对应后端 ScenarioController 的 @RequestMapping("/sim/scenario")
export const loadScenarioFromJson = (fileName: string) =>
    request.post('/scenario/load', null, { params: { fileName } });

// ================= 日志查询 =================
export const getEvents = (sinceSimTime: number = 0) => request.get('/events', { params: { since: sinceSimTime } });
export const getErrors = (sinceSimTime: number = 0) => request.get('/errors', { params: { since: sinceSimTime } });
export const getAllErrors = () => request.get('/errors/all');
export const getSuspendedChains = () => request.get('/errors/suspended-chains');

// ================= 设备控制 =================
export const moveTruck = (data: any) => request.post('/command/truck/move', data);
export const moveCrane = (data: any) => request.post('/command/crane/move', data);
export const operateCrane = (data: any) => request.post('/command/crane/operate', data);
export const controlFence = (data: any) => request.post('/command/fence', data);
export const chargeTruck = (data: any) => request.post('/command/truck/charge', data);
export const assignTask = (data: any) => request.post('/command/assign', data);

// ================= 外部算法/场景测试 =================
// 供外部算法统一派发全局任务的接口
export const dispatchAllTasks = () => request.post('/test/dispatch-all');

// ================= 健康检查 =================
export const healthCheck = () => request.get('/health/ping');