package controller;

import common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.dispatch.DispatchService;

@RestController
@RequestMapping("/dispatch")
public class DispatchController {

    @Autowired
    private DispatchService dispatchService;

    // 触发接口: POST /dispatch/run
    @PostMapping("/run")
    public Result run() {
        return dispatchService.runDispatch();
    }
}
