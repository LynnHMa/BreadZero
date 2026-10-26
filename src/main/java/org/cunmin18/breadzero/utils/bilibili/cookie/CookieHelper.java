package org.cunmin18.breadzero.utils.bilibili.cookie;

import org.cunmin18.breadzero.BreadZeroApplication;
import org.cunmin18.breadzero.utils.bilibili.requester.BilibiliRefreshCookieRequester;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;

public class CookieHelper {
    public static String getCookieParam(String cookie,String name){
        var params = cookie.split("[?;]",-1);
        for(String param : params){
            if(param.startsWith(name)){
                var value = param.split("=",2);
                if(value.length == 2) return value[1];
            }
        }
        return null;
    }
    public static void cacheCookie(String newCookie){
        var bilibiliCatheStorage = BilibiliCacheStorage.getInstance();
        bilibiliCatheStorage.setCookie(newCookie);
        BreadZeroApplication.saveAllConfig();
    }
    public static int checkCookie(){
        return BilibiliRefreshCookieRequester.checkIfCookieShouldRefreshByRequest();
    }
    public static boolean refreshCookie() throws Exception {
        var correspondPath = CorrespondPathHelper.getCorrespondPath();
        if(correspondPath.isEmpty()) {
            System.out.println("Failed to get correspond path!");
            return false;
        }
        var refresh_csrf = BilibiliRefreshCookieRequester.getRefreshCSRFByRequest(correspondPath);
        if(refresh_csrf == null) {
            return false;
        }
        var newCookie = BilibiliRefreshCookieRequester.getRefreshCookie();
        if(newCookie == null) return false;
        return BilibiliRefreshCookieRequester.confirmRefreshCookie();
    }
}
