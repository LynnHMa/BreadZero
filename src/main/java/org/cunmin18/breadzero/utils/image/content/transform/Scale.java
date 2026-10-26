package org.cunmin18.breadzero.utils.image.content.transform;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Scale {
    private int width;
    private int height;

    public Scale(Scale scale){
        this.width = scale.getWidth();
        this.height = scale.getHeight();
    }

    public Scale(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public Scale(int size) {
        this.width = size;
        this.height = size;
    }
    public static Scale ZERO(){
        return new Scale(0,0);
    }
}
