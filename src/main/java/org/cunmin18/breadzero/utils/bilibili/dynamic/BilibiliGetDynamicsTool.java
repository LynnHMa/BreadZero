package org.cunmin18.breadzero.utils.bilibili.dynamic;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.cunmin18.breadzero.utils.bilibili.dynamic.content.BilibiliContentProperty;
import org.cunmin18.breadzero.utils.image.content.element.ImageElement;
import org.cunmin18.breadzero.utils.image.content.element.TextElement;
import org.cunmin18.breadzero.utils.image.content.transform.Position;
import org.cunmin18.breadzero.utils.image.content.transform.Scale;
import org.cunmin18.breadzero.utils.bilibili.requester.BilibiliAPIRequester;
import org.cunmin18.breadzero.utils.image.ImageUtils;
import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;
import org.cunmin18.breadzero.yamls.config.BilibiliDynamicsPusherConfig;
import org.cunmin18.breadzero.plugins.BilibiliDynamicsPusherPlugin;
import org.cunmin18.breadzero.utils.string.FileUtils;
import org.cunmin18.breadzero.utils.string.JsonUtils;
import org.cunmin18.breadzero.utils.string.StringUtils;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static org.cunmin18.breadzero.utils.bilibili.dynamic.BilibiliGetDynamicsTool.DynamicRenderer.Dynamic.DynamicContent.Tool.getTextHeight;


@Component
public class BilibiliGetDynamicsTool {
    /// 查询UP主动态列表的动态数 这里写了5 防止某些疯子用动态刷屏
    private static final int getDynamicCount = 5;

    /// 获取新动态的图片URL
    public static List<String> getUserNewDynamics(String host_mid) throws IOException {
        try{
            List<String> result = new ArrayList<>();

            /// GET请求尝试获取全部动态
            var response = BilibiliAPIRequester.requestAllDynamics(host_mid);
            Gson gson = new Gson();
            JsonObject responseJson = gson.fromJson(response, JsonObject.class);
            if(responseJson == null) {
                System.out.println("JsonObject is null!");
                return result;
            }
            var code = responseJson.get("code").getAsString();
            var dataJson = JsonUtils.of(responseJson).getAsObject("data");

            /// 检查返回的Json是否异常
            switch (code) {
                case "-352":
                    System.out.println("访问频繁触发风控校验，将休息一段时间再轮询动态！" + responseJson);
                    result.add(ErrorBuilder.build("RISK_CONTROL_VERIFICATION"));
                    return result;
                case "-101":
                    System.out.println("账号未登录，请重新登录！");
                    result.add(ErrorBuilder.build("LOGIN_FAILED"));
                    return result;
            }

            /// 加载动态列表
            var itemsJsonElementList =  JsonUtils.of(dataJson).getAsElementList("items");

            /// 没动态 直接返回
            if (itemsJsonElementList.isEmpty()) {
                System.out.println("User " + host_mid + " has no dynamics! (maybe..)" + responseJson);
                return result;
            }

            /// 查询UP主的最近几个动态
            var currentPubTs = System.currentTimeMillis();
            for (int i = 0; i < Math.min(getDynamicCount,itemsJsonElementList.size()); i++) {
                var newDynamic = JsonUtils.of(itemsJsonElementList).getObjectAt(i);
                var dynamicInfo = new BilibiliContentProperty.DynamicInfo(newDynamic, BilibiliContentProperty.DynamicInfo.DynamicInfoJsonType.ITEM_ELEMENT);
                var isPushedDynamic = DynamicRenderer.Dynamic.isPushedDynamic(dynamicInfo);
                var pubTs = dynamicInfo.getPub_ts() * 1000;
                var isOldDynamic = currentPubTs - pubTs > BilibiliDynamicsPusherPlugin.currentPollDuration * 3;

                /// 是否为最新动态且未推送过 是则推送
                if(!isPushedDynamic && !isOldDynamic){
                    var renderer = new DynamicRenderer();
                    var renderedImageUrl = renderer.render(newDynamic);
                    result.add(renderedImageUrl);
                }
            }
            return result;
        }
        catch (Exception e){
            System.out.println("出现错误!" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    @Component
    static class DynamicRenderer{
        private final static Gson gson = new Gson();
        private final static Random random = new Random();

        /// 富文本蓝色
        private final static Color atColor = new Color(70, 130, 180, 255);

        /// 当前使用的G2D
        private Graphics2D g2d = null;

        /// 当前渲染的动态
        DynamicRenderer.Dynamic currentDynamic = null;

        /// 当前渲染的动态 正文模块
        Dynamic.DynamicContent currentDynamicContent = null;

        /// 当前渲染的动态 图片模块
        Dynamic.DynamicImages currentDynamicImages = null;

        /// 渲染主方法 返回URL
        public String render(JsonObject newDynamic) throws IOException{
            /// 初始化当前动态
            currentDynamic = new DynamicRenderer.Dynamic();

            /// 获取配置文件实例
            var bilibiliConfig = BilibiliDynamicsPusherConfig.getInstance();

            var modules = JsonUtils.of(newDynamic).getAsExt("modules");
            /// Modules 为 null 可能为转发动态
            if(modules.isNull()) {
                modules = JsonUtils.of(newDynamic);
            }

            /// 创建临时G2D 用于高度估算
            var tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            var tempG2d = tempImg.createGraphics();
            g2d = tempG2d;
            currentDynamic.setG2d(g2d);

            /// 获取作者信息
            var module_authorJson = modules.getAsObject("module_author");
            var youtuberInfo = new BilibiliContentProperty.YoutuberInfo(module_authorJson, BilibiliContentProperty.YoutuberInfo.YoutuberInfoJsonType.MODULE_AUTHOR);
            youtuberInfo.cache();
            currentDynamic.setAuthorInfo(youtuberInfo);

            /// 获取动态信息
            var module_dynamicJson = modules.getAsObject("module_dynamic");
            var dynamicInfo = new BilibiliContentProperty.DynamicInfo(newDynamic, BilibiliContentProperty.DynamicInfo.DynamicInfoJsonType.ITEM_ELEMENT);
            dynamicInfo.cache();
            currentDynamic.setDynamicInfo(dynamicInfo);
            currentDynamicContent = new Dynamic.DynamicContent(module_dynamicJson,currentDynamic);
            currentDynamic.setCurrentDynamicContent(currentDynamicContent);
            var contentAreaHeight = currentDynamicContent.getContentAreaHeight();

            /// 判断动态行为
            var actionText = "   ";
            var dynamicType = dynamicInfo.getType();
            switch (dynamicInfo.getType()) {
                case "DYNAMIC_TYPE_AV" -> actionText = "   投稿了视频";
                case "DYNAMIC_TYPE_DRAW" -> actionText = "   发布了动态";
                case "DYNAMIC_TYPE_FORWARD" -> actionText = "   转发了动态";
            }

            /// 检查是否支持播报 不支持直接返回错误信息直发链接
            if(!Dynamic.isSupportedDynamicType(dynamicType)){
                var link = "https://t.bilibili.com/" + dynamicInfo.getId();
                var errorMsg = ErrorBuilder.build("UNSUPPORTED_TYPE",youtuberInfo.getName() + " " + link);
                System.out.println("不支持的动态类型： " + dynamicType);
                return errorMsg;
            }

            /// 获取图片信息
            var detailJson = currentDynamicContent.getDetailJson();
            currentDynamicImages = new Dynamic.DynamicImages(detailJson,currentDynamic);
            currentDynamic.setCurrentDynamicImages(currentDynamicImages);
            var imageAreaHeight = currentDynamicImages.getImageAreaHeight();

            /// 获取动态视频信息
            var videoAreaHeight = 0;
            var dynamicVideo = new Dynamic.DynamicVideo(false,currentDynamic);
            BilibiliContentProperty.VideoInfo videoInfo = null;
            if(dynamicType.equals("DYNAMIC_TYPE_AV")){
                var videoInfoJson = JsonUtils.of(module_dynamicJson)
                        .getAsExt("major")
                        .getAsObject("archive");
                if(videoInfoJson != null){
                    videoInfo = new BilibiliContentProperty.VideoInfo(videoInfoJson);
                    currentDynamic.setVideoInfo(videoInfo);
                    dynamicVideo = new Dynamic.DynamicVideo(true,currentDynamic);
                    currentDynamic.setCurrentDynamicVideo(dynamicVideo);
                    videoAreaHeight = dynamicVideo.getVideoAreaHeight();
                }
            }

            /// 获取转发动态的信息（如果有）
            var forwardedDynamicImageHeight = 0;
            var forwardedDynamicImageWidth = 0;
            var forwardedDynamicImage = "";
            BufferedImage bufferedForwardedDynamicImage = null;
            if(dynamicType.equals("DYNAMIC_TYPE_FORWARD")){
                var orig = newDynamic.get("orig");
                if(orig != null) {
                    var forwardedRenderer = new DynamicRenderer();
                    forwardedDynamicImage = forwardedRenderer.render(orig.getAsJsonObject());
                }
                if(!forwardedDynamicImage.isEmpty()){
                    bufferedForwardedDynamicImage = ImageIO.read(new File(forwardedDynamicImage));
                    if(bufferedForwardedDynamicImage != null) {
                        bufferedForwardedDynamicImage = ImageUtils.scaleImage(bufferedForwardedDynamicImage,(int)(bufferedForwardedDynamicImage.getWidth() * 0.6f) , (int)(bufferedForwardedDynamicImage.getHeight() * 0.6f));
                        bufferedForwardedDynamicImage = ImageUtils.makeRoundedCorner(bufferedForwardedDynamicImage,35);
                    }
                }
                forwardedDynamicImageHeight = bufferedForwardedDynamicImage != null ? bufferedForwardedDynamicImage.getHeight() : 0;
                forwardedDynamicImageWidth = bufferedForwardedDynamicImage != null ? bufferedForwardedDynamicImage.getWidth() : 0;
            }


            /// 计算出背景总高度
            var backgroundHeightOffset = 530;
            var backgroundHeight = videoAreaHeight + imageAreaHeight + contentAreaHeight + forwardedDynamicImageHeight + backgroundHeightOffset;

            /// 初始化图片
            var result = new ImageElement(0,0,1024,backgroundHeight,"");
            tempG2d.dispose();
            g2d = result.getG2d();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            /// 绘制背景
            var backgroundImageElement = new ImageElement(result.getPosition(),result.getScale(),bilibiliConfig.getBackground_url());
            backgroundImageElement.render(g2d);

            /// 绘制底层白框
            var whiteRoundedRectImage = ImageUtils.drawRoundedRect(backgroundImageElement.getScale().getWidth() - 100, backgroundImageElement.getScale().getHeight() - 100,30,0.55f,Color.white);
            var whiteRoundedRectImageElement = new ImageElement(50,50,whiteRoundedRectImage);
            whiteRoundedRectImageElement.render(g2d);

            /// 绘制UP主信息白框
            var whiteRoundedRectTopImage = ImageUtils.drawRoundedRect(backgroundImageElement.getScale().getWidth() - 100, 190,40,0.55f,Color.white);
            var whiteRoundedRectTopImageElement = new ImageElement(50,50,whiteRoundedRectTopImage);
            whiteRoundedRectTopImageElement.render(g2d);

            /// 绘制用户头像
            var avatarElement = new ImageElement(80,90,100,100,youtuberInfo.getAvatarUrl());
            avatarElement.setImage(ImageUtils.addCircleMask(avatarElement.getImage()));
            avatarElement.render(g2d);

            /// 渲染用户昵称文字
            var userNameTextElement = new TextElement(
                    avatarElement.getPosition().getX() + avatarElement.getScale().getWidth() / 2 + 80,
                    avatarElement.getPosition().getY() + avatarElement.getScale().getHeight() - 50,
                    youtuberInfo.getName(),
                    FontManager.usernameFont);
            userNameTextElement.render(g2d);

            /// 渲染动态时间文字
            var dynamicTimeTextElement = new TextElement(
                    userNameTextElement.getPosition().getX() - 5,
                    userNameTextElement.getPosition().getY() + 55,
                    dynamicInfo.getTimeStr() + actionText,
                    FontManager.dynamicTimeFont,
                    Color.GRAY);
            dynamicTimeTextElement.render(g2d);

            /// 绘制标题（如果有）
            var hasTitle = !currentDynamicContent.getTitleText().isEmpty();
            g2d.setColor(Color.BLACK);
            if(hasTitle){
                var dynamicTitleTextElement = new TextElement(90,305,currentDynamicContent.getTitleText(),FontManager.dynamicTitleFont);
                dynamicTitleTextElement.render(g2d);
            }

            /// 绘制正文
            var dynamicMainPosX = 90;
            var dynamicMainPosY = 360;
            var lineFedText = currentDynamicContent.getLineFedMainText();
            g2d.setColor(Color.BLACK);
            renderMultilineText(lineFedText,dynamicMainPosX,dynamicMainPosY,FontManager.textFont,hasTitle);

            /// 绘制表情
            renderAllEmoji(dynamicMainPosX,dynamicMainPosY,hasTitle);

            /// 绘制图片（如果有）
            if(dynamicType.equals("DYNAMIC_TYPE_DRAW")){
                renderAllImages(hasTitle);
            }

            /// 绘制视频卡片（如果有）
            if(dynamicType.equals("DYNAMIC_TYPE_AV") && videoInfo != null){
                renderVideoCard();
            }

            /// 绘制转发的动态（如果有）
            if(dynamicType.equals("DYNAMIC_TYPE_FORWARD") && bufferedForwardedDynamicImage != null){
                var forwardedDynamicImagePosX = (1024 - forwardedDynamicImageWidth) / 2;
                var forwardedDynamicImagePosY = dynamicMainPosY + contentAreaHeight + 20;
                var forwardedDynamicImageElement = new ImageElement(forwardedDynamicImagePosX,forwardedDynamicImagePosY,bufferedForwardedDynamicImage);
                forwardedDynamicImageElement.render(g2d);
            }

            /// 释放G2D
            g2d.dispose();
            g2d = null;

            /// 设置图片文件昵称，并且拼接输出路径
            /// 输出格式：<统一后的时间> + -BREADZERO- + <随机数>
            var fileName = dynamicInfo.getTimeStr().replaceAll(":","-") + "-BREADZERO-" + random.nextInt(50000);
            var outputPath = FileUtils.getOutputDirFilePath(fileName);
            var file = outputPath + ".png";

            /// 把渲染结果保存为图片 并且返回URL
            ImageIO.write(result.getImage(), "PNG", new File(file));
            return file;
        }

        /// 渲染多行文字
        public void renderMultilineText(String text, int x, int y, Font font,boolean hasTitle) {
            g2d.setFont(font);
            FontMetrics metrics = g2d.getFontMetrics();
            int lineHeight = metrics.getHeight() + 10;

            var specialSpacesS = " ";

            String[] lines = text.replaceAll("\\\\n","\n").split("\n",-1);

            var additionalHeight = 0;
            var specialTexts = currentDynamicContent.getSpecialTexts();

            var renderingRichTextWhichIsLineFed = false;
            var losingRichTextSectionInNextLine = "";
            for (int i = 0; i < lines.length; i++) {
                var l = lines[i];

                /// 如果有大表情，该列内容下移一段距离
                if(i > 0) {
                    var lastL = lines[i - 1];
                    if(lastL.contains("{BigE}") && !l.contains("{BigE}")){
                        additionalHeight += 10;
                    }
                }

                var containBigEmoji = l.contains("{BigE}") && (i != 0 || hasTitle);
                if(containBigEmoji) {
                    additionalHeight += lineHeight;
                }

                /// 这里将表情标记符替换为了空格，形成表情空白区域，区域不渲染任何东西
                int bigEWidth = metrics.stringWidth("{BigE}");
                int EWidth = metrics.stringWidth("{E}");
                int spaceWidth = metrics.stringWidth(specialSpacesS);
                int bigESpaceCount = (int) Math.ceil((double) bigEWidth / spaceWidth);
                int ESpaceCount = (int) Math.ceil((double) EWidth / spaceWidth);

                var l2 = l.replaceAll("\\{BigE}",specialSpacesS.repeat(bigESpaceCount)).replaceAll("\\{E}",specialSpacesS.repeat(ESpaceCount));
                var g2dColor = g2d.getColor();

                /// 是否包含富文本
                if(!specialTexts.isEmpty()){
                    var sectionList = StringUtils.splitStringAndSaveSeparator(l2,specialTexts);
                    if(sectionList.isEmpty()) {
                        System.out.println("no section found");
                        continue;
                    }

                    var sectionWidthInTotal = 0;

                    /// 如果正在渲染上一行缺失的富文本 要重新处理原片段列表的第一个元素，确保除富文本外内容颜色为黑色
                    if(renderingRichTextWhichIsLineFed && !losingRichTextSectionInNextLine.isEmpty() && sectionList.get(0).length() > losingRichTextSectionInNextLine.length()){
                        sectionList.set(0,StringUtils.removeAllOccurrences(sectionList.get(0),losingRichTextSectionInNextLine));
                        sectionList.add(0,losingRichTextSectionInNextLine);
                    }

                    for (int j = 0; j < sectionList.size(); j++) {
                        var section = sectionList.get(j);
                        /// 富文本部分是否因分行被分开 影响检测
                        var lineFedRichText = false;

                        /// 片段为富文本
                        var isRichText = specialTexts.contains(section);

                        /// 检测是否为分行符分开的富文本部分 特殊处理
                        if(j == sectionList.size() - 1 && i < lines.length - 1 && !isRichText && StringUtils.hasStartWithElement(section,specialTexts)){
                            /// 提前获取下一行内容 判断开头是否为富文本缺失部分
                            var nextL = lines[i + 1];
                            var nextL2 = nextL.replaceAll("\\{BigE}",specialSpacesS.repeat(bigESpaceCount)).replaceAll("\\{E}",specialSpacesS.repeat(ESpaceCount));

                            /// 根据末尾富文本前部分 判断可能的富文本整体
                            var bestMatchByContainment = StringUtils.findBestMatchByContainment(section,specialTexts);

                            /// 根据可能的整体，得到末尾富文本缺失的后部分
                            var losingRichTextSection = StringUtils.removeAllOccurrences(bestMatchByContainment, section);
                            var losingRichTextSectionLen = losingRichTextSection.length();
                            var nextSectionList = StringUtils.splitStringAndSaveSeparator(nextL2,specialTexts);

                            /// 获取下一行的第一个片段
                            var firstSectionOfNextLine = !nextSectionList.isEmpty() ? nextSectionList.get(0) : "";

                            /// 从获取的第一个片段截取富文本缺失部分
                            losingRichTextSectionInNextLine = firstSectionOfNextLine.length() > losingRichTextSectionLen ? firstSectionOfNextLine.substring(0,losingRichTextSectionLen) : firstSectionOfNextLine;

                            /// 把两个片段拼接，然后判断是否为富文本内容
                            lineFedRichText = bestMatchByContainment.replaceAll("\n","").equals((section + losingRichTextSectionInNextLine).replaceAll("\n","").replaceAll(" ","")) && !losingRichTextSectionInNextLine.isEmpty();
                            if(lineFedRichText) {
                                renderingRichTextWhichIsLineFed = true;
                            }
                        }

                        /// 富文本为蓝色
                        var color = isRichText || renderingRichTextWhichIsLineFed ? atColor : g2dColor;

                        if(renderingRichTextWhichIsLineFed && j == 0) {
                            renderingRichTextWhichIsLineFed = false;
                            losingRichTextSectionInNextLine = "";
                        }

                        var sectionTextElement = new TextElement(x + sectionWidthInTotal, y + i * lineHeight + additionalHeight,section,font,color);
                        sectionTextElement.render(g2d);
                        sectionWidthInTotal += Dynamic.DynamicContent.Tool.getTextWidth(section,font,g2d);
                    }
                }
                /// 没有富文本 直接渲染
                else{
                    var l2TextElement = new TextElement(x, y + i * lineHeight + additionalHeight,l2,font,g2dColor);
                    l2TextElement.render(g2d);
                }
            }
        }

        /// 绘制全部表情
        public void renderAllEmoji(int dynamicMainPosX,int dynamicMainPosY,boolean hasTitle){

            /// 把表情替代符和普通文本分开 得到字符串列表
            var words = StringUtils.splitStringAndSaveSeparator(currentDynamicContent.getLineFedMainText(), List.of("{BigE}","{E}"));

            /// 获取动态表情信息列表
            var dynamicEmojis = currentDynamicContent.getDynamicEmojis();

            /// 获取分行后的正文
            var lineFedText = currentDynamicContent.getLineFedMainText();

            /// 正文宽度限制
            var textWidth = currentDynamicContent.getTextWidthLimit();

            /// 寻找表情替代符并判断其位置 渲染表情
            if(!dynamicEmojis.isEmpty()){
                /// 混合文本存储器 分行后可判断最后一行文字宽度 总文本高度 计算表情的渲染位置
                var w_t = new StringBuilder();

                /// 表情信息列表的索引
                var emojiInfoTemp = 0;

                for (int i = 0; i < words.size(); i++) {
                    /// 从字符串列表获取片段
                    var w = words.get(i);

                    /// 把片段添加到 混合文本存储器
                    w_t.append(w);

                    /// 判断片段是否为表情替代符 是则继续
                    if(!w.contains("{BigE}") && !w.contains("{E}")){
                        continue;
                    }

                    /// 把文本存储器包含的内容自动分行
                    var lineFedW_t = Dynamic.DynamicContent.Tool.autoLineFeedText(w_t.toString(),FontManager.textFont, textWidth,g2d);

                    /// 把分行后的文本存储器每一行分开 存在新的列表中
                    var lines = StringUtils.splitStringAndSaveSeparator(lineFedW_t, List.of("\n"));

                    /// 从列表获取最后一行内容
                    var bottomLine = !lines.isEmpty() ? lines.get(lines.size() - 1) : lineFedW_t;
                    /// 计算最后一行文字宽度
                    var bottomLineWidth = Dynamic.DynamicContent.Tool.getTextWidth(bottomLine,FontManager.textFont,g2d);

                        /* 考虑到每一行可能会出现大表情，渲染位置要靠下一些，所以在计算位置时要提前获取该行全部内容 */
                    /// 获取最后一行是第几行
                    var whichLine = StringUtils.whichLine(lineFedW_t,w_t.length() - 1);
                    /// 从分行后的文本存储器中获取该行内容
                    var t1 = StringUtils.getALineOfText(lineFedW_t,whichLine);
                    /// 从分行后的总文本中获取该行内容
                    var t2 = StringUtils.getALineOfText(lineFedText,whichLine);
                    /// 把文本存储器最后一行补全，再计算存储器文本高度
                    var dynamicTextHeight = getTextHeight(lineFedW_t.replace(t1,t2),FontManager.textFont,hasTitle,g2d);

                    /// 获取表情信息
                    var emojiInfo = dynamicEmojis.get(emojiInfoTemp);
                    emojiInfoTemp++;
                    /// 表情类型 大/小
                    var emojiSizeType = emojiInfo.getEmojiSizeType();
                    /// 表情的渲染尺寸
                    var emojiSize = emojiSizeType == BilibiliContentProperty.EmojiInfo.EmojiSize.BIG ? 95 : 50;
                    /// 表情位置的偏移 根据类型而定
                    var emojiPosOffsetX = 0;
                    var emojiPosOffsetY = switch (emojiSizeType) {
                        case SMALL -> {
                            emojiPosOffsetX = -52;
                            yield -93;
                        }
                        case BIG -> {
                            emojiPosOffsetX = -105;
                            yield -133;
                        }
                    };

                    /// 表情渲染位置 代入文本存储器中计算的宽高度计算
                    var emojiPosX = bottomLineWidth + dynamicMainPosX + emojiPosOffsetX;
                    var emojiPosY = dynamicTextHeight + dynamicMainPosY + emojiPosOffsetY;

                    /// 开始渲染
                    var emojiImageElement = new ImageElement(emojiPosX,emojiPosY,emojiSize,emojiSize,emojiInfo.getUrl());
                    emojiImageElement.render(g2d);
                }
            }
        }

        /// 绘制全部图片
        public void renderAllImages(boolean hasTitle){
            var imagePosX = 90;
            var imagePosY = 360 + getTextHeight(currentDynamicContent.getLineFedMainText(), FontManager.textFont,hasTitle,g2d);
            var imagePosOffsetX = 0;
            var imagePosOffsetY = 0;
            var imageSize = currentDynamicImages.getImageSize();
            var imageUrlList = currentDynamicImages.getImageUrlList();
            var imageCount = imageUrlList.size();
            for (int i = 0; i < imageCount; i++) {
                imagePosOffsetX = i % 3 * imageSize;
                imagePosOffsetY = i / 3 * imageSize;

                var accurateImagePosX = imagePosX + imagePosOffsetX;
                var accurateImagePosY = imagePosY + imagePosOffsetY;

                /// 动态只转发一次 图片没必要缓存
                var imageUrl = imageUrlList.get(i);
                var image =  ImageUtils.getImage(imageUrl,false);
                /// 如果是宽图，自动调整大小然后截取中间的部分
                var isWideImage = Math.min(image.getWidth(), image.getHeight()) == image.getHeight();
                var n1 = isWideImage ? image.getHeight() : image.getWidth();
                var n2 = (float)n1 / (float)imageSize;

                image = ImageUtils.scaleImage(image, (int)(image.getWidth() / n2), (int)(image.getHeight() / n2));
                var handledImage = ImageUtils.makeRoundedCorner(ImageUtils.cropCenterSquare(image,imageSize),25);

                var imageElement = new ImageElement(accurateImagePosX,accurateImagePosY,handledImage);
                imageElement.render(g2d);
            }
        }

        /// 绘制视频卡片
        public void renderVideoCard(){
            var videoInfo = currentDynamic.getVideoInfo();
            var dynamicVideo = currentDynamic.getCurrentDynamicVideo();
            /// 绘制卡片白框
            var videoWhiteRoundedRectTopImage = ImageUtils.drawRoundedRect(dynamicVideo.getWhiteRoundedRectScale().getWidth(),dynamicVideo.getWhiteRoundedRectScale().getHeight() ,50,0.65f,Color.white);
            var videoWhiteRoundedRectTopImageElement = new ImageElement((1024 - dynamicVideo.getWhiteRoundedRectScale().getWidth()) / 2,dynamicVideo.getCoverPos().getY() - 40,videoWhiteRoundedRectTopImage);
            videoWhiteRoundedRectTopImageElement.render(g2d);

            /// 绘制视频封面 无需缓存
            var videoCoverImage = ImageUtils.getImage(videoInfo.getCoverUrl(), false);
            var videoCoverImageElement = new ImageElement(dynamicVideo.getCoverPos().getX(),dynamicVideo.getCoverPos().getY(),
                    ImageUtils.makeRoundedCorner(ImageUtils.scaleImage(videoCoverImage,dynamicVideo.getCoverScale().getWidth(),dynamicVideo.getCoverScale().getHeight()),30));
            videoCoverImageElement.render(g2d);

            /// 绘制视频标题文字
            g2d.setColor(Color.BLACK);
            renderMultilineText(dynamicVideo.getLineFedVideoTitleText(),dynamicVideo.getTitleTextPos().getX(),dynamicVideo.getTitleTextPos().getY(), FontManager.videoTitleTextFont,false);

            /// 绘制视频简介文字
            g2d.setColor(Color.GRAY);
            renderMultilineText(dynamicVideo.getLineFedVideoDescText(),dynamicVideo.getDescTextPos().getX(),dynamicVideo.getDescTextPos().getY(), FontManager.descFont,false);

            /// 绘制视频时长黑框
            var blackDurationRectImage = ImageUtils.drawRoundedRect(dynamicVideo.getBlackDurationRectScale().getWidth(), dynamicVideo.getBlackDurationRectScale().getHeight(),45,0.75f,Color.BLACK);
            var blackDurationRectImageElement = new ImageElement(dynamicVideo.getBlackDurationRectPos().getX(),dynamicVideo.getBlackDurationRectPos().getY(),blackDurationRectImage);
            blackDurationRectImageElement.render(g2d);

            /// 绘制视频时长文字到黑框上
            var blackDurationTextElement = new TextElement(dynamicVideo.getBlackDurationRectPos().getX() + (dynamicVideo.getBlackDurationRectScale().getWidth() - dynamicVideo.getVideoDurationTextScale().getWidth()) / 2,
                    dynamicVideo.getVideoDurationTextScale().getHeight() + dynamicVideo.getBlackDurationRectPos().getY() + (dynamicVideo.getBlackDurationRectScale().getHeight() - dynamicVideo.getVideoDurationTextScale().getHeight()) / 2 - 13,videoInfo.getDuration(),FontManager.videoTitleTextFont,Color.white);
            blackDurationTextElement.render(g2d);
        }

        /// 动态主体
        @Getter
        @Setter
        static class Dynamic{
            /// 发布者信息
            private BilibiliContentProperty.YoutuberInfo authorInfo;
            /// 动态信息
            private BilibiliContentProperty.DynamicInfo dynamicInfo;
            /// 动态视频信息
            private BilibiliContentProperty.VideoInfo videoInfo;

            /// 当前的正文处理模块
            private DynamicContent currentDynamicContent;
            /// 当前的图像处理模块
            private DynamicImages currentDynamicImages;
            /// 当前的视频处理模块
            private DynamicVideo currentDynamicVideo;

            /// 当前的G2d
            private Graphics2D g2d;

            public Dynamic(){
                this.authorInfo = null;
                this.dynamicInfo = null;
                this.videoInfo = null;
                this.currentDynamicContent = null;
                this.currentDynamicImages = null;
                this.currentDynamicVideo = null;
                this.g2d = null;
            }

            /// 是否为置顶动态
            public static boolean isStickyDynamic(JsonObject dynamicJson) {
                var moduleTag = JsonUtils.of(dynamicJson)
                        .getAsExt("modules")
                        .getAsString("module_tag");
                if(moduleTag.isEmpty()) return false;
                return moduleTag.contains("置顶");
            }

            /// 是否支持图片播报
            public static boolean isSupportedDynamicType(String dynamicType){
                return dynamicType.equals("DYNAMIC_TYPE_DRAW")
                        || dynamicType.equals("DYNAMIC_TYPE_FORWARD")
                        ||  dynamicType.equals("DYNAMIC_TYPE_AV");
            }

            /// 是否为推送过的动态（读取缓存）
            public static boolean isPushedDynamic(BilibiliContentProperty.DynamicInfo dynamicInfo){
                var cachedDynamicInfos = BilibiliCacheStorage.getInstance().getDynamic_infos();
                for (int i = 0; i < cachedDynamicInfos.size(); i++) {
                    var cachedDynamicInfo = cachedDynamicInfos.get(i);
                    if(cachedDynamicInfo.getId().equals(dynamicInfo.getId())){
                        return true;
                    }
                }
                return false;
            }

            /// 动态正文处理模块
            @Getter
            static class DynamicContent{
                /// 当前动态
                private final Dynamic currentDynamic;
                /// 当前G2d
                private final Graphics2D g2d;
                public DynamicContent(JsonObject module_dynamic,Dynamic currentDynamic) throws NullPointerException {
                    this.currentDynamic = currentDynamic;
                    this.g2d = currentDynamic.getG2d();
                    if(module_dynamic == null) return;

                    /// 图文动态 可以有标题
                    if(currentDynamic.getDynamicInfo().getType().equals("DYNAMIC_TYPE_DRAW")){
                        var detailResponse = BilibiliAPIRequester.requestDynamicDetails(currentDynamic.getDynamicInfo().getId());
                        var detailJsonObject = gson.fromJson(detailResponse, JsonObject.class);
                        this.detailJson = detailJsonObject;
                        var detailModules = JsonUtils.of(detailJsonObject)
                                .getAsExt("data")
                                .getAsExt("item")
                                .getAsElementList("modules");
                        var titleJson = JsonUtils.of(detailModules).getObjectAt(0);
                        var titleText = titleJson != null
                                ? JsonUtils.of(titleJson)
                                .getAsExt("module_title")
                                .getAsString("text") : "";

                        var paragraphs = JsonUtils.of(detailModules)
                                .getAsExt(detailModules.size() - 2)
                                .getAsExt("module_content")
                                .getAsElementList("paragraphs");

                        var newDynamicTextList = JsonUtils.of(paragraphs)
                                .getAsExt(0)
                                .getAsExt("text")
                                .getAsElementList("nodes");

                        //添加文本/表情/@标记
                        for (int i = 0; i < newDynamicTextList.size(); i++) {
                            var e = newDynamicTextList.get(i).getAsJsonObject();
                            var eExt = JsonUtils.of(e);
                            switch (eExt.getAsString("type")) {
                                case "TEXT_NODE_TYPE_WORD":
                                    var t = eExt.getAsExt("word")
                                            .getAsString("words")
                                            .replaceAll("\\{BigE}","")
                                            .replaceAll("\\{E}","");
                                    mainTextStringBuilder.append(t);
                                    break;
                                case "TEXT_NODE_TYPE_RICH":
                                    handleRichText(e,true);
                                    break;
                            }
                        }
                        this.titleText = titleText;
                    }
                    /// 非图文动态 一般没有标题
                    else{
                        var dynamic_desc = JsonUtils.of(module_dynamic).getAsObject("desc");
                        if(dynamic_desc != null) {
                            var richTextNodes = JsonUtils.of(dynamic_desc).getAsElementList("rich_text_nodes");
                            if(!richTextNodes.isEmpty()) {
                                for (int i = 0; i < richTextNodes.size(); i++) {
                                    var e = JsonUtils.of(richTextNodes).getObjectAt(i);
                                    handleRichText(e,false);
                                }
                            }
                        }
                    }
                }

                /// 正文宽度限制
                private final int textWidthLimit = 844;

                /// 正文（包含表情替代符）
                private final StringBuilder mainTextStringBuilder = new StringBuilder();

                /// 富文本列表
                private final List<String> specialTexts = new ArrayList<>();

                /// 表情列表
                private final List<BilibiliContentProperty.EmojiInfo> dynamicEmojis = new ArrayList<>();

                /// 动态标题
                private String titleText = "";

                private JsonObject detailJson;

                /// 分行过的正文
                public String getLineFedMainText(){
                    return Tool.autoLineFeedText(mainTextStringBuilder.toString(),FontManager.textFont,textWidthLimit,g2d);
                }

                /// 文章部分的高度
                public int getContentAreaHeight() {
                    var hasTitle = !titleText.isEmpty();
                    return getTextHeight(getLineFedMainText(), FontManager.textFont,hasTitle,g2d);
                }

                /// 处理富文本
                private void handleRichText(JsonObject e,boolean drawDynamic){
                    var richExt = drawDynamic ? JsonUtils.of(e).getAsExt("rich") : JsonUtils.of(e);
                    var richType = drawDynamic ? richExt.getAsString("type") : JsonUtils.of(e).getAsString("type");
                    switch (richType) {
                        case "RICH_TEXT_NODE_TYPE_TEXT":
                            if(!drawDynamic){
                                var rt = richExt.getAsString("orig_text").replaceAll("\\{BigE}","").replaceAll("\\{E}","");
                                mainTextStringBuilder.append(rt);
                            }
                            break;
                        case "RICH_TEXT_NODE_TYPE_EMOJI":
                            var emojiJson= richExt.getAsObject("emoji");
                            if(emojiJson == null){
                                System.out.println("EmojiJson is null!");
                                return;
                            }
                            var emojiInfo = new BilibiliContentProperty.EmojiInfo(emojiJson);
                            var emojiUrl = emojiInfo.getUrl();
                            var emojiSizeType = emojiInfo.getEmojiSizeType();
                            mainTextStringBuilder.append(emojiSizeType == BilibiliContentProperty.EmojiInfo.EmojiSize.BIG ? "{BigE}" : "{E}");
                            dynamicEmojis.add(new BilibiliContentProperty.EmojiInfo(emojiSizeType,emojiUrl));
                            break;
                        default:
                            var defaultText = richExt.getAsString("orig_text");
                            specialTexts.add(defaultText);
                            mainTextStringBuilder.append(defaultText);
                            break;
                    }
                }

                /// 模块工具
                static class Tool{
                    /// 根据宽度对文字进行分行
                    public static String autoLineFeedText(String text,Font font,float bgWidth,Graphics2D g2d) {
                        StringBuilder single = new StringBuilder();
                        StringBuilder result = new StringBuilder();
                        for (int i = 0; i < text.length(); i++) {
                            var c = text.charAt(i);
                            single.append(c);
                            var lineBreakStr = "\n";

                            var isLineBreakStr = StringUtils.isInRange(text,lineBreakStr,i);
                            var lineFeedEmojiChar = false;
                            var ls = single.toString().replaceAll("\\\\n",lineBreakStr).split(lineBreakStr,-1);

                            if(single.toString().endsWith("{") && i < text.length() - 1){
                                var c_p1 = text.charAt(i + 1);
                                var emojiStr = "";
                                if(c_p1 == 'B'){
                                    emojiStr = "\\{BigE}";
                                }
                                else if(c_p1 == 'E'){
                                    emojiStr = "\\{E}";
                                }

                                if(!emojiStr.isEmpty()){
                                    var fSingle = single + "T".repeat(emojiStr.length() - 1);
                                    var fls = fSingle.replaceAll("\\\\n",lineBreakStr).split(lineBreakStr,-1);
                                    if(getTextWidth(fls[fls.length - 1],font,g2d) - bgWidth > 0){
                                        lineFeedEmojiChar = true;
                                    }
                                }
                            }
                            if((getTextWidth(ls[ls.length - 1],font,g2d) > bgWidth && !isLineBreakStr) || lineFeedEmojiChar){
                                single.deleteCharAt(single.length() - 1);
                                i--;
                                result.append(single);
                                result.append("\n");
                                single.setLength(0);
                            }
                        }
                        if(!single.isEmpty()){
                            result.append(single);
                        }
                        return result.toString();
                    }

                    /**
                     * 绘制文字到临时BufferedImage，并返回其宽度
                     */
                    public static int getTextWidth(String text, Font font,Graphics2D g2d) {
                        FontMetrics metrics = g2d.getFontMetrics(font);
                        int textHeight = metrics.getHeight();
                        BufferedImage tempImage = new BufferedImage(1000, textHeight, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D tempG2d = tempImage.createGraphics();
                        tempG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        tempG2d.setFont(font);
                        tempG2d.drawString(text, 0, metrics.getAscent());
                        tempG2d.dispose();
                        return metrics.stringWidth(text);
                    }

                    /**
                     * 绘制文字到临时BufferedImage，并返回其高度
                     */
                    public static int getTextHeight(String text, Font font,boolean hasTitle,Graphics2D g2d) {
                        FontMetrics metrics = g2d.getFontMetrics(font);
                        var emojiHeightA = 0;
                        var additionalHeight = 0;
                        var ls = text.replaceAll("\\\\n","\n").split("\n",-1);
                        for (int i = 0; i < ls.length; i++) {
                            var l = ls[i];

                            if(i > 0) {
                                var ll = ls[i - 1];
                                if(ll.contains("{BigE}") && !l.contains("{BigE}")) {
                                    additionalHeight += 10;
                                }
                            }

                            if(l.contains("{BigE}") && (i != 0 || hasTitle)){
                                emojiHeightA ++ ;
                            }
                        }
                        return (metrics.getHeight() + 10) * (ls.length + emojiHeightA) + additionalHeight;
                    }
                }
            }

            /// 动态图片处理模块
            @Getter
            static class DynamicImages{
                /// 当前动态
                private final Dynamic currentDynamic;
                public DynamicImages(JsonObject dynamicJson,Dynamic currentDynamic){
                    this.currentDynamic = currentDynamic;
                    if(dynamicJson == null) return;
                    var modules = JsonUtils.of(dynamicJson)
                            .getAsExt("data")
                            .getAsExt("item")
                            .getAsElementList("modules");

                    //默认索引为1 获取图片
                    var picGroupTemp = 1;

                    var paragraphList = JsonUtils.of(modules)
                            .getAsExt(modules.size() - 2)
                            .getAsExt("module_content")
                            .getAsElementList("paragraphs");

                    if(paragraphList == null) {
                        System.out.println("Paragraphs list is null!");
                        return;
                    }

                    JsonElement picsElement;
                    if(paragraphList.size() < picGroupTemp + 1) {
                        picsElement = JsonUtils.of(modules)
                                .getAsExt("module_top")
                                .getAsExt("display")
                                .getAsExt("album")
                                .getElement("pics");
                    }
                    else{
                        picsElement = JsonUtils.of(paragraphList)
                                .getAsExt(picGroupTemp)
                                .getAsExt("pic")
                                .getElement("pics");
                    }

                    if(picsElement == null) {
                        return;
                    }

                    var imageObjList = picsElement.getAsJsonArray();

                    var result = new ArrayList<String>();

                    for (int i = 0; i < imageObjList.size(); i++) {
                        var imageExt =  JsonUtils.of(imageObjList).getAsExt(i);
                        var url = imageExt.getAsString("url");
                        if(url.isEmpty()) continue;
                        result.add(url);
                    }

                    this.imageUrlList = result;
                    this.imageSize = currentDynamic.getCurrentDynamicContent().getTextWidthLimit() / (result.size() > 1 ? 3 : 1);
                }

                /// 图片列表
                private List<String> imageUrlList = new ArrayList<>();

                /// 图片尺寸
                private int imageSize = 0;

                /// 获取图像排版后区域高度
                public int getImageAreaHeight(){
                    var imageCount = imageUrlList == null ? 0 : imageUrlList.size();

                    //没有图片 直接让高度为0
                    if(imageCount < 1) return 0;

                    //根据图片的数量计算排版后图片区域的高度
                    return ((imageCount - 1) / 3 + 1) * imageSize + 10;
                }
            }

            /// 动态视频处理模块
            @Getter
            @Setter
            static class DynamicVideo{
                /// 是否有视频
                private boolean hasVideo;
                /// 当前动态
                private Dynamic currentDynamic;

                /// 分行后的视频标题
                private String lineFedVideoTitleText = "";
                /// 分行后的视频简介
                private String lineFedVideoDescText = "";

                /// 标题文字区域高度
                private int videoTitleTextHeight = 0;

                /// 简介文字区域高度
                private int videoDescTextHeight = 0;

                /// 封面位置
                private Position coverPos = Position.ZERO();
                /// 时长黑框位置
                private Position blackDurationRectPos = Position.ZERO();
                /// 标题位置
                private Position titleTextPos = Position.ZERO();
                /// 简介位置
                private Position descTextPos = Position.ZERO();

                /// 卡片白框大小
                private Scale whiteRoundedRectScale = Scale.ZERO();
                /// 封面尺寸
                private Scale coverScale =  Scale.ZERO();
                /// 时长黑框尺寸
                private Scale blackDurationRectScale = Scale.ZERO();
                /// 时长文字宽度和高度
                private Scale videoDurationTextScale = Scale.ZERO();

                public DynamicVideo(boolean hasVideo, Dynamic currentDynamic) {
                    this.hasVideo = hasVideo;
                    this.currentDynamic = currentDynamic;
                    if(hasVideo){
                        var videoInfo = currentDynamic.getVideoInfo();
                        var g2d = currentDynamic.getG2d();

                        var contentAreaHeight = currentDynamic.getCurrentDynamicContent().getContentAreaHeight();

                        /// 将视频标题和简介自动分行
                        var videoTitleWidthLimit = 664;
                        lineFedVideoTitleText = Dynamic.DynamicContent.Tool.autoLineFeedText(videoInfo.getTitle(),FontManager.videoTitleTextFont, videoTitleWidthLimit,g2d);
                        lineFedVideoDescText = Dynamic.DynamicContent.Tool.autoLineFeedText(videoInfo.getDescription(),FontManager.descFont, videoTitleWidthLimit,g2d);

                        /// 计算标题高度
                        videoTitleTextHeight = getTextHeight(lineFedVideoTitleText,FontManager.videoTitleTextFont,false,g2d);

                        /// 计算简介高度
                        videoDescTextHeight = getTextHeight(lineFedVideoDescText,FontManager.descFont,false,g2d);

                        /// 计算时长文字长宽度
                        var videoDurationTextWidth = Dynamic.DynamicContent.Tool.getTextWidth(videoInfo.getDuration(),FontManager.videoTitleTextFont,g2d);
                        var videoDurationTextHeight = getTextHeight(videoInfo.getDuration(),FontManager.videoTitleTextFont,false,g2d);
                        videoDurationTextScale = new Scale(videoDurationTextWidth,videoDurationTextHeight);

                        /// 计算封面位置
                        var coverPosX = 150;
                        var coverPosY = contentAreaHeight + 390;
                        coverPos = new Position(coverPosX,coverPosY);

                        /// 计算封面尺寸
                        var coverWidth = 724;
                        int coverHeight = (int) (coverWidth / 1.7878f);
                        coverScale = new Scale(coverWidth,coverHeight);

                        /// 计算标题位置
                        var videoTitlePosY = coverPosY + 60 + coverHeight;
                        titleTextPos = new Position(190,videoTitlePosY);

                        /// 计算简介位置
                        var videoDescPosY = videoTitlePosY + videoTitleTextHeight;
                        descTextPos = new Position(190,videoDescPosY);

                        /// 计算卡片白框尺寸
                        var whiteRoundedRect_AV_Height = coverHeight + videoTitleTextHeight + videoDescTextHeight + 100;
                        var whiteRoundedRect_AV_Width = 804;
                        whiteRoundedRectScale = new Scale(whiteRoundedRect_AV_Width,whiteRoundedRect_AV_Height);

                        /// 计算时长黑框尺寸
                        var blackDurationRectWidth = videoDurationTextWidth + 35;
                        var blackDurationRectHeight = videoDurationTextHeight - 10;
                        blackDurationRectScale = new Scale(blackDurationRectWidth,blackDurationRectHeight);

                        /// 计算时长黑框位置
                        var blackDurationRectPosX = coverPosX + 30;
                        var blackDurationRectPosY = coverPosY + coverHeight - 65;
                        blackDurationRectPos = new Position(blackDurationRectPosX,blackDurationRectPosY);
                    }

                }
                /// 视频区域所占高度
                public int getVideoAreaHeight(){
                    if(!hasVideo || currentDynamic == null) return 0;
                    var additionalValue = -20;
                    return whiteRoundedRectScale.getHeight() + additionalValue;
                }
            }
        }

        /// 字体存储器
        @Getter
        static class FontManager{

            /// 视频标题字体
            public static final Font videoTitleTextFont = new Font(BilibiliDynamicsPusherConfig.getInstance().getFont(), Font.BOLD, 35);

            /// 视频简介字体
            public static final Font descFont = new Font(BilibiliDynamicsPusherConfig.getInstance().getFont(), Font.PLAIN, 28);

            /// 用户昵称字体
            public static final Font usernameFont = new Font(BilibiliDynamicsPusherConfig.getInstance().getFont(), Font.BOLD, 50);

            /// 动态时间字体
            public static final Font dynamicTimeFont = new Font(BilibiliDynamicsPusherConfig.getInstance().getFont(), Font.PLAIN, 30);

            /// 正文标题字体
            public static final Font dynamicTitleFont = new Font(BilibiliDynamicsPusherConfig.getInstance().getFont(), Font.BOLD, 42);

            /// 动态正文字体
            public static final Font textFont = new Font(BilibiliDynamicsPusherConfig.getInstance().getFont(), Font.PLAIN, 38);
        }
    }
}