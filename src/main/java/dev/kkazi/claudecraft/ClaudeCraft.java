package dev.kkazi.claudecraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ClaudeCraft implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("claude").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("How can I help you today, Master?"), false);
                return 1;
            }));
        });
    }
}
