package model.entity;

import common.consts.BizTypeEnum;
import java.time.LocalDateTime;

/**
 * 作业指令
 */
public class WorkInstruction {

    // 基础信息
    private String wiRefNo;       // 指令编号
    private String queueName;     // 指令所属的队列
    private String containerId;   // 作业的箱号id
    private BizTypeEnum moveKind; // 指令类型:移动 抓取

    // 流程
    private String fetchCheId;    // 抓箱设备ID
    private LocalDateTime fetchTime;

    private String carryCheId;    // 运输设备ID
    private LocalDateTime carryTime;

    private String putCheId;      // 放箱设备ID
    private LocalDateTime putTime;

    // 位置
    private String fromPos;       // 起点
    private String toPos;         // 终点/当前流转位置

    // 状态
    private String wiStatus;      // 指令总体状态 到达等...
    private String jobStep;       // 指令执行的具体步骤
    // 时间
    private LocalDateTime dispatchTime; // 调度时间
    private LocalDateTime doneTime;     // 完成时间

    //  无参构造函数
    public WorkInstruction() {
    }

    // 基础构造函数 需要指令号
    public WorkInstruction(String wiRefNo) {
        this.wiRefNo = wiRefNo;
    }

    //   Getter 和 Setter 方法

    public String getWiRefNo() {
        return wiRefNo;
    }

    public void setWiRefNo(String wiRefNo) {
        this.wiRefNo = wiRefNo;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public BizTypeEnum getMoveKind() {
        return moveKind;
    }

    public void setMoveKind(BizTypeEnum moveKind) {
        this.moveKind = moveKind;
    }

    public String getFetchCheId() {
        return fetchCheId;
    }

    public void setFetchCheId(String fetchCheId) {
        this.fetchCheId = fetchCheId;
    }

    public LocalDateTime getFetchTime() {
        return fetchTime;
    }

    public void setFetchTime(LocalDateTime fetchTime) {
        this.fetchTime = fetchTime;
    }

    public String getCarryCheId() {
        return carryCheId;
    }

    public void setCarryCheId(String carryCheId) {
        this.carryCheId = carryCheId;
    }

    public LocalDateTime getCarryTime() {
        return carryTime;
    }

    public void setCarryTime(LocalDateTime carryTime) {
        this.carryTime = carryTime;
    }

    public String getPutCheId() {
        return putCheId;
    }

    public void setPutCheId(String putCheId) {
        this.putCheId = putCheId;
    }

    public LocalDateTime getPutTime() {
        return putTime;
    }

    public void setPutTime(LocalDateTime putTime) {
        this.putTime = putTime;
    }

    public String getFromPos() {
        return fromPos;
    }

    public void setFromPos(String fromPos) {
        this.fromPos = fromPos;
    }

    public String getToPos() {
        return toPos;
    }

    public void setToPos(String toPos) {
        this.toPos = toPos;
    }

    public String getWiStatus() {
        return wiStatus;
    }

    public void setWiStatus(String wiStatus) {
        this.wiStatus = wiStatus;
    }

    public String getJobStep() {
        return jobStep;
    }

    public void setJobStep(String jobStep) {
        this.jobStep = jobStep;
    }

    public LocalDateTime getDispatchTime() {
        return dispatchTime;
    }

    public void setDispatchTime(LocalDateTime dispatchTime) {
        this.dispatchTime = dispatchTime;
    }

    public LocalDateTime getDoneTime() {
        return doneTime;
    }

    public void setDoneTime(LocalDateTime doneTime) {
        this.doneTime = doneTime;
    }

}
