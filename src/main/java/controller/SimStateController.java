package controller;

import common.Result;
import model.bo.GlobalContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sim/state")
public class SimStateController {

    /**
     * 外部算法用来观察当前的地图全貌
     */
    @GetMapping("/all")
    public Result getAllState() {
        return Result.success("查询成功", GlobalContext.getInstance());
    }
}