package org.cunmin18.breadzero;

import org.cunmin18.breadzero.yamls.cathe.BilibiliCacheStorage;
import org.cunmin18.breadzero.yamls.config.BilibiliDynamicsPusherConfig;
import org.cunmin18.breadzero.yamls.config.BotSettingsConfig;
import org.cunmin18.breadzero.yamls.config.PowerBillCheckerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BreadZeroApplication {

	public static void main(String[] args) {
		SpringApplication.run(BreadZeroApplication.class, args);
        try {
            Thread.currentThread().join();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
	}
    public static void saveAllConfig(){
        BilibiliDynamicsPusherConfig.save();
        BotSettingsConfig.save();
        PowerBillCheckerConfig.save();

        BilibiliCacheStorage.save();
    }
    public static void loadAllConfig(){
        /// 配置文件
        BilibiliDynamicsPusherConfig.load();
        BotSettingsConfig.load();
        PowerBillCheckerConfig.load();
        /// 缓存文件
        BilibiliCacheStorage.load();
    }
}
