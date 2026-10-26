package org.cunmin18.breadzero.utils.sit_power_bill;

import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.cunmin18.breadzero.utils.Content.USER_AGENT;
import static org.cunmin18.breadzero.utils.Content.gson;

public class PowerBillRequester {
    public static final CookieManager COOKIE_MANAGER = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .cookieHandler(COOKIE_MANAGER)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();


    private static final String INDEX_URL = "https://ecard.sit.edu.cn/epay/electric/load4electricindex";
    private static final String POWER_BILL_URL = "https://ecard.sit.edu.cn/epay/electric/queryelectricbill";

    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";

    private static volatile String csrfToken = null;

    private static synchronized void refreshCSRFToken() {
        if (csrfToken != null) return;

        System.out.println("正在自动换取最新的 CSRF-TOKEN...");
        try {
            HttpRequest indexRequest = HttpRequest.newBuilder()
                    .uri(URI.create(INDEX_URL))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(indexRequest, HttpResponse.BodyHandlers.ofString());

            System.out.println("探路请求走完，最终状态码: " + response.statusCode());

            if (response.statusCode() != 200) {
                throw new RuntimeException("SSO 认证流程失败，最终状态码：" + response.statusCode());
            }

            String html = response.body();
            String token = null;

            // 匹配 Meta 标签 <meta name="_csrf" content="xxx">
            Matcher m1 = Pattern.compile("name=[\"']_csrf[\"']\\s*content=[\"']([^\"']+)[\"']").matcher(html);
            if (m1.find()) token = m1.group(1);

            // 匹配 Input 隐藏域 <input type="hidden" name="_csrf" value="xxx">
            if (token == null) {
                Matcher m2 = Pattern.compile("name=[\"']_csrf[\"']\\s*value=[\"']([^\"']+)[\"']").matcher(html);
                if (m2.find()) token = m2.group(1);
            }

            // 匹配 id 为 csrf 的标签 <meta id="_csrf" content="xxx">
            if (token == null) {
                Matcher m3 = Pattern.compile("id=[\"']_csrf[\"']\\s*content=[\"']([^\"']+)[\"']").matcher(html);
                if (m3.find()) token = m3.group(1);
            }

            if (token != null) {
                csrfToken = token;
                System.out.println("成功提取到动态 CSRF Token: " + csrfToken);
            }
            else {
                //System.out.println("================= HTML 源码开始 =================");
                //System.out.println(html.substring(0, Math.min(html.length(), 3000)));
                //System.out.println("================= HTML 源码结束 =================");

                throw new RuntimeException("未能从网页源码中找到 CSRF Token，请查看控制台输出的源码，手动寻找 token 在哪！");
            }
        } catch (Exception e) {
            throw new RuntimeException("初始化会话失败: " + e.getMessage(), e);
        }
    }

    public static CompletableFuture<PowerBillResponseBody> postForPowerBill(int buildId, int roomId){

        // 检查并确保登录完成
        if(LoginRequester.needCaptcha){
            System.out.println("由于未进行验证码获取，轮询电费为暂停状态！");
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Boolean> ensureLoginFuture;
        if(COOKIE_MANAGER.getCookieStore().getCookies().isEmpty()){
            System.out.println("Cookie 为空，准备走自动登录流程...");
            ensureLoginFuture = LoginRequester.loginToRefreshCASTGC();
        }
        else {
            ensureLoginFuture = CompletableFuture.completedFuture(true);
        }

        // 等登录彻底完成后，再去获取 CSRF Token 和查电费
        return ensureLoginFuture.thenCompose(loginSuccess -> {
            if (!loginSuccess) {
                System.out.println("登录流程失败，放弃本次查电费！");
                COOKIE_MANAGER.getCookieStore().removeAll();
                return CompletableFuture.completedFuture(null);
            }

            try {
                // 此时登录已经成功，拿 CSRF Token
                refreshCSRFToken();
            }
            catch (Exception e) {
                System.out.println(e.getMessage());
                return CompletableFuture.completedFuture(null);
            }

            if(csrfToken == null || csrfToken.isEmpty()) {
                COOKIE_MANAGER.getCookieStore().removeAll();
                return CompletableFuture.completedFuture(null);
            }

            var requestBody = new PowerBillRequestBody(buildId, roomId);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(POWER_BILL_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", CONTENT_TYPE)
                    .header("x-csrf-token", csrfToken)
                    .header("x-requested-with", "XMLHttpRequest")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toForm()))
                    .build();

            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        }).thenApply((resp) -> {
            if(resp == null) return null;

            var responseBody = resp.body();

            if (responseBody == null || !responseBody.trim().startsWith("{")) {
                System.out.println("服务器返回了非 JSON 内容（凭证已失效）！正在强制清空缓存并安排重登...");

                // ★ 必须在这里提前清空！否则下次还会拿着死 Token 去请求！
                COOKIE_MANAGER.getCookieStore().removeAll();
                csrfToken = null;
                return null;
            }

            var powerInfo = gson.fromJson(responseBody, PowerBillResponseBody.class);

            if(powerInfo.getRetcode() != 0){
                System.out.println("查询电费失败：\n Code: " + powerInfo.getRetcode() + "\n Message: " + powerInfo.getRetmsg());
                System.out.println("Response: " + responseBody);

                if (powerInfo.getRetcode() == 403 || powerInfo.getRetcode() == 401) {
                    System.out.println("检测到 403 权限不足，正在重置状态...");
                    COOKIE_MANAGER.getCookieStore().removeAll();
                    csrfToken = null;
                }
            } else {
                System.out.println(buildId + "号楼 | " + roomId + "寝室 | 剩余电费 | " + powerInfo.getRestElecDegree());
            }

            return powerInfo;

        }).exceptionally(ex -> {
            System.out.println("发生严重异常：" + ex.getMessage() + "，正在强制重置所有会话状态！");
            // 天塌下来也要把这俩清空，保证下一次轮询能重新走登录流程！
            COOKIE_MANAGER.getCookieStore().removeAll();
            csrfToken = null;
            return null;
        });
    }

    @Getter
    @Setter
    public static class PowerBillRequestBody {
        private int sysid = 2;
        private int elcarea = 99;
        private int elcbuis;
        private int roomNo;
        public PowerBillRequestBody(int elcbuis,int roomNo){
            this.elcbuis = elcbuis + 1;
            this.roomNo = roomNo;
        }
        public String toForm(){
            return  "sysid=" + sysid +
                    "&elcarea=" + elcarea +
                    "&elcbuis=" + elcbuis +
                    "&roomNo=" + roomNo;
        }
    }

    @Getter
    @Setter
    public static class PowerBillResponseBody {
        private int retcode = 403;
        private String retmsg;
        private boolean multiflag;
        private float restElecDegree;
    }
}
