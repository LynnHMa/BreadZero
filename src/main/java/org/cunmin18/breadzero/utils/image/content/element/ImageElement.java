package org.cunmin18.breadzero.utils.image.content.element;

import lombok.Getter;
import lombok.Setter;
import org.cunmin18.breadzero.utils.image.content.transform.Position;
import org.cunmin18.breadzero.utils.image.content.transform.Scale;
import org.cunmin18.breadzero.utils.image.ImageUtils;

import java.awt.*;
import java.awt.image.BufferedImage;

@Getter
@Setter
/// 图片元素
public class ImageElement extends Element{

    /// 当前图片
    protected BufferedImage image;

    /// 图片尺寸
    protected Scale scale;

    public ImageElement(Position position, Scale scale, String url) {
        this.position = new Position(position);
        this.scale = new Scale(scale);
        if(!url.isEmpty()){
            this.image = ImageUtils.getImage(url,true);
        }
        else{
            this.image = new BufferedImage(scale.getWidth(),scale.getHeight(),BufferedImage.TYPE_INT_ARGB);
        }
    }
    public ImageElement(int x,int y,int width,int height,String url){
        this.position = new Position(x,y);
        this.scale = new Scale(width,height);
        if(!url.isEmpty()){
            this.image = ImageUtils.getImage(url,true);
        }
        else{
            this.image = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        }
    }
    public ImageElement(int x,int y,BufferedImage image){
        this.position = new Position(x,y);
        this.scale = new Scale(image.getWidth(),image.getHeight());
        this.image = image;
    }
    public ImageElement(Position position,BufferedImage image){
        this.position = new Position(position);
        this.scale = new Scale(image.getWidth(),image.getHeight());
        this.image = image;
    }

    public void render(Graphics2D g2d){
        if(this.image != null){
            this.image = ImageUtils.scaleImage(image,scale.getWidth(),scale.getHeight());
            g2d.drawImage(image,position.getX(),position.getY(),image.getWidth(),image.getHeight(),null);
        }
        else System.out.println("image is null!");
    }
    public Graphics2D getG2d(){
        return image.createGraphics();
    }
}
