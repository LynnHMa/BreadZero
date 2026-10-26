package org.cunmin18.breadzero.utils.image;

import org.cunmin18.breadzero.utils.bilibili.dynamic.content.MiscContentProperty;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public class ImageUtils {
    /// 通过URL获取图像（可读取缓存）
    public static BufferedImage getImage(String imageUrl, boolean cache){
        BufferedImage result;
        var imageCache = MiscContentProperty.imageCache;
        if(imageCache.containsKey(imageUrl)){
            result = imageCache.get(imageUrl);
            return result;
        }
        try {
            result = ImageIO.read(new URL(imageUrl));
            if(cache) {
                MiscContentProperty.imageCache.put(imageUrl, result);
            }
            return result;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /// 调整图像大小
    public static BufferedImage scaleImage(BufferedImage src, int targetW, int targetH) {
        BufferedImage dest = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = dest.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, targetW, targetH, null);
        g2d.dispose();
        return dest;
    }

    /// 给图片添加圆形遮罩
    public static BufferedImage addCircleMask(BufferedImage originalImage) {
        if (originalImage == null) {
            return null;
        }

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        BufferedImage maskedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = maskedImage.createGraphics();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = Math.min(width, height);
            int x = (width - diameter) / 2;
            int y = (height - diameter) / 2;
            Ellipse2D circle = new Ellipse2D.Double(x, y, diameter, diameter);
            g2.setClip(circle);
            g2.drawImage(originalImage, 0, 0, null);

        } finally {
            g2.dispose();
        }

        return maskedImage;
    }

    /**
     * 从 BufferedImage 中截取中心 x*x 大小的正方形区域
     * @param source 原始图片
     * @param size 截取的正方形边长（x）
     * @return 中心区域的图片，如果输入不合法则返回 null
     */
    public static BufferedImage cropCenterSquare(BufferedImage source, int size) {
        // 入参校验
        if (source == null) {
            throw new IllegalArgumentException("原始图片不能为空");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("截取尺寸必须为正数");
        }

        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();

        // 如果原始图片尺寸小于截取尺寸，直接返回原图（或可根据需求缩放）
        if (srcWidth < size || srcHeight < size) {
            return source;
        }

        // 计算中心区域的起始坐标
        int x = (srcWidth - size) / 2;
        int y = (srcHeight - size) / 2;

        // 截取中心区域（使用原始图片的像素数据创建新图片）
        BufferedImage cropped = new BufferedImage(size, size, source.getType());
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                // 从原始图片读取像素并写入新图片
                cropped.setRGB(i, j, source.getRGB(x + i, y + j));
            }
        }

        return cropped;
    }

    /**
     * 将图片边缘圆化（添加圆角）
     * @param source 原始图片
     * @param cornerRadius 圆角半径（值越大，圆角越明显）
     * @return 边缘圆化后的图片，若输入无效则返回null
     */
    public static BufferedImage makeRoundedCorner(BufferedImage source, int cornerRadius) {
        // 入参校验
        if (source == null) {
            throw new IllegalArgumentException("原始图片不能为空");
        }
        if (cornerRadius < 0) {
            throw new IllegalArgumentException("圆角半径不能为负数");
        }

        int width = source.getWidth();
        int height = source.getHeight();

        // 创建一个带透明通道的图片作为结果（确保圆角区域透明）
        BufferedImage roundedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = roundedImage.createGraphics();

        try {
            // 开启抗锯齿，让边缘更平滑
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // 绘制圆角矩形作为"蒙版"，后续会将原图绘制到这个蒙版内
            RoundRectangle2D roundedRect = new RoundRectangle2D.Float(
                    0, 0, width, height,
                    cornerRadius, cornerRadius  // 圆角的水平和垂直半径
            );
            g2d.setClip(roundedRect);  // 设置裁剪区域为圆角矩形

            // 将原始图片绘制到裁剪区域内（超出圆角的部分会被截断）
            g2d.drawImage(source, 0, 0, width, height, null);

        } finally {
            g2d.dispose();  // 释放资源
        }

        return roundedImage;
    }

    /**
     * 绘制带圆边的白色半透明矩形
     * @param rectWidth 矩形宽度
     * @param rectHeight 矩形高度
     * @param cornerRadius 圆角半径（值越大，圆角越明显）
     * @param alpha 透明度（0.0f完全透明，1.0f完全不透明）
     * @return 包含圆角半透明矩形的图片
     */
    public static BufferedImage drawRoundedRect(
            int rectWidth, int rectHeight,
            int cornerRadius, float alpha,
            Color color) {

        BufferedImage image = new BufferedImage(rectWidth, rectHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            AlphaComposite alphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
            g2.setComposite(alphaComposite);
            g2.setColor(color);
            RoundRectangle2D roundedRect = new RoundRectangle2D.Double(
                    0, 0, rectWidth, rectHeight,
                    cornerRadius, cornerRadius
            );
            g2.fill(roundedRect);

        } finally {
            g2.dispose();
        }

        return image;
    }
}
