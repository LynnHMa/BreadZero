package org.cunmin18.breadzero.utils.bilibili.cookie.content;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginQRCodeBody {
    private int code;
    private String message;
    private int ttl;
    private Data data;

    @Getter
    @Setter
    public static class Data{
        private String url;
        private String qrcode_key;
    }
}
