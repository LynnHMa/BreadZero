package org.cunmin18.breadzero.utils.qq;

import com.mikuac.shiro.core.Bot;
import org.cunmin18.breadzero.yamls.config.BotSettingsConfig;

public class MsgExt {
    /// 获取图片的CQ码
    public static String getImageCQCode(String imageUrl){
        if(imageUrl.startsWith("[ERROR]")) return "";
        return "[CQ:image,file=" + imageUrl + "]";
    }

    public static void sendMessageToOwners(Bot bot,String message){
        if(bot == null) return;
        var breadZeroConfig = BotSettingsConfig.getInstance();
        var owners = breadZeroConfig.getOwners();
        for (var ownerId : owners) {
            bot.sendPrivateMsg(ownerId,message,false);
        }
    }
    public static void sendMessageToOwners(String message){
        sendMessageToOwners(BotHelper.getBot(),message);
    }
}
