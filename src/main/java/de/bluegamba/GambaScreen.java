package de.bluegamba;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/** Eine kompakte, blau gehaltene Chest-inspirierte Slotmaschine mit 3x3 Erz-Walzen. */
public final class GambaScreen extends Screen {
    private static final int PANEL_WIDTH = 432;
    private static final int PANEL_HEIGHT = 300;
    private static final int BLUE = 0xFF164E8A;
    private static final int LIGHT_BLUE = 0xFF52B8FF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF6666;
    private static final int GREEN = 0xFF7CFF91;
    private static final int GOLD = 0xFFFFD166;

    private static final Ore[] ORES = Ore.values();
    private final ItemStack[] symbols = new ItemStack[9];

    private EditBox wagerInput;
    private Button spinButton;
    private boolean spinning;
    private long activeWager;
    private long spinEndsAtNanos;
    private long nextShuffleAtNanos;
    private String status = "Gib einen Einsatz ein und druecke DREHEN.";
    private int statusColor = WHITE;

    public GambaScreen() {
        super(Component.literal("Blue Gamba"));
        Item[] openingSymbols = {
                Items.COAL_ORE, Items.IRON_ORE, Items.GOLD_ORE,
                Items.DIAMOND_ORE, Items.EMERALD_ORE, Items.ANCIENT_DEBRIS,
                Items.COAL_ORE, Items.DIAMOND_ORE, Items.EMERALD_ORE
        };
        for (int i = 0; i < openingSymbols.length; i++) {
            symbols[i] = new ItemStack(openingSymbols[i]);
        }
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        wagerInput = new EditBox(this.font, left + 28, top + 218, 112, 20,
                Component.literal("Einsatz in Coins"));
        wagerInput.setMaxLength(12);
        wagerInput.setValue("100");
        wagerInput.setHint(Component.literal("z. B. 100"));
        wagerInput.active = !spinning;
        addRenderableWidget(wagerInput);

        spinButton = Button.builder(Component.literal("DREHEN"), button -> spin())
                .bounds(left + 151, top + 218, 112, 20)
                .build();
        spinButton.active = !spinning;
        addRenderableWidget(spinButton);
    }

    private void spin() {
        if (spinning) {
            return;
        }

        final long wager;
        try {
            String raw = wagerInput.getValue().trim();
            if (raw.isEmpty()) {
                throw new NumberFormatException("empty");
            }
            wager = Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            showError("Der Einsatz muss eine ganze Zahl groesser als 0 sein.");
            return;
        }

        if (wager <= 0) {
            showError("Der Einsatz muss mindestens 1 Coin betragen.");
            return;
        }
        if (wager > GambaAccount.balance) {
            showError("Dafuer hast du nicht genug Coins.");
            return;
        }

        activeWager = wager;
        GambaAccount.balance -= wager;
        GambaAccount.save();

        long now = System.nanoTime();
        long durationMillis = ThreadLocalRandom.current().nextLong(2_000L, 4_001L);
        spinEndsAtNanos = now + durationMillis * 1_000_000L;
        nextShuffleAtNanos = now;
        spinning = true;
        statusColor = LIGHT_BLUE;
        status = "Die Walzen drehen sich...";
        wagerInput.active = false;
        spinButton.active = false;
    }

    private void showError(String message) {
        status = "Fehler: " + message;
        statusColor = RED;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[Blue Gamba] " + message));
        }
    }

    private void randomizeSymbols() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < symbols.length; i++) {
            symbols[i] = new ItemStack(ORES[random.nextInt(ORES.length)].item);
        }
    }

    private void finishSpin() {
        spinning = false;
        wagerInput.active = true;
        spinButton.active = true;

        // Die drei Symbole der mittleren Reihe sind die Gewinnlinie (Index 3, 4, 5).
        Ore left = findOre(symbols[3]);
        Ore middle = findOre(symbols[4]);
        Ore right = findOre(symbols[5]);

        if (left == middle && middle == right) {
            long payout = Math.round(activeWager * left.multiplier);
            GambaAccount.balance += payout;
            GambaAccount.save();
            long net = payout - activeWager;
            status = "3x " + left.label + "! Auszahlung: " + payout + " | Netto: " + signed(net) + " Coins";
            statusColor = net > 0 ? GREEN : (net < 0 ? GOLD : WHITE);
        } else {
            status = "Kein Drilling. Verlorener Einsatz: " + activeWager + " Coins.";
            statusColor = RED;
        }
    }

    private static String signed(long amount) {
        return amount > 0 ? "+" + amount : Long.toString(amount);
    }

    private static Ore findOre(ItemStack stack) {
        for (Ore ore : ORES) {
            if (stack.is(ore.item)) {
                return ore;
            }
        }
        return Ore.COAL;
    }

    @Override
    public void tick() {
        super.tick();
        if (wagerInput != null) {
            wagerInput.tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        if (spinning) {
            if (now >= spinEndsAtNanos) {
                finishSpin();
            } else if (now >= nextShuffleAtNanos) {
                randomizeSymbols();
                nextShuffleAtNanos = now + 75_000_000L;
            }
        }

        graphics.fill(0, 0, this.width, this.height, 0xB9000717);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        // Chest-inspiriertes Panel mit blauem Rahmen und separatem Innenbereich.
        graphics.fill(left - 4, top - 4, left + PANEL_WIDTH + 4, top + PANEL_HEIGHT + 4, 0xFF071A30);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF102B49);
        graphics.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + 31, BLUE);
        graphics.fill(left + 12, top + 34, left + PANEL_WIDTH - 12, top + 35, 0xFF2C76B8);

        graphics.drawCenteredString(this.font, Component.literal("BLUE GAMBA  |  ERZ-SLOT"), this.width / 2, top + 12, WHITE);
        graphics.drawString(this.font, "3 gleiche Symbole in der Mitte zahlen aus", left + 18, top + 40, LIGHT_BLUE);

        renderReels(graphics, left, top);
        renderPayoutTable(graphics, left, top);

        graphics.drawString(this.font, "Guthaben: " + GambaAccount.balance + " Coins", left + 28, top + 183, WHITE);
        graphics.drawString(this.font, "Einsatz (nur positive Werte)", left + 28, top + 201, LIGHT_BLUE);
        graphics.drawString(this.font, spinning ? "DREHT..." : "2-4 Sekunden pro Dreh", left + 279, top + 223,
                spinning ? GOLD : 0xFFB9D8F3);

        drawStatus(graphics, left + 18, top + 248, PANEL_WIDTH - 36);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderReels(GuiGraphics graphics, int left, int top) {
        int startX = left + 28;
        int startY = top + 55;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = startX + col * 39;
                int y = startY + row * 39;
                boolean winningLine = row == 1;
                int fill = winningLine ? 0xFF174A78 : 0xFF0B1E33;
                int outline = winningLine ? LIGHT_BLUE : 0xFF37618B;
                graphics.fill(x, y, x + 34, y + 34, fill);
                graphics.fill(x, y, x + 34, y + 2, outline);
                graphics.fill(x, y + 32, x + 34, y + 34, outline);
                graphics.fill(x, y, x + 2, y + 34, outline);
                graphics.fill(x + 32, y, x + 34, y + 34, outline);
                graphics.renderItem(symbols[row * 3 + col], x + 9, y + 9);
            }
        }
        graphics.drawString(this.font, "GEWINNLINIE", startX + 1, startY + 3 * 39 + 1, LIGHT_BLUE);
    }

    private void renderPayoutTable(GuiGraphics graphics, int left, int top) {
        graphics.drawString(this.font, "MULTIPLIKATOREN", left + 174, top + 43, LIGHT_BLUE);
        for (int i = 0; i < ORES.length; i++) {
            Ore ore = ORES[i];
            int y = top + 57 + i * 17;
            graphics.renderItem(new ItemStack(ore.item), left + 174, y - 3);
            graphics.drawString(this.font, ore.label, left + 196, y, WHITE);
            graphics.drawString(this.font, ore.multiplierLabel, left + 350, y, ore.multiplier >= 5.0 ? GOLD : 0xFFB9D8F3);
        }
    }

    private void drawStatus(GuiGraphics graphics, int x, int y, int maxWidth) {
        String remaining = status;
        int lineY = y;
        for (int line = 0; line < 2 && !remaining.isEmpty(); line++) {
            String current = this.font.plainSubstrByWidth(remaining, maxWidth);
            if (current.isEmpty()) {
                break;
            }
            graphics.drawString(this.font, current, x, lineY, statusColor);
            remaining = remaining.substring(current.length()).stripLeading();
            lineY += 12;
        }
    }

    @Override
    public void onClose() {
        // Einsatz und Dreh bleiben nicht in einem unbeendeten Zwischenzustand zurueck.
        if (spinning) {
            status = "Bitte warten: Die Drehung muss erst enden.";
            statusColor = GOLD;
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Ore {
        COAL(Items.COAL_ORE, "Kohle", 0.5, "0.5x"),
        IRON(Items.IRON_ORE, "Eisen", 1.2, "1.2x"),
        GOLD(Items.GOLD_ORE, "Gold", 2.0, "2.0x"),
        DIAMOND(Items.DIAMOND_ORE, "Diamant", 5.0, "5.0x"),
        EMERALD(Items.EMERALD_ORE, "Smaragd", 10.0, "10.0x"),
        NETHERITE(Items.ANCIENT_DEBRIS, "Netherit", 25.0, "25.0x");

        private final Item item;
        private final String label;
        private final double multiplier;
        private final String multiplierLabel;

        Ore(Item item, String label, double multiplier, String multiplierLabel) {
            this.item = item;
            this.label = label;
            this.multiplier = multiplier;
            this.multiplierLabel = multiplierLabel;
        }
    }
}
