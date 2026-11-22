package dev.thorntimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.ChatFormatting;

/**
 * Thorn Timer
 *
 * Starts a 17 second countdown the moment Thorn's first boss line appears in chat
 * (Dungeons F4 / M4), shows it on the HUD, and flashes a big "THROW" title at 0.
 */
public class ThornTimer implements ClientModInitializer {

    /** Countdown length in milliseconds. */
    private static final long DURATION_MS = 17_000L;
    /** How long the THROW title stays on screen (ticks). */
    private static final int TITLE_FADE_IN = 0;
    private static final int TITLE_STAY = 30;
    private static final int TITLE_FADE_OUT = 10;
    /** Ignore further Thorn lines for this long after a start so the timer is not restarted. */
    private static final long LOCKOUT_MS = 120_000L;

    private static long startTime = -1L;   // System.currentTimeMillis() at fight start, -1 = idle
    private static boolean thrown = false; // THROW title already shown for this run
    private static long lastStart = -1L;

    @Override
    public void onInitializeClient() {
        // Detect the fight start from chat. Thorn's first line is
        // "[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! ..."
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return; // ignore action bar
            String text = ChatFormatting.stripFormatting(message.getString());
            if (text == null) return;
            if (text.startsWith("[BOSS] Thorn:")) {
                long now = System.currentTimeMillis();
                if (startTime < 0 && (lastStart < 0 || now - lastStart > LOCKOUT_MS)) {
                    start(now);
                }
            }
        });

        // Fire the title the instant the countdown reaches 0.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (startTime < 0 || thrown) return;
            if (remainingMs() <= 0) {
                thrown = true;
                showThrow(mc);
            }
        });

        // Reset when leaving / switching worlds.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset());

        // HUD counter.
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("thorn-timer", "counter"),
                (graphics, tickCounter) -> renderCounter(graphics));
    }

    private static void start(long now) {
        startTime = now;
        lastStart = now;
        thrown = false;
    }

    private static void reset() {
        startTime = -1L;
        thrown = false;
        lastStart = -1L;
    }

    private static long remainingMs() {
        return DURATION_MS - (System.currentTimeMillis() - startTime);
    }

    private static void showThrow(Minecraft mc) {
        // The big "THROW" text is drawn by the HUD renderer below.
    }

    private static void drawCentered(GuiGraphicsExtractor graphics, Minecraft mc,
                                     String label, float scale, float centerY, int color) {
        int width = mc.font.width(label);
        float x = (graphics.guiWidth() - width * scale) / 2f;
        float y = centerY - (mc.font.lineHeight * scale) / 2f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(mc.font, label, 0, 0, color, true);
        graphics.pose().popMatrix();
    }

    private static void renderCounter(GuiGraphicsExtractor graphics) {
        if (startTime < 0) return;
        Minecraft mc = Minecraft.getInstance();

        long rem = remainingMs();
        if (rem < -3_000L) { // hide a few seconds after THROW
            startTime = -1L;
            return;
        }

        float centerY = graphics.guiHeight() / 2f;

        if (rem > 0) {
            String label = String.format("Thorn: %.1fs", rem / 1000.0);
            int color = rem > 5_000L ? 0xFFFFFFFF : 0xFFFFAA00;
            drawCentered(graphics, mc, label, 2.0f, centerY + 30f, color);
        } else {
            // Big THROW in the middle of the screen
            drawCentered(graphics, mc, "THROW", 8.0f, centerY - 20f, 0xFFFF5555);
        }
    }
}
