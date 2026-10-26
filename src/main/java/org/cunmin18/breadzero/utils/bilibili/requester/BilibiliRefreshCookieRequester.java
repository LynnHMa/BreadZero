package org.cunmin18.breadzero.utils.bilibili.requester;

import com.google.gson.Gson;
import org.cunmin18.breadzero.utils.bilibili.cookie.CookieHelper;
import org.cunmin18.breadzero.utils.bilibili.cookie.content.*;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;
import org.jsoup.Jsoup;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Objects;

import static org.cunmin18.breadzero.utils.Content.USER_AGENT;
import static org.cunmin18.breadzero.utils.Content.gson;

public class BilibiliRefreshCookieRequester {
    private final static RestTemplate restTemplate = new RestTemplate();

    private static String stored_refresh_csrf;
    private static final String check_cookie_available = "https://api.bilibili.com/x/web-interface/nav";

    private static final String refresh_csrf_url = "https://www.bilibili.com/correspond/1/";

    private static final String refresh_cookie_url = "https://passport.bilibili.com/x/passport-login/web/cookie/refresh";

    private static final String confirm_cookie_refresh_url = "https://passport.bilibili.com/x/passport-login/web/confirm/refresh";

    private static final String generate_QR_code_url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate";

    private static final String update_QR_code_state_url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key={qrcode_key}";

    private static final String get_buvid_url = "https://api.bilibili.com/x/frontend/finger/spi";

    private static HttpHeaders getHeaderParams(){
        var bilibiliCathe = BilibiliCacheStorage.getInstance();
        if(bilibiliCathe == null) return null;
        var headerParams = new HttpHeaders();
        headerParams.put("User-Agent", Collections.singletonList(USER_AGENT));
        headerParams.add("Cookie",bilibiliCathe.getCookie());
        headerParams.add("Referer","https://www.bilibili.com/");
        headerParams.add("Origin", "https://www.bilibili.com");
        headerParams.add("Host", "api.bilibili.com");
        return headerParams;
    }

    public static int checkIfCookieShouldRefreshByRequest(){
        var requestBody = restTemplate.exchange(check_cookie_available, HttpMethod.GET, new HttpEntity<>(getHeaderParams()), String.class).getBody();
        var checkCookieBody = gson.fromJson(requestBody, CheckCookieBody.class);
        if(checkCookieBody == null) {
            System.out.println("检查Cookie时出现未知错误！");
            return -999;
        }
        if(checkCookieBody.getMessage().equals("OK")) return 0;
        return checkCookieBody.getCode();
    }

    public static String getRefreshCSRFByRequest(String correspondPath){
        var requestBody = restTemplate.exchange(refresh_csrf_url + correspondPath, HttpMethod.GET, new HttpEntity<>(getHeaderParams()), String.class).getBody();
        if (requestBody != null) {
            var newRefreshCSRF = get1Name(requestBody);
            if(newRefreshCSRF == null) {
                System.out.println("获取REFRESH_CSRF失败！响应体：" + requestBody);
                return null;
            }
            stored_refresh_csrf = newRefreshCSRF;
            return stored_refresh_csrf;
        }
        else {
            return null;
        }
    }

    public static String getRefreshCookie(){
        var bilibiliCathe = BilibiliCacheStorage.getInstance();
        var cookie = bilibiliCathe.getCookie();
        MultiValueMap<String, String> bodyParams = new LinkedMultiValueMap<>();
        bodyParams.add("csrf", CookieHelper.getCookieParam(cookie,"bili_jct"));
        bodyParams.add("refresh_csrf",stored_refresh_csrf);
        bodyParams.add("source","main_web");
        bodyParams.add("refresh_token",bilibiliCathe.getRefresh_token());

        var request = restTemplate.exchange(refresh_cookie_url, HttpMethod.POST, new HttpEntity<>(bodyParams,getHeaderParams()), String.class);
        var requestHeaders = request.getHeaders();
        var cookieHeaders = requestHeaders.get(HttpHeaders.COOKIE);
        var newCookie = new StringBuilder();
        if (cookieHeaders != null) {
            for(var cookieHeader : cookieHeaders){
                var attributes = cookieHeader.split(";");
                var keyValue = attributes[0];
                newCookie.append(keyValue);
            }
            CookieHelper.cacheCookie(newCookie.toString());
            return newCookie.toString();
        }
        System.out.println("new cookie is null!");
        return null;
    }

    public static boolean confirmRefreshCookie(){
        var bilibiliCathe = BilibiliCacheStorage.getInstance();
        var cookie = bilibiliCathe.getCookie();
        MultiValueMap<String, String> bodyParams = new LinkedMultiValueMap<>();
        bodyParams.add("csrf", CookieHelper.getCookieParam(cookie,"bili_jct"));
        bodyParams.add("refresh_token",bilibiliCathe.getRefresh_token());
        var request = restTemplate.exchange(confirm_cookie_refresh_url, HttpMethod.POST, new HttpEntity<>(bodyParams,getHeaderParams()), String.class);
        var requestBody = request.getBody();
        var confirmBody = gson.fromJson(requestBody, ConfirmRefreshCookieBody.class);
        if(confirmBody == null) return false;
        return confirmBody.getCode() == 0 && confirmBody.getMessage().equals("0") && confirmBody.getTtl() == 1;
    }

    private static String get1Name(String html) {
        var doc = Jsoup.parse(html);
        var oneNameElement = doc.selectFirst("[class=1-name]");

        if (oneNameElement != null) {
            return oneNameElement.text();
        }
        else return null;
    }

    public static LoginQRCodeBody.Data getQRCodeData(){
        var requestBody = restTemplate.exchange(generate_QR_code_url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class).getBody();
        return Objects.requireNonNull(gson.fromJson(requestBody, LoginQRCodeBody.class)).getData();
    }

    public static LoginQRCodeStateBody.Data getQRCodeState(String qrcode_key){
        var requestBody = restTemplate.exchange(update_QR_code_state_url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class,qrcode_key).getBody();
        return Objects.requireNonNull(gson.fromJson(requestBody, LoginQRCodeStateBody.class)).getData();
    }

    public static GetBuvidBody.Data getBuvidData(){
        var requestBody = restTemplate.exchange(get_buvid_url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class).getBody();
        return Objects.requireNonNull(gson.fromJson(requestBody, GetBuvidBody.class)).getData();
    }

}
