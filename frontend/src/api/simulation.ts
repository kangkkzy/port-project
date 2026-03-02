import request from './request.ts';

// ================= 状态查询 =================
export const getSnapshot = () => request.get('/state/snapshot');

// ================= 地图配置 =================
export const getMapPaths = () => request.get('/map/paths');
export const getMapPathsMap = () => request.get('/map/paths/map');
export const validatePosition = (deviceType: string, x: number, y: number) =>
    request.get('/map/validate', { params: { deviceType, x, y } });

// ================= 指令控制 =================
export const stepNextEvent = () => request.post('/command/step/next-event');
export const stepWithCommands = (data: any) => request.post('/command/stepWithCommands', data);

// ================= 后台管理 =================
export const resetSimulation = () => request.post('/admin/reset');
export const loadScenario = (data: any) => request.post('/admin/load', data);

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

// ================= 仿真测试场景 =================
export const testDsch = () => request.post('/test/dsch');           // DSCH 卸船
export const testLoad = () => request.post('/test/load');           // LOAD 装船
export const testYardShift = () => request.post('/test/yard-shift');// YARD_SHIFT 移箱
export const testDlvr = () => request.post('/test/dlvr');           // DLVR 提箱
export const testRecv = () => request.post('/test/recv');           // RECV 收箱
export const testDirectIn = () => request.post('/test/direct-in');  // DIRECT_IN 直进
export const testDirectOut = () => request.post('/test/direct-out');// DIRECT_OUT 直提
export const testTaskChain = () => request.post('/test/task-chain');// 🌟 多业务链路流转

// ================= 健康检查 =================
export const healthCheck = () => request.get('/health/ping');