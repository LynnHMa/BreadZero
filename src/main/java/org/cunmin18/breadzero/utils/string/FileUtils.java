package org.cunmin18.breadzero.utils.string;

import org.cunmin18.breadzero.yamls.config.BilibiliDynamicsPusherConfig;

import java.io.File;
import java.io.IOException;

public class FileUtils {
    /**
     * 获取 JAR 外部同级目录下的文件路径（兼容开发和 JAR 环境）
     */
    public static String getDirFilePath(String fileName, String folder) {
        // 绝对路径直接返回
        if (folder.contains(":/") || folder.startsWith("/")) {
            return new File(folder, fileName).getAbsolutePath();
        }

        try {
            // 获取当前程序的运行目录（JAR 外部的工作目录）
            // 优先用用户工作目录（java -jar 执行时的目录）
            String workingDir = System.getProperty("user.dir");
            File targetDir = new File(workingDir, folder);

            // 确保目录存在
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw new IOException("无法创建目录: " + targetDir.getAbsolutePath());
            }

            // 返回文件绝对路径
            File targetFile = new File(targetDir, fileName);
            System.out.println("目标文件路径: " + targetFile.getAbsolutePath()); // 调试用
            return targetFile.getAbsolutePath();

        } catch (IOException e) {
            throw new RuntimeException("获取文件路径失败: " + fileName, e);
        }
    }

    /**
     * 获取 JAR 外部同级目录下的文件路径（兼容开发和 JAR 环境）
     */
    public static String getOutputDirFilePath(String fileName) {
        if (BilibiliDynamicsPusherConfig.getInstance() == null) {
            throw new RuntimeException("BilibiliDynamicsPusherConfig 尚未初始化");
        }
        var outputPath = BilibiliDynamicsPusherConfig.getInstance().getImage_output_path();
        return getDirFilePath(fileName, outputPath);
    }
}