package engine.handler.wi;

import common.consts.EventTypeEnum;
import common.consts.WiStatusEnum;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import model.bo.GlobalContext;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Component;

/**
 * 指令完结
 */
@Component
public class WiCompleteHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.WI_COMPLETE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String wiRefNo = event.getPrimarySubject("WI");
        WorkInstruction doneWi = context.getWorkInstructionMap().get(wiRefNo);
        if (doneWi != null) {
            doneWi.setWiStatus(WiStatusEnum.COMPLETED.getCode());
        }
    }
}
