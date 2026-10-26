package org.cunmin18.breadzero.plugins;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.SneakyThrows;
import org.cunmin18.breadzero.BreadZeroApplication;
import org.cunmin18.breadzero.utils.bilibili.dynamic.content.BilibiliContentProperty;
import org.cunmin18.breadzero.utils.bilibili.cookie.LoginHelper;
import org.cunmin18.breadzero.utils.bilibili.dynamic.BilibiliAuthorInfoLoader;
import org.cunmin18.breadzero.utils.qq.MsgExt;
import org.cunmin18.breadzero.utils.sit_power_bill.CaptchaHelper;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;
import org.cunmin18.breadzero.yamls.cathe.CacheType;
import org.cunmin18.breadzero.yamls.config.BilibiliDynamicsPusherConfig;
import org.cunmin18.breadzero.yamls.config.BotSettingsConfig;
import org.cunmin18.breadzero.yamls.config.PowerBillCheckerConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.regex.Pattern;

/// 指令模块
enum CommandPart{
    DEFAULT,
    BILIBILI_DYNAMICS_PUSHER,
    CACHE,
    OP,
    MODULE
}

/// 指令插件
@Component
public class CommandManagerPlugin extends BotPlugin {

    /// 当接收到群消息
    @SneakyThrows
    @Override
    public int onGroupMessage(Bot bot, GroupMessageEvent event) {
        /// 加载配置文件实例
        var config = BilibiliDynamicsPusherConfig.getInstance();
        var settingConfig = BotSettingsConfig.getInstance();
        var bilibiliCacheStorage = BilibiliCacheStorage.getInstance();

        /// 获取消息
        var message = event.getMessage();

        /// 识别空格并给消息分段
        var section = message.split(" ",-1);

        /// 群号
        var groupId = event.getGroupId();

        /// 发送者QQ号
        var userId = event.getUserId();

        /// 是否保存配置文件
        var saveConfig = false;

        /// 是否为管理员 不是则跳过
        if(!BotSettingsConfig.getInstance().getOwners().contains(userId)) return 0;

        /// 消息是否为指令
        if(section.length == 0) return 0;
        if(!section[0].startsWith("/")) return 1;

        /// 要回复的消息 初始为空
        StringBuilder replyText = new StringBuilder();
        var builtMessage = "";

        /// 根据开头判断指令对应的模块
        switch(section[0]){

            /// 动态推送插件指令
            case "/bdp" -> {
                var action = section.length >= 2 ? section[1] : "";
                var user = section.length >= 3 ? section[2] : "";
                /// 判断是否用户信息为UID
                var uidGiven = !user.isEmpty() && Pattern.matches("(\\d*)", user);
                var uid = uidGiven ? user : BilibiliAuthorInfoLoader.getAuthorUid(user);
                switch (action) {
                    /// 添加订阅
                    case "add" ->{
                        /// UID为空 返回帮助
                        if (uid.isEmpty()) {
                            sendHelpGroupMessage(bot, groupId, event.getUserId(), CommandPart.BILIBILI_DYNAMICS_PUSHER);
                            return 0;
                        }

                        /// 读取UP主其他信息
                        var name = uidGiven ? BilibiliAuthorInfoLoader.getAuthorName(uid) : user;
                        var avatarUrl = BilibiliAuthorInfoLoader.getAuthorAvatarURL(uid);

                        /// 是否已经订阅过此UP主
                        if (hasSubscribedUser(uid, groupId)) {
                            replyText = new StringBuilder("\n你已经订阅过此UP主").append(name).append("(UID: ").append(uid).append(")了！");
                        }
                        else {
                            /// 是否为缓存的UP主 不是则缓存
                            if(!BilibiliAuthorInfoLoader.isCachedAuthor(uid)){
                                if(!name.startsWith("[ERROR]") && !avatarUrl.startsWith("[ERROR]") && !name.isEmpty() && !avatarUrl.isEmpty()) {
                                    var youtuber = new BilibiliContentProperty.YoutuberInfo(name,uid,avatarUrl);
                                    youtuber.cache();
                                }
                            }

                            /// 是否出现错误
                            if (name.startsWith("[ERROR]")) {
                                var errorCode = name.replaceAll("\\[ERROR]", "");
                                replyText = switch (name.replaceAll("\\[ERROR]", "")) {
                                    case "-799" ->
                                            new StringBuilder("\n订阅UP主(UID: " + uid + ")失败，请求冷却期，请稍后重试！" + errorCode);
                                    default ->
                                            new StringBuilder("\n订阅UP主(UID: " + uid + ")失败，未找到该用户！" + errorCode);
                                };
                            }
                            /// 没有错误 正常订阅
                            else {
                                var groupInfo = findGroupInfoFromConfig(groupId);
                                var groupInfoList = new ArrayList<>(config.getGroup_info_list());
                                if (groupInfo != null) {
                                    groupInfoList.remove(groupInfo);
                                }
                                var subscribedYoutubers = groupInfo != null && groupInfo.getSubscribed_youtubers() != null ? new ArrayList<>(groupInfo.getSubscribed_youtubers()) : new ArrayList<String>();
                                subscribedYoutubers.add(uid);
                                groupInfoList.add(new BilibiliDynamicsPusherConfig.GroupInfo(subscribedYoutubers, groupId.toString()));
                                config.setGroup_info_list(groupInfoList);

                                var avatarCQCode = !avatarUrl.isEmpty() ? MsgExt.getImageCQCode(avatarUrl) : "";
                                replyText = new StringBuilder("\n订阅UP主 " + name + "(UID: " + uid + ")成功！\n" + avatarCQCode);

                                saveConfig = true;
                            }
                        }
                    }
                    /// 删除订阅
                    case "remove" ->{
                        /// UID为空 返回帮助
                        if (uid.isEmpty()) {
                            sendHelpGroupMessage(bot, groupId, event.getUserId(), CommandPart.BILIBILI_DYNAMICS_PUSHER);
                            return 0;
                        }
                        /// UP不在订阅列表
                        if (!hasSubscribedUser(uid, groupId)) {
                            replyText = new StringBuilder("\n此UP主(UID: " + uid + ")不在订阅列表内！");
                        }
                        /// 取消订阅
                        else {
                            /// 读取UP主其他信息
                            var name = uidGiven ? BilibiliAuthorInfoLoader.getAuthorName(uid) : user;
                            var avatarUrl = BilibiliAuthorInfoLoader.getAuthorAvatarURL(uid);

                            var groupInfo = findGroupInfoFromConfig(groupId);
                            var groupInfoList = new ArrayList<>(config.getGroup_info_list());
                            if (groupInfo != null) {
                                groupInfoList.remove(groupInfo);
                            }
                            var subscribedYoutubers = groupInfo != null ? new ArrayList<>(groupInfo.getSubscribed_youtubers()) : new ArrayList<String>();
                            subscribedYoutubers.remove(uid);
                            groupInfoList.add(new BilibiliDynamicsPusherConfig.GroupInfo(subscribedYoutubers, groupId.toString()));
                            config.setGroup_info_list(groupInfoList);

                            var avatarCQCode = !avatarUrl.isEmpty() ? MsgExt.getImageCQCode(avatarUrl) : "";
                            replyText = new StringBuilder("\n取消订阅UP主 " + name + "(UID: " + uid + ")成功！\n" + avatarCQCode);
                            saveConfig = true;
                        }
                    }
                    /// 查阅订阅的UP列表
                    case "list" ->{
                        var groupInfo = findGroupInfoFromConfig(groupId);
                        var subscribedYoutubers = groupInfo != null ? new ArrayList<>(groupInfo.getSubscribed_youtubers()) : new ArrayList<String>();
                        if(!subscribedYoutubers.isEmpty()){
                            replyText = new StringBuilder("\n该群（" + groupId + "）订阅名单：\n");
                            for (var youtuber : subscribedYoutubers) {
                                var name = BilibiliAuthorInfoLoader.getAuthorName(youtuber);
                                var avatarUrl = BilibiliAuthorInfoLoader.getAuthorAvatarURL(youtuber);
                                var avatarCQCode = !avatarUrl.isEmpty() ? MsgExt.getImageCQCode(avatarUrl) : "";
                                replyText.append("\n").append(name).append(" - ").append("UID: ").append(youtuber).append("\n").append(avatarCQCode);
                            }
                        }
                        else{
                            replyText = new StringBuilder("\n该群还没有订阅的UP主！");
                        }
                    }
                    /// 格式错误 返回帮助消息
                    default ->{
                        sendHelpGroupMessage(bot, groupId, event.getUserId(), CommandPart.BILIBILI_DYNAMICS_PUSHER);
                        return 0;
                    }
                }
                /// 根据情况判定是否保存配置文件
                if (saveConfig) {
                    BreadZeroApplication.saveAllConfig();
                }

                /// 构造消息体并发送
                builtMessage = MsgUtils.builder().at(event.getUserId()).text(replyText.toString()).build();
                if (builtMessage.isEmpty()) return 0;
                bot.sendGroupMsg(groupId, builtMessage, false);
            }

            /// 机器人管理员
            case "/op" ->{
                /// 需要的操作
                var op_action = section.length >= 2 ? section[1] : "";

                /// 需要操作的用户QQ
                var op_user = section.length >= 3 ? Long.parseLong(section[2]) : 0L;

                /// 管理员列表
                var owners = settingConfig.getOwners();

                switch (op_action) {
                    /// 添加管理员
                    case "add" ->{
                        /// 判断QQ号格式是否正确
                        if(op_user < 10001){
                            sendHelpGroupMessage(bot,groupId, event.getUserId(),CommandPart.OP);
                            return 0;
                        }
                        /// 已经是管理员
                        if(owners.contains(op_user)){
                            replyText = new StringBuilder("\n你已经添加过此管理员(" + op_user + ")了！");
                        }
                        else{
                            var newOwners = new ArrayList<>(owners);
                            newOwners.add(op_user);
                            settingConfig.setOwners(newOwners);
                            replyText = new StringBuilder("\n添加管理员 " + op_user + "成功！");
                            saveConfig = true;
                        }
                    }
                    /// 删除管理员
                    case "remove" -> {
                        /// 判断QQ号格式是否正确
                        if(op_user < 10001){
                            sendHelpGroupMessage(bot,groupId, event.getUserId(),CommandPart.OP);
                            return 0;
                        }
                        /// 不是管理员
                        if(!owners.contains(op_user)){
                            replyText = new StringBuilder("\n此用户(" + op_user + ")不是管理员！");
                        }
                        else{
                            var newOwners = new ArrayList<>(owners);
                            newOwners.remove(op_user);
                            settingConfig.setOwners(newOwners);
                            replyText = new StringBuilder("\n删除管理员 " + op_user + "成功！");
                            saveConfig = true;
                        }
                    }
                    /// 管理员名单
                    case "list" ->{
                        replyText = new StringBuilder("\n管理员（全局）名单：\n");
                        for(var owner : owners){
                            replyText.append("\n").append("QQ: ").append(owner);
                        }
                    }
                    /// 格式错误 返回帮助
                    default -> {
                        sendHelpGroupMessage(bot,groupId,event.getUserId(),CommandPart.OP);
                        return 0;
                    }
                }

                /// 根据需要保存管理员配置文件
                if(saveConfig){
                    BreadZeroApplication.saveAllConfig();
                }

                /// 构造消息
                builtMessage = MsgUtils.builder().at(event.getUserId()).text(replyText.toString()).build();
                if(builtMessage.isEmpty()) return 0;
                bot.sendGroupMsg(groupId,builtMessage,false);
            }

            /// 重载配置文件
            case "/reload"->{
                BreadZeroApplication.loadAllConfig();
                var reloadMsg = MsgUtils.builder().at(event.getUserId()).text("\n配置文件重载成功！").build();
                bot.sendGroupMsg(groupId,reloadMsg,false);
                return 0;
            }

            /// 清除缓存
            case "/cache" ->{
                var action = section.length >= 2 ? section[1] : "";
                var part = section.length >= 3 ? section[2] : "";
                if(action.equals("clear") && !part.isEmpty()){
                    var cleanPart = CacheType.NULL;
                    switch(part){
                        case "0" -> cleanPart = CacheType.ALL;
                        case "1" -> cleanPart = CacheType.DYNAMIC_INFO;
                        case "2" -> cleanPart = CacheType.YOUTUBER_INFO;
                    }
                    if(cleanPart != CacheType.NULL){
                        var clearMsg = MsgUtils.builder().at(event.getUserId()).text("\n缓存文件删除成功！TYPE: " + cleanPart).build();
                        bot.sendGroupMsg(groupId,clearMsg,false);
                        BilibiliCacheStorage.clean(cleanPart);
                        return 0;
                    }
                }
                sendHelpGroupMessage(bot,groupId,event.getUserId(),CommandPart.CACHE);
                return 0;
            }

            /// 模块管理
            case "/module" -> {
                var module = section.length >= 2 ? section[1] : "";
                var enabled = section.length < 3 || Boolean.parseBoolean(section[2]);
                var moduleText = "";
                var resultText = enabled ? "开启" : "关闭";
                switch (module) {
                    case "bdp" -> {
                        var bilibiliConfig = BilibiliDynamicsPusherConfig.getInstance();
                        bilibiliConfig.setEnabled(enabled);
                        moduleText = "B站动态推送";
                    }
                    case "power" -> {
                        var powerBillCheckerConfig = PowerBillCheckerConfig.getInstance();
                        powerBillCheckerConfig.setEnabled(enabled);
                        moduleText = "电费预警";
                    }
                    default -> {
                        sendHelpGroupMessage(bot,event.getGroupId(),event.getUserId(),CommandPart.MODULE);
                        return 0;
                    }
                }
                replyText = new StringBuilder("\n" + moduleText + "功能已" + resultText + "!");
                builtMessage = MsgUtils.builder().at(event.getUserId()).text(replyText.toString()).build();
                bot.sendGroupMsg(groupId,builtMessage,false);
                BreadZeroApplication.saveAllConfig();
            }

            /// 验证
            case "/captcha" -> {
                var captcha = section.length >= 2 ? section[1] : "";
                if(CaptchaHelper.inCaptcha){
                    CaptchaHelper.receivedCaptcha(captcha);
                    replyText = new StringBuilder("验证成功，正在尝试登录OA！");
                }
                else if(captcha.equals("spawn")){
                    CaptchaHelper.captcha();
                    replyText = new StringBuilder("已生成验证码并私信发送至OWNERS！");
                }
                builtMessage = MsgUtils.builder().at(event.getUserId()).text(replyText.toString()).build();
                bot.sendGroupMsg(groupId,builtMessage,false);
            }

            /// 未知指令 返回帮助
            default -> {
                sendHelpGroupMessage(bot,event.getGroupId(),event.getUserId(),CommandPart.DEFAULT);
                return 0;
            }

        }
        return super.onGroupMessage(bot, event);
    }

    @Override
    public int onPrivateMessage(Bot bot, PrivateMessageEvent event) {
        try{
            /// 获取消息
            var message = event.getMessage();

            /// 识别空格并给消息分段
            var section = message.split(" ",-1);

            /// 发送者QQ号
            var userId = event.getUserId();

            /// 是否为管理员 不是则跳过
            if(!BotSettingsConfig.getInstance().getOwners().contains(userId)) return 0;

            /// 消息是否为指令
            if(section.length == 0) return 0;
            if(!section[0].startsWith("/")) return 1;
            var replyText = new StringBuilder();
            switch(section[0]){
                case "/bdp" -> {
                    var action = section.length >= 2 ? section[1] : "";
                    switch(action){
                        case "login" ->{
                            var method = section.length >= 3 ? section[2] : "-c";
                            if(method.equals("-c")){
                                replyText = new StringBuilder("已生成登录二维码，请前往后台进行扫码登陆！");
                                LoginHelper.printQRCode();
                            }
                            else if(method.equals("-pc")){
                                replyText = new StringBuilder("请扫描二维码登录！\n");
                                var qr_code_url = LoginHelper.generateQRCodeImageFile();
                                if(qr_code_url == null) {
                                    System.out.println("QR CODE IS NULL!");
                                    return 0;
                                }
                                replyText.append(MsgExt.getImageCQCode(qr_code_url));
                            }
                        }
                    }
                }
            }
            bot.sendPrivateMsg(userId, replyText.toString(),false);
            return super.onPrivateMessage(bot, event);
        }
        catch (Exception e){
            System.out.println("接收私聊消息时出现错误：" + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    /// 生成登录二维码

    /// 是否已经订阅
    private boolean hasSubscribedUser(String uid,long givenGroupId){
        var groupInfoList = BilibiliDynamicsPusherConfig.getInstance().getGroup_info_list();
        if(groupInfoList.isEmpty()) return false;
        for (int i = 0; i < groupInfoList.size(); i++) {
            var groupInfo = groupInfoList.get(i);
            var groupId = Long.parseLong(groupInfo.getGroup_id());
            if(groupId == givenGroupId){
                var youtubers = groupInfo.getSubscribed_youtubers();
                if(youtubers == null) return false;
                return youtubers.contains(uid);
            }
        }
        return false;
    }

    /// 从配置文件加载群信息
    private BilibiliDynamicsPusherConfig.GroupInfo findGroupInfoFromConfig(long givenGroupId){
        var config = BilibiliDynamicsPusherConfig.getInstance();
        var groupInfoList = config.getGroup_info_list();
        if(groupInfoList.isEmpty()) return null;
        for (int i = 0; i < groupInfoList.size(); i++) {
            var groupInfo = groupInfoList.get(i);
            var groupId = Long.parseLong(groupInfo.getGroup_id());
            if(groupId == givenGroupId) return groupInfo;
        }
        return null;
    }

    /// 发送帮助消息
    private void sendHelpGroupMessage(Bot bot, long groupId, long userId, CommandPart commandPart){
        var help_text = new StringBuilder("\n");
        switch(commandPart){
            case BILIBILI_DYNAMICS_PUSHER:
                help_text.append("""
                        Bilibili-Dynamics-Pusher 插件帮助
                        /bdp add <UID/昵称> 订阅UP主
                        /bdp remove <UID/昵称> 取消订阅UP主
                        /bdp list 查看目前订阅的UP主""");
                break;
            case OP:
                help_text.append("""
                        BOT-OWNERS 插件帮助
                        /op add <qq号> 添加管理员（全局）
                        /op remove <qq号> 删除管理员（全局）
                        /op list 查看管理员列表（全局）""");
                break;
            case CACHE:
                help_text.append("""
                        CACHE-MANAGER 插件帮助
                        /cache clear <id> 清理缓存文件
                        - <id>:
                            0: 全部缓存
                            1: 动态缓存
                            2: UP主信息缓存""");
                break;
            case MODULE:
                help_text.append("""
                        MODULE-MANAGER 帮助
                        /module <module_name> <boolean> 开启/关闭指定模块""");
                break;
            default:
                help_text.append("""
                        BreadZero 帮助
                        /bdp 查看bilibili动态推送插件帮助
                        /op 查看管理员列表功能帮助
                        /reload 重载配置文件
                        /cache 查看缓存相关帮助""");
                break;
        }
        var sendMsg = MsgUtils.builder().at(userId).text(help_text.toString()).build();
        bot.sendGroupMsg(groupId,sendMsg,false);
    }

}
