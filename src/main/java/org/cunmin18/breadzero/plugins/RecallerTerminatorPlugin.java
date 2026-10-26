package org.cunmin18.breadzero.plugins;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotPlugin;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.notice.GroupMsgDeleteNoticeEvent;
import org.cunmin18.breadzero.utils.qq.MsgExt;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/// 防撤回插件
@Component
public class RecallerTerminatorPlugin extends BotPlugin {
    /// 记录的消息列表
    public Map<Integer,String> listenedMessages = new HashMap<>();

    /// 记录收到的任何消息，方便之后撤回的时候根据消息ID回溯
    @Override
    public int onAnyMessage(Bot bot, AnyMessageEvent event) {
        listenedMessages.put(event.getMessageId(),event.getMessage());
        return super.onAnyMessage(bot, event);
    }

    /// 检测到消息撤回，找到被撤回的消息，并私信转发给所有机器人管理员
    @Override
    public int onGroupMsgDeleteNotice(Bot bot, GroupMsgDeleteNoticeEvent event) {
        var messageId = event.getMessageId();
        var userId = event.getUserId();
        var groupId = event.getGroupId();
        var recalledMessage = listenedMessages.get(messageId);
        var message = MsgUtils.builder().text("【Recaller Terminator】\n来自群 (" + groupId + ") 的用户 (" + userId + ") \n刚刚撤回了一条消息！！\n可惜被我捕获了\n让我来看看他撤回了什么^_^👇：").build();

        MsgExt.sendMessageToOwners(bot, message);
        MsgExt.sendMessageToOwners(bot, recalledMessage);
        return super.onGroupMsgDeleteNotice(bot, event);
    }
}
