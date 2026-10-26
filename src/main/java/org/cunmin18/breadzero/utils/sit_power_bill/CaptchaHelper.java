package org.cunmin18.breadzero.utils.sit_power_bill;
import org.cunmin18.breadzero.utils.qq.BotHelper;
import org.cunmin18.breadzero.utils.qq.MsgExt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.cunmin18.breadzero.plugins.PowerBillCheckerPlugin.checkAndAlertPowerBill;

public class CaptchaHelper {
    public static boolean inCaptcha = false;
    public static void remindingCaptcha(){
        var bot = BotHelper.getBot();
        if(bot == null) return;

        MsgExt.sendMessageToOwners(bot,"OA需要验证码，请发送/captcha spawn开始验证！");
    }

    public static CompletableFuture<Boolean> captcha() {
        var bot = BotHelper.getBot();
        if(bot == null) return CompletableFuture.completedFuture(false);
        inCaptcha = true;
        var completed = new AtomicBoolean(false);

        LoginRequester.refreshPWDSaltAndLt().thenAccept(success -> {
            if (success) {
                String base64Image = LoginRequester.downloadCaptchaBase64();
                if (base64Image != null) {
                    String cqCode = "[CQ:image,file=base64://" + base64Image + "]";
                    MsgExt.sendMessageToOwners(bot, "验证码已生成，请尽可能在30s内完成验证！输入 /captcha <验证码>\n" + cqCode);
                } else {
                    MsgExt.sendMessageToOwners(bot, "验证码图片获取失败！");
                }
            } else {
                MsgExt.sendMessageToOwners(bot, "无法获取登录页流水号！");
            }
        });

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!completed.get() && inCaptcha) {
                MsgExt.sendMessageToOwners(bot, "OA验证已取消！");
                inCaptcha = false;
            }
            return true;
        });
    }

    public static void receivedCaptcha(String captchaStr){
        if(inCaptcha){
            System.out.println("已收到人工验证码：" + captchaStr);
            // 把收到的验证码赋值给核心逻辑
            LoginRequester.captcha = captchaStr;
            LoginRequester.needCaptcha = false;
            inCaptcha = false;

            // 收到验证码后，直接触发登录流程！(此时因为 captcha 不为空，它会自动跳过刷新网页)
            LoginRequester.loginToRefreshCASTGC().thenAccept(loginSuccess -> {
                if (loginSuccess) {
                    System.out.println("人工验证成功，正在查电费...");
                    // 登录成功了再查电费
                    checkAndAlertPowerBill(BotHelper.getBot(), 0);
                } else {
                    MsgExt.sendMessageToOwners(BotHelper.getBot(), "验证码错误或登录失败，请重新触发！");
                }
            });
        }
    }
}
