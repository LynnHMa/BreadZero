package org.cunmin18.breadzero.yamls.config;

import jakarta.annotation.PostConstruct;
import lombok.*;
import org.cunmin18.breadzero.utils.string.ConfigUtils;
import org.cunmin18.breadzero.utils.string.FileUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class BilibiliDynamicsPusherConfig{
    public static String configDir;

    @Getter
    private static BilibiliDynamicsPusherConfig instance;

    private boolean enabled;
    private long polling_duration_per_youtuber = 600;
    private long polling_duration_per_group = 1800;
    private String background_url = "";
    private String font = "黑体";
    private String image_output_path = "output/";
    private List<GroupInfo> group_info_list =  new ArrayList<>();

    @Setter
    @Getter
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GroupInfo {
        private List<String> subscribed_youtubers = new ArrayList<>();
        private String group_id = "";
    }

    @PostConstruct
    public void init() {
        configDir = FileUtils.getDirFilePath("bilibili-dynamic-pusher.yml","config");
        load();
    }

    public static void load() {
        instance = ConfigUtils.loadConfig(configDir,BilibiliDynamicsPusherConfig.class);
    }
    public static void save() {
        ConfigUtils.saveConfig(configDir,instance);
    }
}
