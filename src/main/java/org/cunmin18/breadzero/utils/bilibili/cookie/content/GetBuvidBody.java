package org.cunmin18.breadzero.utils.bilibili.cookie.content;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetBuvidBody {
    private int code;
    private Data data;

    @Getter
    @Setter
    public static class Data{
        private String b_3;
        private String b_4;
        private String message;
    }
}
