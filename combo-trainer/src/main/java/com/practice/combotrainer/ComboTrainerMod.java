package com.practice.combotrainer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class ComboTrainerClient implements ClientModInitializer {

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("combotrainer", "general"));

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

    private static final int SWING_RESOLVE_TICKS = 6;

    private static final class PendingSwing {
        final LivingEntity target;
        final float hurtTimeAtSwing;
        final boolean predictedCrit;
        final boolean wTapped;
        int ticksLeft = SWING_RESOLVE_TICKS;

        PendingSwing(LivingEntity target, float hurtTimeAtSwing, boolean predictedCrit, boolean wTapped) {
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
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
    }
