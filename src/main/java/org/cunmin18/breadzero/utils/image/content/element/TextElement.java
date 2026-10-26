package org.cunmin18.breadzero.utils.image.content.element;

import lombok.Getter;
import lombok.Setter;
import org.cunmin18.breadzero.utils.image.content.transform.Position;

import java.awt.*;

@Getter
@Setter
/// 文字元素
public class TextElement extends Element{

    /// 文字内容
    protected String string;

    /// 当前字体
    protected Font font;

    /// 字体颜色
    protected Color color = Color.BLACK;

    public TextElement(int x, int y, String string,Font font){
        this.position = new Position(x,y);
        this.string = string;
        this.font = font;
    }
    public TextElement(Position position, String string,Font font){
        this.position = new Position(position);;
        this.string = string;
        this.font = font;
    }
    public TextElement(int x, int y, String string,Font font,Color color){
        this.position = new Position(x,y);
        this.string = string;
        this.font = font;
        this.color = color;
    }
    public TextElement(Position position, String string,Font font,Color color){
        this.position = new Position(position);;
        this.string = string;
        this.font = font;
        this.color = color;
    }

    @Override
    public void render(Graphics2D g2d) {
        var oriColor = g2d.getColor();
        g2d.setFont(font);
        g2d.setColor(color);
        g2d.drawString(string,position.getX(),position.getY());
        g2d.setColor(oriColor);
    }

    @Override
    public Graphics2D getG2d() {
        return super.getG2d();
    }
}
