package controller;

import common.Result;
import lombok.extern.slf4j.Slf4j;
import model.dto.request.KDataSnapReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.sync.KDataSyncService;

@RestController
@RequestMapping("/data")
@Slf4j
public class KDataController {

    @Autowired
    private KDataSyncService kDataSyncService;

    @PostMapping("/snap")
    public Result receiveSnapshot (@RequestBody KDataSnapReq request) {
        log.info("收到全量快照请求, ID: {}, 时间: {}", request.getReqId(), request.getSendTime());
        try {
            kDataSyncService.handleSnapshot(request);
            return Result.success("同步成功");
        } catch (Exception e) {
            log.error("处理快照失败", e);
            return Result.error(500, "处理失败: " + e.getMessage());
        }
    }
}