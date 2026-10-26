package org.cunmin18.breadzero.utils.image.content.element;

import lombok.Getter;
import lombok.Setter;
import org.cunmin18.breadzero.utils.image.content.transform.Position;

import java.awt.*;

@Getter
@Setter
public class Element {

    /// 元素位置
    protected Position position;

    public void render(Graphics2D g2d){
    }
    public Graphics2D getG2d(){
        return null;
    }
}
