package org.cunmin18.breadzero.utils.bilibili.requester;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.cunmin18.breadzero.utils.Content;
import org.cunmin18.breadzero.utils.bilibili.dynamic.ErrorBuilder;
import org.cunmin18.breadzero.utils.string.JsonUtils;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;
import org.cunmin18.breadzero.yamls.config.BilibiliDynamicsPusherConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.cunmin18.breadzero.utils.Content.gson;

@Getter
public class BilibiliAPIRequester {
    private final static RestTemplate restTemplate = new RestTemplate();

    /// 动态列表API
    static final String all_dynamic_url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?host_mid={host_mid}";

    /// 动态详情API
    static final String dynamic_details_url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/opus/detail?id={id}";

    /// 用户详情API
    static final String user_info_url = "https://api.bilibili.com/x/space/acc/info?mid={mid}";

    /// 通过名字获取UID的API
    static final String name_to_uid_url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/name-to-uid?names={names}";

    /// 请求头
    private static HttpEntity<?> getHeaders(){
        var headers = new HttpHeaders();
        headers.add("User-Agent", Content.USER_AGENT);
        headers.add("Cookie", BilibiliCacheStorage.getInstance().getCookie());
        headers.add("Referer","https://space.bilibili.com/");
        headers.add("Origin", "https://www.bilibili.com");
        headers.add("Host", "api.bilibili.com");
        return new HttpEntity<>(headers);
    }

    /// GET 获取动态列表
    public static String requestAllDynamics(String host_mid){
        if (host_mid == null || host_mid.isEmpty()) return "";
        //System.out.println("requested " + host_mid);
        return restTemplate.exchange(all_dynamic_url, HttpMethod.GET, getHeaders(), String.class,host_mid).getBody();
    }

    /// GET 获取动态详情
    public static String requestDynamicDetails(String id){
        if (id == null || id.isEmpty()) return "";
        return restTemplate.exchange(dynamic_details_url, HttpMethod.GET, getHeaders(), String.class,id).getBody();
    }

    /// GET 获取UP主昵称
    public static String getYoutuberNameByRequest(String mid) throws InterruptedException {
        if (mid == null || mid.isEmpty()) return "";
        var request = restTemplate.exchange(user_info_url, HttpMethod.GET, getHeaders(), String.class,mid).getBody();
        var jsonObject = gson.fromJson(request,JsonObject.class);
        if(jsonObject == null) return "";
        var jsonExt = JsonUtils.of(jsonObject);
        var code = jsonExt.getAsInt("code");
        if(code != 0){
            /// 错误码为-799 尝试重新获取
            if(code == -799){
                Thread.sleep(3000);
                return getYoutuberNameByRequest(mid);
            }

            System.out.println("Get name failed!" + jsonObject);
            return "[ERROR]" + code;
        }
        return jsonExt
                .getAsExt("data")
                .getAsString("name");
    }

    /// GET 获取UP主UID
    public static String getYoutuberUidByRequest(String names) throws InterruptedException {
        if (names == null || names.isEmpty()) return "";
        var request = restTemplate.exchange(name_to_uid_url, HttpMethod.GET, getHeaders(), String.class,names).getBody();
        var jsonObject = gson.fromJson(request,JsonObject.class);
        if(jsonObject == null) return "";
        var code = jsonObject.get("code").getAsInt();
        if(code != 0) {
            /// 错误码为-799 尝试重新获取
            if(code == -799){
                Thread.sleep(3000);
                return getYoutuberUidByRequest(names);
            }

            System.out.println("Get uid failed!" + jsonObject);
            return "";
        }
        var jsonExt = JsonUtils.of(jsonObject);
        var uid_list = jsonExt
                .getAsExt("data")
                .getAsElementList("uid_list");
        if(uid_list.isEmpty()) return "";
        return JsonUtils.of(uid_list)
                .getAsExt(0)
                .getAsString("uid");
    }

    /// GET 获取UP主头像URL
    public static String getYoutuberAvatarUrlByRequest(String mid) throws InterruptedException {
        if (mid == null || mid.isEmpty()) return "";
        var request = restTemplate.exchange(user_info_url, HttpMethod.GET, getHeaders(), String.class,mid).getBody();
        var jsonObject = gson.fromJson(request,JsonObject.class);
        if(jsonObject == null) return "";
        var jsonExt = JsonUtils.of(jsonObject);
        var code = jsonExt.getAsInt("code");
        if(code != 0){
            /// 错误码为-799 尝试重新获取
            if(code == -799){
                Thread.sleep(3000);
                return getYoutuberAvatarUrlByRequest(mid);
            }

            System.out.println("Get avatar failed!" + jsonObject);
            return ErrorBuilder.build("[ERROR]" + code);
        }
        return jsonExt
                .getAsExt("data")
                .getAsString("face");
    }
}
