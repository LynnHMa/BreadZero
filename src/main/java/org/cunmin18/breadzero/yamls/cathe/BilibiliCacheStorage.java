package org.cunmin18.breadzero.yamls.cathe;

import jakarta.annotation.PostConstruct;
import lombok.*;
import org.cunmin18.breadzero.utils.bilibili.dynamic.content.BilibiliContentProperty;
import org.cunmin18.breadzero.utils.string.ConfigUtils;
import org.cunmin18.breadzero.utils.string.FileUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Component
public class BilibiliCacheStorage {
    protected static String configDir;

    @Getter
    private static BilibiliCacheStorage instance;

    private List<BilibiliContentProperty.YoutuberInfo> youtuber_infos = new ArrayList<>();

    private List<BilibiliContentProperty.DynamicInfo> dynamic_infos = new ArrayList<>();

    private String cookie;
    private String refresh_token;

    @PostConstruct
    public void init() {
        configDir = FileUtils.getDirFilePath("bilibili_cache.yml","cache");
        load();
    }

    public static void load() {
        instance = ConfigUtils.loadConfig(configDir, BilibiliCacheStorage.class);
    }
    public static void save() {
        ConfigUtils.saveConfig(configDir,instance);
    }
    public static void clean(CacheType type){
        switch (type){
            case ALL ->{
                instance.youtuber_infos.clear();
                instance.dynamic_infos.clear();
            }
            case YOUTUBER_INFO -> instance.youtuber_infos.clear();
            case DYNAMIC_INFO -> instance.dynamic_infos.clear();
        }
        save();
    }

}
