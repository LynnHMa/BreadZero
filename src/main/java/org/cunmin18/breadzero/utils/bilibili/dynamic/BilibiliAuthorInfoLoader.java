package org.cunmin18.breadzero.utils.bilibili.dynamic;

import org.cunmin18.breadzero.utils.bilibili.requester.BilibiliAPIRequester;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;

public class BilibiliAuthorInfoLoader {

    /// 作者信息是否已缓存
    public static boolean isCachedAuthor(String uid){
        var bilibiliCacheStorage = BilibiliCacheStorage.getInstance();
        var youtubers = bilibiliCacheStorage.getYoutuber_infos();
        if(youtubers != null){
            if(!youtubers.isEmpty()){
                for (int i = 0; i < youtubers.size(); i++) {
                    var youtuber =  youtubers.get(i);
                    if(youtuber.getUid().equals(uid)){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /// 获取作者UID
    public static String getAuthorUid(String name) throws InterruptedException {
        var bilibiliCacheStorage = BilibiliCacheStorage.getInstance();
        var youtubers = bilibiliCacheStorage.getYoutuber_infos();
        if(youtubers != null){
            if(!youtubers.isEmpty()){
                for (int i = 0; i < youtubers.size(); i++) {
                    var youtuber =  youtubers.get(i);
                    if(youtuber.getName().equalsIgnoreCase(name)){
                        return youtuber.getUid();
                    }
                }
            }
        }
        return BilibiliAPIRequester.getYoutuberUidByRequest(name);
    }

    /// 获取作者昵称
    public static String getAuthorName(String uid) throws InterruptedException {
        var bilibiliCacheStorage = BilibiliCacheStorage.getInstance();
        var youtubers = bilibiliCacheStorage.getYoutuber_infos();
        if(youtubers != null){
            if(!youtubers.isEmpty()){
                for (int i = 0; i < youtubers.size(); i++) {
                    var youtuber =  youtubers.get(i);
                    if(youtuber.getUid().equals(uid)){
                        return youtuber.getName();
                    }
                }
            }
        }
        return BilibiliAPIRequester.getYoutuberNameByRequest(uid);
    }

    /// 获取作者头像URL
    public static String getAuthorAvatarURL(String uid) throws InterruptedException {
        var bilibiliCacheStorage = BilibiliCacheStorage.getInstance();
        var youtubers = bilibiliCacheStorage.getYoutuber_infos();
        if(youtubers != null){
            if(!youtubers.isEmpty()){
                for (int i = 0; i < youtubers.size(); i++) {
                    var youtuber =  youtubers.get(i);
                    if(youtuber.getUid().equals(uid)){
                        return youtuber.getAvatarUrl();
                    }
                }
            }
        }
        return BilibiliAPIRequester.getYoutuberAvatarUrlByRequest(uid);
    }
}
