package com.example.vulkanwater.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.example.vulkanwater.render.VulkanGlobals; // Your Vulkan UBO / global state holder

public class WaterSettingsScreen extends Screen {
    private final Screen parent;

    public WaterSettingsScreen(Screen parent) {
        super(Component.literal("Live Water Shader Settings"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        // Keeps the game loop and Vulkan frame rendering active
        return false;
    }

    @Override
    protected void init() {
        int panelWidth = 180;
        int x = 12; // Docked to left edge so center screen water remains visible
        int y = 24;

        // 1. Live Wave Height Slider -> Updates Vulkan Global UBO immediately
        this.addRenderableWidget(new AbstractSliderButton(x, y, panelWidth, 20,
                Component.literal("Wave Height: " + String.format("%.2f", VulkanGlobals.waveHeight)),
                VulkanGlobals.waveHeight / 2.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal("Wave Height: " + String.format("%.2f", VulkanGlobals.waveHeight)));
            }

            @Override
            protected void applyValue() {
                VulkanGlobals.waveHeight = (float) (this.value * 2.0);
                VulkanGlobals.markDirty(); // Flags UBO for re-upload before next vkQueueSubmit
            }
        });

        // 2. Live Caustics / Refraction Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Caustics: " + (VulkanGlobals.enableCaustics ? "ON" : "OFF")),
                btn -> {
                    VulkanGlobals.enableCaustics = !VulkanGlobals.enableCaustics;
                    VulkanGlobals.markDirty();
                    btn.setMessage(Component.literal("Caustics: " + (VulkanGlobals.enableCaustics ? "ON" : "OFF")));
                })
                .bounds(x, y + 26, panelWidth, 20)
                .build());

        // 3. Close Overlay & Persist to Disk
        this.addRenderableWidget(Button.builder(Component.literal("Save & Close"), btn -> this.onClose())
                .bounds(x, y + 58, panelWidth, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw a subtle translucent sidebar backdrop instead of full-screen dimming
        graphics.fill(6, 12, 198, 115, 0x88000000);
        graphics.drawString(this.font, this.title, 12, 14, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Leave empty to prevent default full-screen blur or dark overlay
    }

    @Override
    public void onClose() {
        VulkanGlobals.saveConfig(); // Persist final values to disk once closed
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
