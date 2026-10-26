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
public class PowerBillCheckerConfig {
    public static String configDir;

    @Getter
    private static PowerBillCheckerConfig instance;

    private boolean enabled = false;
    private long lowest_alert_power_bill = 5;
    private int min_check_min_duration;
    private int max_check_min_duration;
    private String username;
    private String password;
    private List<GroupPowerBill> group_power_bill =  new ArrayList<>();

    @Setter
    @Getter
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GroupPowerBill {
        private List<String> checking_rooms = new ArrayList<>();
        private String group_id = "";
    }

    @PostConstruct
    public void init() {
        configDir = FileUtils.getDirFilePath("power-bill-checker.yml","config");
        load();
    }

    public static void load() {
        instance = ConfigUtils.loadConfig(configDir, PowerBillCheckerConfig.class);
    }
    public static void save() {
        ConfigUtils.saveConfig(configDir,instance);
    }
}
