package org.cunmin18.breadzero.plugins;

import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.core.BotPlugin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.cunmin18.breadzero.utils.qq.BotHelper;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class UpdateBotPlugin extends BotPlugin {
    @Resource
    private BotContainer botContainer;

    @PostConstruct
    public void initPoll(){
        try{
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(() ->{
                var bots = botContainer.robots;
                for (var bot : bots.values()){
                    BotHelper.setBot(bot);
                    break;
                }
            }, 0, 2, TimeUnit.SECONDS);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
