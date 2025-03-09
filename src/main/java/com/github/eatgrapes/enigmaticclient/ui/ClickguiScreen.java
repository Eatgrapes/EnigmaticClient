package com.github.eatgrapes.enigmaticclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public class ClickguiScreen extends GuiScreen {
    private static final int ANIMATION_DURATION = 400;
    private static final int CORNER_SEGMENTS = 16; // 每个圆角细分段数
    private long animationStartTime;
    private boolean closing = false;
    private final int MD3_PRIMARY = 0xFFD0BCFF; // MD3标准紫色

    public ClickguiScreen() {
        animationStartTime = System.currentTimeMillis();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 禁用默认背景
        GlStateManager.enableBlend();
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        // 计算动画进度
        float progress = Math.min(1.0f, (System.currentTimeMillis() - animationStartTime) / ANIMATION_DURATION);
        progress = closing ? 1.0f - progress : progress;
        progress = (float) Math.pow(progress, 0.6);

        // 动态尺寸
        int panelWidth = (int) (360 * progress);
        int panelHeight = (int) (240 * progress);
        int radius = (int) (16 * progress);
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - panelHeight) / 2;

        // 开启抗锯齿
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 绘制完整圆角面板
        drawRoundedPanel(x, y, panelWidth, panelHeight, radius);

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

private void drawRoundedPanel(int x, int y, int width, int height, int radius) {
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glColor4f(
        (MD3_PRIMARY >> 16 & 0xFF) / 255f,
        (MD3_PRIMARY >> 8 & 0xFF) / 255f,
        (MD3_PRIMARY & 0xFF) / 255f,
        (MD3_PRIMARY >> 24 & 0xFF) / 255f
    );

    // 修复后的drawRect调用（添加颜色参数）
    drawRect(
        x + radius, 
        y + radius, 
        x + width - radius, 
        y + height - radius,
        MD3_PRIMARY // 新增第五个颜色参数
    );

    // 绘制四个圆角
    drawRoundedCorner(x + radius, y + radius, radius, 180, 270);
    drawRoundedCorner(x + width - radius, y + radius, radius, 270, 360);
    drawRoundedCorner(x + width - radius, y + height - radius, radius, 0, 90);
    drawRoundedCorner(x + radius, y + height - radius, radius, 90, 180);

    GL11.glEnable(GL11.GL_TEXTURE_2D);
}

    private void drawRoundedCorner(int centerX, int centerY, int radius, int startAngle, int endAngle) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2i(centerX, centerY); // 圆心

        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            float angle = (float) Math.toRadians(startAngle + (endAngle - startAngle) * i / CORNER_SEGMENTS);
            float x = centerX + radius * (float) Math.cos(angle);
            float y = centerY + radius * (float) Math.sin(angle);
            GL11.glVertex2f(x, y);
        }

        GL11.glEnd();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closing = true;
            animationStartTime = System.currentTimeMillis();
            new Thread(() -> {
                try {
                    Thread.sleep(ANIMATION_DURATION);
                    Minecraft.getMinecraft().displayGuiScreen(null);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}