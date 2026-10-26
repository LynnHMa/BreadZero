package org.cunmin18.breadzero.utils.bilibili.cookie;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.cunmin18.breadzero.utils.bilibili.cookie.content.LoginQRCodeBody;
import org.cunmin18.breadzero.utils.bilibili.cookie.content.LoginQRCodeStateBody;
import org.cunmin18.breadzero.utils.bilibili.requester.BilibiliRefreshCookieRequester;
import org.cunmin18.breadzero.utils.qq.MsgExt;
import org.cunmin18.breadzero.utils.string.FileUtils;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@EnableAsync
public class LoginHelper {
    @Getter
    @Setter
    private static boolean shouldLogin = false;
    private static LoginQRCodeBody.Data currentQRCodeData;
    private static boolean isAvailableQRCode(LoginQRCodeBody.Data data){
        return data != null && data.getUrl() != null && data.getQrcode_key() != null;
    }

    public static boolean generateQRCode(){
        currentQRCodeData = BilibiliRefreshCookieRequester.getQRCodeData();
        return isAvailableQRCode(currentQRCodeData);
    }

    @SneakyThrows
    public static void refreshQRCodeTask(){
        outer:
        while(true){
            var currentQRCodeState = BilibiliRefreshCookieRequester.getQRCodeState(currentQRCodeData.getQrcode_key());
            switch (currentQRCodeState.getCode()){
                case 0:
                    MsgExt.sendMessageToOwners("B站账号登陆成功！");
                    onLogin(currentQRCodeState);
                    break outer;
                case 86038:
                    MsgExt.sendMessageToOwners("二维码已过期，请重新获取！");
                    break outer;
                case 86090:
                    System.out.println("已扫码，请在手机确认！");
                    break;
                case 86101:
                    break;
                default:
                    MsgExt.sendMessageToOwners("未知状态！状态码：" + currentQRCodeState.getCode() + "，请重新获取！");
                    break outer;
            }
            Thread.sleep(3000);
        }
    }

    @SneakyThrows
    public static void printQRCode() {
        if(!generateQRCode()) return;

        var QRCodeUrl = currentQRCodeData.getUrl();

        int width = 30;
        int height = 30;

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = new MultiFormatWriter().encode(QRCodeUrl, BarcodeFormat.QR_CODE, width, height, hints);

        final String BLACK = "\033[40m  \033[0m";
        final String WHITE = "\033[47m  \033[0m";

        System.out.println("请扫描下方二维码登录：");

        for (int i = 0; i < bitMatrix.getWidth() + 2; i++) System.out.print(WHITE);
        System.out.println();

        for (int y = 0; y < bitMatrix.getHeight(); y++) {
            System.out.print(WHITE);

            for (int x = 0; x < bitMatrix.getWidth(); x++) {
                System.out.print(bitMatrix.get(x, y) ? BLACK : WHITE);
            }
            System.out.print(WHITE);
            System.out.println();
        }

        for (int i = 0; i < bitMatrix.getWidth() + 2; i++) System.out.print(WHITE);
        System.out.println();
        CompletableFuture.runAsync(LoginHelper::refreshQRCodeTask);
    }

    @SneakyThrows
    public static String generateQRCodeImageFile(){
        if(!generateQRCode()) return null;

        var QRCodeUrl = currentQRCodeData.getUrl();
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(QRCodeUrl, BarcodeFormat.QR_CODE, 100, 100);
        var fileName = "QRCODE_" + System.currentTimeMillis() + ".png";
        var file = FileUtils.getOutputDirFilePath(fileName);
        MatrixToImageWriter.writeToPath(bitMatrix, "PNG", Paths.get(file));
        CompletableFuture.runAsync(LoginHelper::refreshQRCodeTask);
        return file;
    }

    public static void onLogin(LoginQRCodeStateBody.Data data){
        var bilibiliCacheStorage = BilibiliCacheStorage.getInstance();
        shouldLogin = false;
        var url = data.getUrl();
        var split = url.split("[?]",2);
        if(split.length != 2) return;
        var params = split[1].split("&");
        var newCookie = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            var param = params[i];
            if(notInCookie(param)) continue;
            var group = param.split("=",2);
            if(group.length == 2){
                newCookie.append(group[0]).append("=").append(group[1]).append("; ");
            }
        }
        var buvidData = BilibiliRefreshCookieRequester.getBuvidData();
        newCookie.append("buvid3=").append(buvidData.getB_3()).append("; buvid4=").append(buvidData.getB_4()).append("; ");
        newCookie.append("b_nut=").append(summonBNut()).append(buvidData.getB_4());
        bilibiliCacheStorage.setRefresh_token(data.getRefresh_token());
        CookieHelper.cacheCookie(newCookie.toString().trim());
    }

    private static String summonBNut(){
        long timestamp = System.currentTimeMillis() / 1000;
        return String.valueOf(timestamp);
    }
    private static boolean notInCookie(String param){
        return param.contains("Expires") || param.contains("gourl") || param.contains("first_domain");
    }
}
