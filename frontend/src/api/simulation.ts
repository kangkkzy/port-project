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

// ================= 后台管理 =================
export const resetSimulation = () => request.post('/admin/reset');
export const initSimulation = () => request.post('/admin/init');
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
// 已实现的业务流程测试（与后端对应）
export const testTruckDelivery = () => request.post('/test/truck-delivery');  // DSCH 卸船
export const testQcLoading = () => request.post('/test/qc-loading');        // LOAD 装船
export const testAscUnloading = () => request.post('/test/asc-unloading');  // DLVR 提箱
export const testFullLoading = () => request.post('/test/full-loading');    // 完整装船流程

export const testYardShift = () => request.post('/test/yard-shift');        // YARD_SHIFT 移箱
export const testRecv = () => request.post('/test/recv');                   // RECV 收箱
export const testDirectIn = () => request.post('/test/direct-in');          // DIRECT_IN 直进
export const testDirectOut = () => request.post('/test/direct-out');        // DIRECT_OUT 直提

// QC/ASC 双向移动测试
export const testQcHorizontalVertical = () => request.post('/test/qc-horizontal-vertical');   // QC 水平+垂直
export const testAscHorizontalVertical = () => request.post('/test/asc-horizontal-vertical'); // ASC 水平+垂直

// ================= 健康检查 =================
export const healthCheck = () => request.get('/health/ping');