/**
 * simulation.ts
 *
 * 仿真系统 API 接口集合。
 * 所有与后端仿真相关的请求均通过此模块导出，每个函数对应一个具体的 API 端点。
 * 注意：所有路径已补齐后端的 @RequestMapping 前缀 /sim
 */

import request from './request';

// ================= 状态与地图相关接口 =================

/** 获取当前仿真状态的快照 */
export const getSnapshot = () => request.get('/sim/state/snapshot');

/** 设置仿真播放速度 */
export const setPlaybackSpeed = (speed: number) => request.post('/sim/state/speed', null, { params: { speed } });

/** 获取当前播放速度 */
export const getPlaybackSpeed = () => request.get('/sim/state/speed');

/** 步进到下一个事件（单步执行） */
export const stepNextEvent = () => request.post('/sim/command/step/next-event');

/** 按时间步进仿真（deltaMs 为步进毫秒数，默认 100） */
export const tick = (deltaMs: number = 100) => request.post('/sim/command/tick', { deltaMs });

/** 携带自定义命令的步进操作 */
export const stepWithCommands = (data: any) => request.post('/sim/command/stepWithCommands', data);

/** 重置仿真引擎（清空所有状态） */
export const resetSimulation = () => request.post('/sim/engine/reset');

/** 初始化仿真引擎（加载基础配置） */
export const initSimulation = () => request.post('/sim/engine/init');

/** 加载指定场景（通过 JSON 数据） */
export const loadScenario = (data: any) => request.post('/sim/engine/load', data);

/** 从服务器端 JSON 文件加载场景（fileName 为文件名） */
export const loadScenarioFromJson = (fileName: string) =>
    request.post('/sim/scenario/load', null, { params: { fileName } });

/** 获取地图路径列表（通常用于绘制） */
export const getMapPaths = () => request.get('/sim/map/paths');

/** 获取地图路径的映射关系（如起点到终点的路径） */
export const getMapPathsMap = () => request.get('/sim/map/paths/map');

/** 获取所有转运区（transfer zones）信息 */
export const getTransferZones = () => request.get('/sim/map/transfer-zones');

/** 验证给定设备类型在指定坐标是否可放置 */
export const validatePosition = (deviceType: string, x: number, y: number) =>
    request.get('/sim/map/validate', { params: { deviceType, x, y } });

// ================= 设备控制命令 =================

/** 移动卡车到指定位置 */
export const moveTruck = (data: any) => request.post('/sim/command/truck/move', data);

/** 移动起重机到指定位置 */
export const moveCrane = (data: any) => request.post('/sim/command/crane/move', data);

/** 控制起重机执行操作（如抓取、释放） */
export const operateCrane = (data: any) => request.post('/sim/command/crane/operate', data);

/** 控制围栏的开关状态 */
export const controlFence = (data: any) => request.post('/sim/command/fence', data);

/** 为卡车充电 */
export const chargeTruck = (data: any) => request.post('/sim/command/truck/charge', data);

/** 为设备分配任务 */
export const assignTask = (data: any) => request.post('/sim/command/assign', data);

// ================= 事件与错误查询 =================

/** 获取自指定仿真时间以来的事件列表 */
export const getEvents = (sinceSimTime: number = 0) =>
    request.get('/sim/events', { params: { since: sinceSimTime } });

/** 获取自指定仿真时间以来的错误列表 */
export const getErrors = (sinceSimTime: number = 0) =>
    request.get('/sim/errors', { params: { since: sinceSimTime } });

/** 获取所有历史错误 */
export const getAllErrors = () => request.get('/sim/errors/all');

/** 获取当前被挂起的任务链信息 */
export const getSuspendedChains = () => request.get('/sim/errors/suspended-chains');

// ================= 健康检查 =================

/** 健康检查（测试后端是否存活） */
export const healthCheck = () => request.get('/health/ping');
