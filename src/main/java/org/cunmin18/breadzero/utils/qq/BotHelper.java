package org.cunmin18.breadzero.utils.qq;

import com.mikuac.shiro.core.Bot;
import lombok.Getter;
import lombok.Setter;
import org.cunmin18.breadzero.yamls.config.BotSettingsConfig;

public class BotHelper {
    @Setter
    @Getter
    private static Bot bot;

    public static boolean isOwner(long userID) {
        var breadZeroConfig = BotSettingsConfig.getInstance();
        return breadZeroConfig != null && breadZeroConfig.getOwners().contains(userID);
    }
}
