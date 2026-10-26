package org.cunmin18.breadzero.utils.string;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigUtils {
    /// 加载配置文件
    public static <T> T loadConfig(String configPath, Class<T> configClass) {
        Path filePath = Paths.get(configPath);
        createParentDirectories(filePath);
        try {
            if (!Files.exists(filePath)) {
                T defaultInstance = configClass.getDeclaredConstructor().newInstance();
                saveConfig(configPath, defaultInstance);
                System.out.println("配置文件不存在，已创建默认配置：" + filePath.toAbsolutePath());
            }

            try (var is = new InputStreamReader(Files.newInputStream(filePath), StandardCharsets.UTF_8)) {
                Yaml yaml = new Yaml();
                T instance = yaml.loadAs(is, configClass);
                if (instance == null) {
                    throw new IOException("配置文件内容为空：" + configPath);
                }
                return instance;
            }
        }
        catch (Exception e) {
            throw new RuntimeException("加载配置文件失败：" + configPath, e);
        }
    }

    /// 保存配置文件
    public static <T> void saveConfig(String configPath, T instance) {
        createParentDirectories(Paths.get(configPath));

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setExplicitStart(false);
        options.setExplicitEnd(false);

        Representer representer = new Representer(options);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        representer.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        representer.addClassTag(instance.getClass(), Tag.MAP);

        Yaml yaml = new Yaml(representer,options);
        try {
            FileWriter fileWriter = new FileWriter(configPath, StandardCharsets.UTF_8);
            yaml.dump(instance, fileWriter);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /// 创建配置文件目录
    private static void createParentDirectories(Path filePath) {
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new RuntimeException("创建配置文件目录失败：" + parentDir, e);
            }
        }
    }
}
