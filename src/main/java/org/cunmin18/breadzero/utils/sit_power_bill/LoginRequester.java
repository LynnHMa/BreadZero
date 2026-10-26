package org.cunmin18.breadzero.utils.sit_power_bill;

import lombok.Getter;
import lombok.Setter;
import org.cunmin18.breadzero.utils.qq.MsgExt;
import org.cunmin18.breadzero.yamls.config.PowerBillCheckerConfig;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import static org.cunmin18.breadzero.utils.Content.USER_AGENT;
import static org.cunmin18.breadzero.utils.sit_power_bill.PowerBillRequester.HTTP_CLIENT;

public class LoginRequester {
    private static final String AUTH_URL = "https://authserver.sit.edu.cn/authserver";
    private static final String LOGIN_URL = AUTH_URL + "/login?service=https://ecard.sit.edu.cn/epay/j_spring_cas_security_check";
    public static final String CAPTCHA_URL = AUTH_URL + "/captcha.html";

    private static String lt;
    private static String salt;
    private static String execution;
    public static String captcha;

    public static boolean needCaptcha = false;

    public static synchronized CompletableFuture<Boolean> refreshPWDSaltAndLt(){
        var authServerRequest = HttpRequest.newBuilder()
                .uri(URI.create(LOGIN_URL))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(authServerRequest, HttpResponse.BodyHandlers.ofString()).thenApply((response) -> {
            var html = response.body();
            if(response.statusCode() == 200){
                salt = matchSalt(html);
                lt = matchLt(html);
                execution = matchExecution(html);
                return true;
            }
            return false;
        });
    }

    public static synchronized CompletableFuture<Boolean> loginToRefreshCASTGC(){
        if (captcha != null && !captcha.isEmpty()) {
            // 如果 captcha 有值，说明是人工打码传进来的，直接 POST，跳过刷新网页！
            return doSubmitLoginPost();
        }

        // 正常没验证码的时候：先刷新网页拿流水号，再去 POST
        return refreshPWDSaltAndLt().thenCompose((success) -> {
            if (!success) return CompletableFuture.completedFuture(false);
            return doSubmitLoginPost();
        });
    }

    private static CompletableFuture<Boolean> doSubmitLoginPost() {
        try {
            String password = SitCasEncryptor.encryptPassword(salt, PowerBillCheckerConfig.getInstance().getPassword());
            var username = PowerBillCheckerConfig.getInstance().getUsername();

            String formData = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                    "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8) +
                    "&captchaResponse=" + captcha +
                    "&lt=" + URLEncoder.encode(lt, StandardCharsets.UTF_8) +
                    "&dllt=userNamePasswordLogin" +
                    "&execution=" + URLEncoder.encode(execution, StandardCharsets.UTF_8) +
                    "&_eventId=submit" +
                    "&rmShown=1";

            // 拼装完参数后，立刻把验证码清空，防止影响下一次正常登录！
            captcha = "";

            var loginRequest = HttpRequest.newBuilder()
                    .uri(URI.create(LOGIN_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .header("Referer", LOGIN_URL)
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            System.out.println("正在发起登录 POST 请求...");

            return HTTP_CLIENT.sendAsync(loginRequest,HttpResponse.BodyHandlers.ofString()).thenApply((response) ->{
                var responseBody = response.body();

                if (response.body().contains("casLoginForm")) {
                    System.out.println("登录被拒绝，重新回到了登录页！请检查密码是否正确！");
                    if(responseBody.contains("请输入验证码") || responseBody.contains("captchaResponse")) {
                        needCaptcha = true;
                        CaptchaHelper.remindingCaptcha();
                    }
                    return false;
                }
                MsgExt.sendMessageToOwners("OA 已登录！");
                return true;
            });
        }
        catch (Exception e) {
            System.out.println("登录时出现错误： " + e.getMessage());
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(false);
    }

    // 让带有 CookieManager 的 HttpClient 自己去下载图片！
    public static String downloadCaptchaBase64() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CAPTCHA_URL))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            // 转化为 Base64
            return Base64.getEncoder().encodeToString(response.body());
        } catch (Exception e) {
            System.out.println("验证码图片下载失败！");
            return null;
        }
    }

    private static String matchExecution(String html){
        var matcher = Pattern.compile("name=[\"']execution[\"'] value=[\"']([^\"']+)[\"']").matcher(html);
        if(matcher.find()) return matcher.group(1);
        return "e1s1";
    }

    private static String matchLt(String html){
        var ltMatcher = Pattern.compile("type=[\"']hidden[\"'] name=[\"']lt[\"'] value=[\"']([^\"']+)[\"']").matcher(html);
        if(ltMatcher.find()){
            System.out.println("发现lt：" + ltMatcher.group(1));
            return ltMatcher.group(1);
        }
        return null;
    }
    private static String matchSalt(String html){
        var saltMatcher = Pattern.compile("var pwdDefaultEncryptSalt = [\"']([^\"']+)[\"'];").matcher(html);
        if(saltMatcher.find()){
            System.out.println("发现Salt：" + saltMatcher.group(1));
            return saltMatcher.group(1);
        }
        return null;
    }


    @Getter
    @Setter
    public static class LoginData{
        private String username;
        private String password;
        private String captchaResponse;
        private String lt;
        private String dllt = "userNamePasswordLogin";
        private String execution = "e2s1";
        private String _eventId = "submit";
        private String rmShown = "1";

        public LoginData(String username, String password, String captchaResponse,String lt) {
            this.username = username;
            this.password = password;
            this.captchaResponse = captchaResponse;
            this.lt = lt;
        }
    }

    public static class SitCasEncryptor {

        private static final String CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
        private static final SecureRandom RAND = new SecureRandom();

        // 生成指定长度的随机字符串 (对应源码中的 _rds)
        private static String generateRandomString(int length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(CHARS.charAt(RAND.nextInt(CHARS.length())));
            }
            return sb.toString();
        }

        /**
         * Java 版密码加密逻辑
         *
         * @param salt     HTML源码中提取的 pwdDefaultEncryptSalt
         * @param password 你的明文密码
         * @return 加密后可以直接提交的密码字符串
         */
        public static String encryptPassword(String salt, String password) throws Exception {
            if(salt == null || password == null) return null;

            // 1. 生成 16 位随机 IV
            String ivString = generateRandomString(16);

            // 2. 拼接要加密的明文：64位随机字符 + 真实密码
            String contentToEncrypt = generateRandomString(64) + password;

            // 3. 配置 AES 加密器 (AES/CBC/PKCS5Padding 等同于 Dart 的 PKCS7)
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivString.getBytes(StandardCharsets.UTF_8));

            // 4. 执行加密
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(contentToEncrypt.getBytes(StandardCharsets.UTF_8));

            // 5. 将字节数组进行 Base64 编码
            System.out.println("加密后的密码：" + Base64.getEncoder().encodeToString(encryptedBytes));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        }
    }
}
