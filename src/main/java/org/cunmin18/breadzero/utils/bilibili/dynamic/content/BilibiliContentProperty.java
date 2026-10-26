package org.cunmin18.breadzero.utils.bilibili.dynamic.content;

import com.google.gson.JsonObject;
import lombok.*;
import org.cunmin18.breadzero.utils.string.JsonUtils;
import org.cunmin18.breadzero.utils.string.StringUtils;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;

import java.util.ArrayList;

public class BilibiliContentProperty {

    /*表情信息*/
    @Getter
    @Setter
    public static class EmojiInfo{

        public enum EmojiSize{
            /// 大表情
            BIG,
            /// 小表情
            SMALL,
        }

        /// 表情大小类型
        private EmojiSize emojiSizeType;

        /// 表情图片的URL
        private String url;

        public EmojiInfo(EmojiSize emojiSizeType,String url){
            this.emojiSizeType = emojiSizeType;
            this.url = url;
        }
        public EmojiInfo(JsonObject emojiJson){
            var infoJsonExt = JsonUtils.of(emojiJson);
            var emojiSize = infoJsonExt.getAsInt("size");
            this.url = infoJsonExt.getAsString("icon_url");
            this.emojiSizeType = emojiSize < 2 ? EmojiSize.SMALL : EmojiSize.BIG;
        }
    }

    /*UP主信息*/
    @Setter
    @Getter
    @ToString
    @NoArgsConstructor
    public static class YoutuberInfo{
        /*JSON类型，便于解析*/
        public enum YoutuberInfoJsonType{
            MODULE_AUTHOR
        }

        /// UP昵称
        private String name;

        /// UP主UID
        private String uid;

        /// UP主头像URL
        private String avatarUrl;

        public YoutuberInfo(JsonObject infoJson,YoutuberInfoJsonType infoType) {
            var infoJsonExt = JsonUtils.of(infoJson);
            switch (infoType) {
                case MODULE_AUTHOR -> {
                    this.name = infoJsonExt.getAsString("name");
                    this.uid = infoJsonExt.getAsString("mid");
                    this.avatarUrl = infoJsonExt.getAsString("face");
                    return;
                }
            }
            this.name = "";
            this.uid = "";
            this.avatarUrl = "";
        }
        public YoutuberInfo(String name,String uid,String avatarUrl){
            this.name = name;
            this.uid = uid;
            this.avatarUrl = avatarUrl;
        }

        /*将信息缓存到YML，便于之后读取*/
        public void cache(){
            var newInfoList = new ArrayList<>(BilibiliCacheStorage.getInstance().getYoutuber_infos());
            for (int i = 0; i < newInfoList.size(); i++) {
                var youtuberInfo =  newInfoList.get(i);
                if(youtuberInfo.getUid().equals(uid)) return;
            }
            newInfoList.add(this);
            BilibiliCacheStorage.getInstance().setYoutuber_infos(newInfoList);
            BilibiliCacheStorage.save();
        }
    }

    /*动态信息*/
    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class DynamicInfo{

        /*JSON类型，便于解析*/
        public enum DynamicInfoJsonType{
            ITEM_ELEMENT,
            DETAIL_ITEM
        }

        /// 动态ID
        private String id;

        /// 动态类型
        private String type;

        /// 发布时间戳
        private long pub_ts;

        /// 发布时间文字 根据时间戳转换
        private String timeStr;

        public DynamicInfo(String id, String type, long pub_ts) {
            this.id = id;
            this.type = type;
            this.pub_ts = pub_ts;
            this.timeStr = StringUtils.transformTimestampToReadableString(pub_ts);
        }

        public DynamicInfo(JsonObject infoJson, DynamicInfo.DynamicInfoJsonType infoType) {
            var infoJsonExt = JsonUtils.of(infoJson);
            switch (infoType) {
                case ITEM_ELEMENT -> {
                    this.id = infoJsonExt.getAsString("id_str");
                    this.type = infoJsonExt.getAsString("type");
                    this.pub_ts = infoJsonExt
                                    .getAsExt("modules")
                                    .getAsExt("module_author")
                                    .getAsLong("pub_ts");
                }
                //仅能识别图文
                case DETAIL_ITEM -> {
                    this.id = infoJsonExt.getAsString("id_str");
                    var typeToInt = infoJsonExt.getAsInt("type");
                    switch (typeToInt) {
                        //0: 图文
                        case 0:
                            this.type = "DYNAMIC_TYPE_DRAW";
                            break;
                        //1: 非图文
                        default:
                            this.type = "UNKNOWN_DYNAMIC_TYPE";
                            break;
                    }

                    this.pub_ts = infoJsonExt
                            .getAsExt("modules")
                            .getAsExt("module_author")
                            .getAsLong("pub_ts");
                }
                default -> {
                    this.id = "";
                    this.type = "";
                    this.pub_ts = 0;
                }
            }
            this.timeStr = StringUtils.transformTimestampToReadableString(pub_ts);
        }

        /// 缓存动态 可用于防止重复推送
        public void cache(){
            var newInfoList = new ArrayList<>(BilibiliCacheStorage.getInstance().getDynamic_infos());
            for (int i = 0; i < newInfoList.size(); i++) {
                var dynamicInfo =  newInfoList.get(i);
                if(dynamicInfo.getId().equals(id)) return;
            }
            newInfoList.add(this);
            BilibiliCacheStorage.getInstance().setDynamic_infos(newInfoList);
            BilibiliCacheStorage.save();
        }
    }

    /*视频信息*/
    @Getter
    @Setter
    public static class VideoInfo{
        /// 稿件标题
        private final String title;

        /// 稿件简介
        private final String description;

        /// BV号
        private final String bvid;

        /// 稿件时长
        private final String duration;

        /// 稿件封面URL
        private final String coverUrl;

        public VideoInfo(String title, String bvid, String description, String duration, String coverUrl) {
            this.title = title;
            this.bvid = bvid;
            this.description = description;
            this.duration = duration;
            this.coverUrl = coverUrl;
        }

        public VideoInfo(JsonObject videoInfoJson){
            var infoJsonExt = JsonUtils.of(videoInfoJson);
            this.title = infoJsonExt.getAsString("title");
            this.description = infoJsonExt.getAsString("desc");
            this.bvid = infoJsonExt.getAsString("bvid");
            this.coverUrl = infoJsonExt.getAsString("cover");
            this.duration = infoJsonExt.getAsString("duration_text");
        }
    }
}

