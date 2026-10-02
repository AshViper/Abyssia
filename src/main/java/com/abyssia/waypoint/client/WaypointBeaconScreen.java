package com.abyssia.waypoint.client;

import com.abyssia.network.AbyssiaNetwork;
import com.abyssia.registry.ModIndustry;
import com.abyssia.waypoint.WaypointColors;
import com.abyssia.waypoint.WaypointSavePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * W01 settings screen (176x166, no container): name box, 16 colour swatches (8x2, white frame on the selected one,
 * colour name on hover), the current colour, Save / Cancel. Save sends {@link WaypointSavePacket}; the server checks it.
 */
public class WaypointBeaconScreen extends Screen
{
    private static final int W = 176;
    private static final int H = 166;
    private static final int SWATCH = 18;
    private static final int SWATCH_STEP = 20;
    private static final int SWATCH_X = 9;
    private static final int SWATCH_Y = 70;
    private static final int TEXT = 0x404040;

    private final BlockPos pos;
    private final String initialName;
    private int color;
    private int left;
    private int top;
    private EditBox nameBox;
    private Button saveButton;

    public WaypointBeaconScreen(BlockPos pos, String name, int color)
    {
        super(Component.translatable("block.abyssia.waypoint_beacon"));
        this.pos = pos;
        this.initialName = name.isEmpty() ? Component.translatable("block.abyssia.waypoint_beacon").getString() : name;
        this.color = WaypointColors.clamp(color);
    }

    @Override
    protected void init()
    {
        left = (width - W) / 2;
        top = (height - H) / 2;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.abyssia.waypoint.save"), b -> save())
                .bounds(left + 9, top + 138, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.abyssia.waypoint.cancel"), b -> onClose())
                .bounds(left + 91, top + 138, 76, 20).build());
        String value = nameBox != null ? nameBox.getValue() : initialName;     // keep typed text on resize
        nameBox = new EditBox(font, left + 48, top + 38, 119, 16, Component.translatable("gui.abyssia.waypoint.name"));
        nameBox.setMaxLength(WaypointClient.maxNameLength());
        nameBox.setValue(value);
        nameBox.setResponder(s -> saveButton.active = !s.trim().isEmpty());
        saveButton.active = !nameBox.getValue().trim().isEmpty();
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
    }

    private void save()
    {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        AbyssiaNetwork.sendToServer(new WaypointSavePacket(pos, name, color));
        onClose();
    }

    private int swatchAt(double mx, double my)
    {
        for (int i = 0; i < WaypointColors.COUNT; i++)
        {
            int x = left + SWATCH_X + (i % 8) * SWATCH_STEP;
            int y = top + SWATCH_Y + (i / 8) * SWATCH_STEP;
            if (mx >= x && mx < x + SWATCH && my >= y && my < y + SWATCH) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button)
    {
        int i = button == 0 ? swatchAt(mx, my) : -1;
        if (i >= 0)
        {
            color = i;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers)
    {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && saveButton.active)
        {
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick)
    {
        renderBackground(g);
        // vanilla-style panel: black outline, white top-left highlight, dark bottom-right shadow
        g.fill(left, top, left + W, top + H, 0xFF000000);
        g.fill(left + 1, top + 1, left + W - 1, top + H - 1, 0xFFFFFFFF);
        g.fill(left + 2, top + 2, left + W - 1, top + H - 1, 0xFF555555);
        g.fill(left + 2, top + 2, left + W - 2, top + H - 2, 0xFFC6C6C6);

        g.pose().pushPose();
        g.pose().translate(left + 8, top + 8, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.renderItem(new ItemStack(ModIndustry.WAYPOINT_BEACON.get()), 0, 0);
        g.pose().popPose();

        g.drawString(font, title, left + 48 + (119 - font.width(title)) / 2, top + 12, TEXT, false);
        g.drawString(font, Component.translatable("gui.abyssia.waypoint.name"), left + 48, top + 28, TEXT, false);
        g.drawString(font, Component.translatable("gui.abyssia.waypoint.colour"), left + SWATCH_X, top + SWATCH_Y - 10, TEXT, false);

        int hovered = swatchAt(mx, my);
        for (int i = 0; i < WaypointColors.COUNT; i++)
        {
            int x = left + SWATCH_X + (i % 8) * SWATCH_STEP;
            int y = top + SWATCH_Y + (i / 8) * SWATCH_STEP;
            int frame = i == color ? 0xFFFFFFFF : i == hovered ? 0xFFA0A0A0 : 0xFF373737;
            g.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, frame);
            // selected: 3px white frame (1 outside + 2 inside) with a black ring before the colour
            if (i == color)
            {
                g.fill(x, y, x + SWATCH, y + SWATCH, 0xFFFFFFFF);
                g.fill(x + 2, y + 2, x + SWATCH - 2, y + SWATCH - 2, 0xFF000000);
            }
            int inset = i == color ? 3 : 0;
            g.fill(x + inset, y + inset, x + SWATCH - inset, y + SWATCH - inset, 0xFF000000 | WaypointColors.rgb(i));
        }
        g.drawString(font, Component.translatable("gui.abyssia.waypoint.current", Component.translatable(WaypointColors.nameKey(color))),
                left + SWATCH_X, top + SWATCH_Y + 2 * SWATCH_STEP + 4, TEXT, false);

        super.render(g, mx, my, partialTick);
        if (hovered >= 0) g.renderTooltip(font, Component.translatable(WaypointColors.nameKey(hovered)), mx, my);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
