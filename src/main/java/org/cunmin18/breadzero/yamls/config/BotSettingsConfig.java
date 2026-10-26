package org.cunmin18.breadzero.yamls.config;

import jakarta.annotation.PostConstruct;
import lombok.*;
import org.cunmin18.breadzero.utils.string.ConfigUtils;
import org.cunmin18.breadzero.utils.string.FileUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Component
public class BotSettingsConfig {
    public static String configDir;

    @Getter
    private static BotSettingsConfig instance;

    private List<Long> owners = List.of(2040266560L,3532910941L);

    @PostConstruct
    public void init() {
        configDir = FileUtils.getDirFilePath("bot-settings.yml","config");
        load();
    }
    public static void load() {
        instance = ConfigUtils.loadConfig(configDir, BotSettingsConfig.class);
    }
    public static void save() {
        ConfigUtils.saveConfig(configDir,instance);
    }
}
