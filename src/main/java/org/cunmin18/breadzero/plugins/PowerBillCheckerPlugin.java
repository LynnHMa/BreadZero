package org.cunmin18.breadzero.plugins;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.cunmin18.breadzero.utils.qq.BotHelper;
import org.cunmin18.breadzero.utils.sit_power_bill.PowerBillRequester;
import org.cunmin18.breadzero.yamls.config.PowerBillCheckerConfig;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/// SIT电费提示插件
@Component
public class PowerBillCheckerPlugin extends BotPlugin {
    private static final long remainingUserID = 2040266560;
    private static final Random RANDOM = new Random();
    private long lastExecuteTime = 0;
    public static long currentPollDuration = 0;

    /// 获取随机轮询间隔
    private static long getRandomCheckDuration() {
        var powerBillConfig = PowerBillCheckerConfig.getInstance();
        var minMin = powerBillConfig != null ? powerBillConfig.getMin_check_min_duration() : 1;
        var maxMin = powerBillConfig != null ? powerBillConfig.getMax_check_min_duration() : 2;
        //return 5000;
        return (long) minMin * 60 * 1000 + RANDOM.nextInt(maxMin * 60 * 1000);
    }

    private static long getRandomRecheckDuration(){
        return (long) 60 * 1000 + RANDOM.nextInt(30 * 1000);
    }

    @PostConstruct
    public void initPollTask() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this::actualPollTask, 0, 1, TimeUnit.SECONDS);
        currentPollDuration = getRandomCheckDuration();
    }

    @SneakyThrows
    private void actualPollTask() {
        try{
            long now = System.currentTimeMillis();
            if (now - lastExecuteTime < currentPollDuration) {
                return;
            }
            checkAndAlertPowerBill(BotHelper.getBot(), 0);
            lastExecuteTime = now;
        }
        catch (Exception e){
            System.out.println("轮询电费出现错误：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /// 遍历配置文件的所有群订阅的UP主，REQUEST获取其动态
    public static int checkAndAlertPowerBill(Bot bot, long givenGroupId) {
        /// 加载配置文件
        var powerBillCheckerConfig = PowerBillCheckerConfig.getInstance();

        if(powerBillCheckerConfig == null || !powerBillCheckerConfig.isEnabled()) return 1;
        /// 加载群列表
        var groupInfoList = powerBillCheckerConfig.getGroup_power_bill();
        if(groupInfoList == null || groupInfoList.isEmpty()){
            return 1;
        }

        /// 逐个宿舍查询电费
        for (int i = 0; i < groupInfoList.size(); i++) {
            var groupInfo = groupInfoList.get(i);
            var groupId = Long.parseLong(groupInfo.getGroup_id());
            try {
                if(givenGroupId == 0 || groupId == givenGroupId) {
                    var rooms = groupInfo.getChecking_rooms();
                    if(rooms == null || rooms.isEmpty()) {
                        continue;
                    }
                    for (int j = 0; j < rooms.size(); j++) {
                        var room = rooms.get(j);
                        var buildId = RoomInfoHandler.getBuildId(room);
                        var roomId = RoomInfoHandler.getRoomId(room);
                        PowerBillRequester.postForPowerBill(buildId,roomId).whenComplete((powerBillInfo, throwable) -> {

                            if(powerBillInfo == null){
                                return;
                            }

                            var code = powerBillInfo.getRetcode();
                            var powerBillValue = powerBillInfo.getRestElecDegree();
                            var success = code == 0 && powerBillValue >= 0;
                            if(success){
                                /// 电费低则艾特管理员播报
                                if(powerBillValue <= powerBillCheckerConfig.getLowest_alert_power_bill()){
                                    var alertMsg = MsgUtils.builder().at(remainingUserID).text(room + " 电费告急： " + powerBillValue).build();
                                    bot.sendGroupMsg(groupId,alertMsg,false);
                                }
                                else{
                                    var powerBillMsg = MsgUtils.builder().text(room + " | 当前电费 | " + powerBillValue).build();
                                    bot.sendGroupMsg(groupId,powerBillMsg,false);
                                }
                                currentPollDuration = getRandomCheckDuration();
                            }
                            else{
                                var unknownErrorMsg = MsgUtils.builder().text("查询电费时出现错误：" + powerBillInfo.getRetcode()).build();
                                bot.sendGroupMsg(groupId,unknownErrorMsg,false);
                                currentPollDuration = getRandomCheckDuration();
                            }

                            /// 切换宿舍要等待一段时间
                            try {
                                Thread.sleep(3000);
                            }
                            catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }).join();
                    }
                }
            }
            catch (Exception e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
                System.out.println("轮询时出现异常：" + e.getMessage());
                return 3;
            }
        }
        return 0;
    }
    static class RoomInfoHandler{
        private static final Pattern ROOM_PATTERN = Pattern.compile("10(?<buildId>\\d{1,2})(?<roomId>\\d{3})");

        public static int getBuildId(String room){
            String cleanRoom = room.replaceAll("\\D", "");
            var matcher = ROOM_PATTERN.matcher(cleanRoom);
            if(matcher.find()){
                return Integer.parseInt(matcher.group("buildId"));
            }
            else return -999;
        }

        public static int getRoomId(String room){
            String cleanRoom = room.replaceAll("\\D", "");
            var matcher = ROOM_PATTERN.matcher(cleanRoom);
            if(matcher.find()){
                return Integer.parseInt(matcher.group("roomId"));
            }
            else return -999;
        }
    }
}
