package com.practice.combotrainer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Client-only: reads input/HUD state and plays local sound cues. It never
 * sends attack packets, never moves the player, and never targets entities
 * on its own - it only reports timing feedback on swings the human made.
 */
public class ComboTrainerClient implements ClientModInitializer {

    private static KeyBinding toggleHudKey;
    private boolean hudVisible = true;

    private boolean prevAttackPressed = false;
    private final Deque<Long> recentSwingTimestamps = new ArrayDeque<>();

    private boolean wasSprintingLastTick = false;
    private int ticksSinceSprintEnded = 999;

    private final List<PendingSwing> pendingSwings = new ArrayList<>();

    private int comboStreak = 0;
    private int totalHits = 0;
    private int totalMisses = 0;
    private int critHits = 0;
    private int wTapHits = 0;
    private String lastHitLabel = "-";
    private int lastHitLabelColor = 0xFFFFFF;

    private static final int SWING_RESOLVE_TICKS = 6; // ~300ms window to confirm a hit landed

    private static final class PendingSwing {
        final Entity target;
        final float hurtTimeAtSwing;
        final boolean predictedCrit;
        final boolean wTapped;
        int ticksLeft = SWING_RESOLVE_TICKS;

        PendingSwing(Entity target, float hurtTimeAtSwing, boolean predictedCrit, boolean wTapped) {
            this.target = target;
            this.hurtTimeAtSwing = hurtTimeAtSwing;
            this.predictedCrit = predictedCrit;
            this.wTapped = wTapped;
        }
    }

    @Override
    public void onInitializeClient() {
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.combotrainer.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_APOSTROPHE,
                "category.combotrainer"));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        while (toggleHudKey.wasPressed()) {
            hudVisible = !hudVisible;
        }

        // Track sprint-reset ("W-tap") window.
        boolean sprintingNow = client.player.isSprinting();
        if (!sprintingNow && wasSprintingLastTick) {
            ticksSinceSprintEnded = 0;
        } else {
            ticksSinceSprintEnded = Math.min(ticksSinceSprintEnded + 1, 999);
        }
        wasSprintingLastTick = sprintingNow;

        // Edge-detect the attack key ourselves (do NOT call wasPressed() on
        // attackKey - that queue belongs to vanilla's own attack handling).
        boolean attackPressedNow = client.options.attackKey.isPressed();
        if (attackPressedNow && !prevAttackPressed) {
            onSwing(client);
        }
        prevAttackPressed = attackPressedNow;

        // Trim CPS window to the last 1000ms.
        long now = System.currentTimeMillis();
        while (!recentSwingTimestamps.isEmpty() && now - recentSwingTimestamps.peekFirst() > 1000) {
            recentSwingTimestamps.pollFirst();
        }

        resolvePendingSwings();
    }

    private void onSwing(MinecraftClient client) {
        recentSwingTimestamps.addLast(System.currentTimeMillis());

        HitResult target = client.crosshairTarget;
        if (!(target instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity living)) {
            registerMiss("MISS (no target)", 0xFF5555);
            return;
        }

        boolean predictedCrit = computeCritConditions(client);
        boolean wTapped = !client.player.isSprinting() && ticksSinceSprintEnded <= 3;

        pendingSwings.add(new PendingSwing(living, living.hurtTime, predictedCrit, wTapped));
    }

    private boolean computeCritConditions(MinecraftClient client) {
        var player = client.player;
        return player.fallDistance > 0.0F
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasVehicle()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS);
    }

    private void resolvePendingSwings() {
        Iterator<PendingSwing> it = pendingSwings.iterator();
        while (it.hasNext()) {
            PendingSwing swing = it.next();
            boolean landed = !swing.target.isRemoved() && swing.target.hurtTime > swing.hurtTimeAtSwing;
            if (landed) {
                totalHits++;
                comboStreak++;
                StringBuilder label = new StringBuilder("HIT");
                if (swing.predictedCrit) {
                    critHits++;
                    label.append(" + CRIT");
                }
                if (swing.wTapped) {
                    wTapHits++;
                    label.append(" + W-TAP");
                }
                setLastHitLabel(label.toString(), swing.predictedCrit ? 0xFFD700 : 0x55FF55);
                playCue(swing.predictedCrit ? SoundEvents.ENTITY_PLAYER_ATTACK_CRIT : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, swing.predictedCrit ? 1.4f : 1.0f);
                it.remove();
            } else if (--swing.ticksLeft <= 0) {
                registerMiss("MISS", 0xFF5555);
                it.remove();
            }
        }
    }

    private void registerMiss(String label, int color) {
        totalMisses++;
        comboStreak = 0;
        setLastHitLabel(label, color);
        playCue(SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 0.7f);
    }

    private void setLastHitLabel(String label, int color) {
        lastHitLabel = label;
        lastHitLabelColor = color;
    }

    private void playCue(net.minecraft.sound.SoundEvent sound, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, 0.5f));
        }
    }

    private void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!hudVisible) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        RenderSystem.enableBlend();
        int x = 6;
        int y = 6;
        int lineHeight = 10;

        context.drawTextWithShadow(client.textRenderer, Text.literal("Combo Trainer"), x, y, 0x55CCFF);
        y += lineHeight;
        context.drawTextWithShadow(client.textRenderer, Text.literal("CPS: " + recentSwingTimestamps.size()), x, y, 0xFFFFFF);
        y += lineHeight;
        context.drawTextWithShadow(client.textRenderer, Text.literal("Combo streak: " + comboStreak), x, y, 0xFFFFFF);
        y += lineHeight;
        int totalSwings = totalHits + totalMisses;
        String acc = totalSwings == 0 ? "-" : (100 * totalHits / totalSwings) + "%";
        context.drawTextWithShadow(client.textRenderer, Text.literal("Accuracy: " + acc + " (" + totalHits + "/" + totalSwings + ")"), x, y, 0xFFFFFF);
        y += lineHeight;
        context.drawTextWithShadow(client.textRenderer, Text.literal("Crits: " + critHits + "  W-Taps: " + wTapHits), x, y, 0xFFFFFF);
        y += lineHeight;
        context.drawTextWithShadow(client.textRenderer, Text.literal("Last: " + lastHitLabel), x, y, lastHitLabelColor);
    }
}
