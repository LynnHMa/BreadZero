package org.cunmin18.breadzero.utils.bilibili.cookie.content;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmRefreshCookieBody {
    private int code;
    private String message;
    private int ttl;
}
