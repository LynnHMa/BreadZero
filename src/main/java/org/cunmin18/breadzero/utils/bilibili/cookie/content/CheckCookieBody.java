package org.cunmin18.breadzero.utils.bilibili.cookie.content;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckCookieBody {
    private int code;
    private String message;
    private int ttl;
    private Object data;
}
