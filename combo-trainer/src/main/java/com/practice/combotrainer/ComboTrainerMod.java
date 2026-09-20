package com.practice.combotrainer;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class ComboTrainerMod implements ModInitializer {

    public static final String DUMMY_TAG = "combotrainer_dummy";
    public static final String MOD_ID = "combotrainer";

    @Override
    public void onInitialize() {
        registerCommand();
        registerDummyUpkeep();
    }

    private void registerCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("trainingdummy")
                        .then(CommandManager.literal("summon")
                                .executes(ctx -> summonDummy(ctx.getSource(), "zombie"))
                                .then(CommandManager.argument("mob", StringArgumentType.word())
                                        .executes(ctx -> summonDummy(ctx.getSource(), StringArgumentType.getString(ctx, "mob")))))
                        .then(CommandManager.literal("clear")
                                .executes(this::clearDummies))));
    }

    private int summonDummy(ServerCommandSource source, String mobId) {
        ServerWorld world = source.getWorld();
        Entity player = source.getEntity();
        double x = source.getPosition().x;
        double y = source.getPosition().y;
        double z = source.getPosition().z;

        double lookX = 0, lookZ = -2;
        if (player != null) {
            lookX = -Math.sin(Math.toRadians(player.getYaw())) * 3;
            lookZ = Math.cos(Math.toRadians(player.getYaw())) * 3;
        }
        String nbt = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:20.0f,"
                + "CustomName:'{\"text\":\"Training Dummy\"}',CustomNameVisible:1b,"
                + "Tags:[\"" + DUMMY_TAG + "\"]}";
        String cmd = String.format(java.util.Locale.ROOT,
                "summon minecraft:%s %.2f %.2f %.2f %s",
                mobId, x + lookX, y, z + lookZ, nbt);

        source.getServer().getCommandManager().parseAndExecute(source, cmd);
        source.sendFeedback(() -> Text.literal("[Combo Trainer] Training dummy summoned. "
                + "It will auto-heal and cannot be killed - swing away."), false);
        return 1;
    }

    private int clearDummies(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        int[] count = {0};
        world.getEntitiesByType(net.minecraft.entity.EntityType.ZOMBIE, e -> e.getCommandTags().contains(DUMMY_TAG))
                .forEach(e -> { e.discard(); count[0]++; });
        for (Entity e : world.iterateEntities()) {
            if (e instanceof LivingEntity && e.getCommandTags().contains(DUMMY_TAG) && e.isAlive()) {
                e.discard();
                count[0]++;
            }
        }
        int found = count[0];
        source.sendFeedback(() -> Text.literal("[Combo Trainer] Removed " + found + " training dummy(ies)."), false);
        return found;
    }

    private void registerDummyUpkeep() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (Entity e : world.iterateEntities()) {
                if (e instanceof LivingEntity living && living.getCommandTags().contains(DUMMY_TAG)) {
                    if (living.getHealth() < living.getMaxHealth()) {
                        living.setHealth(living.getMaxHealth());
                    }
                    if (living.isOnFire()) {
                        living.extinguish();
                    }
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !entity.getCommandTags().contains(DUMMY_TAG));
    }
}
