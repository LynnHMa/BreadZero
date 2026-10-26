package org.cunmin18.breadzero.plugins;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.cunmin18.breadzero.utils.bilibili.cookie.CookieHelper;
import org.cunmin18.breadzero.utils.bilibili.cookie.LoginHelper;
import org.cunmin18.breadzero.utils.qq.BotHelper;
import org.cunmin18.breadzero.utils.qq.MsgExt;
import org.cunmin18.breadzero.yamls.config.BilibiliDynamicsPusherConfig;
import org.cunmin18.breadzero.utils.bilibili.dynamic.BilibiliGetDynamicsTool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/// Bilibili动态推送插件
@Component
public class BilibiliDynamicsPusherPlugin extends BotPlugin {
    private long lastExecuteTime = 0;
    public static long currentPollDuration = 0;

    /// 获取随机轮询间隔
    private long getRandomPollingDuration() {
        long duration = 5000 + new Random().nextInt(3000) - 1000;
        return Math.max(duration, 1000);
    }

    @PostConstruct
    public void initPollTask() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        /// 每1秒检查一次是否需要执行（实际执行间隔由内部控制）
        executor.scheduleWithFixedDelay(this::actualPollTask, 0, 1, TimeUnit.SECONDS);
        /// 初始化随机间隔
        currentPollDuration = getRandomPollingDuration();
    }

    @SneakyThrows
    private void actualPollTask() {
        try{
            var stateCode = 0;
            long now = System.currentTimeMillis();
            if (now - lastExecuteTime < currentPollDuration) {
                return;
            }
            if(LoginHelper.isShouldLogin() || BotHelper.getBot() == null) {
                return;
            }
            var checkCookie = CookieHelper.checkCookie();
            if(checkCookie != 0){
                currentPollDuration = 1000 * 30;
                LoginHelper.setShouldLogin(checkCookie == -101);
                if(LoginHelper.isShouldLogin()){
                    MsgExt.sendMessageToOwners("B站登录已过期！");
                    System.out.println("登陆过期！");
                }
                else{
                    System.out.println("Cookie失效，正在重新获取！");
                    if(CookieHelper.refreshCookie()){
                        System.out.println("Cookie更新成功！");
                    }
                    else{
                        System.out.println("Cookie更新失败！请查看日志！");
                    }
                }
            }
            else{
                var bot = BotHelper.getBot();
                if(bot == null) return;
                stateCode = refreshAndPushNewDynamics(BotHelper.getBot(), 0);
                switch (stateCode) {
                    /// 正常情况
                    case 0:
                        /// 群列表为空 延长间隔 1min
                    case 1:
                        currentPollDuration = 1000 * 60;
                        break;
                    /// 触发风控校验 大幅延长间隔 30min
                    case 2:
                        currentPollDuration = 1000 * 60 * 30;
                        break;
                    /// 其他情况
                    case 3:
                        currentPollDuration = getRandomPollingDuration();
                        break;
                }
            }
            lastExecuteTime = now;
        }
        catch (Exception e){
            System.out.println("轮询动态出现错误：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /// 遍历配置文件的所有群订阅的UP主，REQUEST获取其动态
    public static int refreshAndPushNewDynamics(Bot bot,long givenGroupId) {
        /// 加载配置文件
        var config = BilibiliDynamicsPusherConfig.getInstance();
        if(!config.isEnabled()) return 3;

        /// 加载群列表
        var groupInfoList = config.getGroup_info_list();
        if(groupInfoList == null || groupInfoList.isEmpty()){
            System.out.println("群列表为空，已将下一次轮询间隔改为一分钟，请尝试订阅UP主。");
            return 1;
        }

        /// 获取UP信息并逐个查看动态
        for (int i = 0; i < groupInfoList.size(); i++) {
            var groupInfo = groupInfoList.get(i);
            var groupId = Long.parseLong(groupInfo.getGroup_id());
            try {
                if(givenGroupId == 0 || groupId == givenGroupId) {
                    var youtubers = groupInfo.getSubscribed_youtubers();
                    if(youtubers == null || youtubers.isEmpty()) {
                        continue;
                    }
                    for (int j = 0; j < youtubers.size(); j++) {
                        var youtuber_uid = youtubers.get(j);
                        var newDynamics = BilibiliGetDynamicsTool.getUserNewDynamics(youtuber_uid);
                        if(newDynamics != null && !newDynamics.isEmpty()){
                            for (int k = 0; k < newDynamics.size(); k++) {
                                var newDynamic = newDynamics.get(k);
                                if (!newDynamic.isEmpty()) {
                                    /// 异常动态
                                    if(newDynamic.startsWith("[ERROR]")){
                                        var errorType = newDynamic.replaceAll("\\[ERROR]","");
                                        if(errorType.startsWith("UNSUPPORTED_TYPE")){
                                            if(newDynamic.split(" ").length >= 3){
                                                var name = newDynamic.split(" ")[1];
                                                var link = newDynamic.split(" ")[2];
                                                var errorMsg = MsgUtils.builder().text(name).text("发布了一条新动态，但该动态类型暂不支持图片形式播报（问就是没写owo）\n链接直达：" + link).build();
                                                bot.sendGroupMsg(groupId, errorMsg, false);
                                                continue;
                                            }
                                        }
                                        else if(errorType.startsWith("RISK_CONTROL_VERIFICATION")){
                                            System.out.println("风控警告!");
                                            /// 暂停轮询
                                            return 2;
                                        }
                                    }

                                    var imageCQCode = MsgExt.getImageCQCode(newDynamic);
                                    bot.sendGroupMsg(groupId, imageCQCode, false);
                                }
                            }
                        }
                        /// 切换UP要等待一段时间
                        Thread.sleep(config.getPolling_duration_per_youtuber());
                    }
                }
                /// 每次切换群要等待一段时间
                Thread.sleep(config.getPolling_duration_per_group());
            }
            catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
                System.out.println("暂停被中断：" + e.getMessage());
                return 3;
            }
        }
        return 0;
    }
}
