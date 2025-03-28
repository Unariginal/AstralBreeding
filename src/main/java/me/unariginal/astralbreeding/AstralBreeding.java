package me.unariginal.astralbreeding;

import me.unariginal.astralbreeding.commands.AstralCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AstralBreeding implements ModInitializer {
    private final String MOD_ID = "astralbreeding";
    private final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static AstralBreeding INSTANCE;
    public static boolean DEBUG = true;

    private MinecraftServer server;

    @Override
    public void onInitialize() {
        INSTANCE = this;

        new AstralCommands();

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            this.server = server;
        });
    }

    public MinecraftServer server() {
        return server;
    }

    public void logInfo(String message) {
        if (DEBUG) {
            LOGGER.info(message);
        }
    }
}
