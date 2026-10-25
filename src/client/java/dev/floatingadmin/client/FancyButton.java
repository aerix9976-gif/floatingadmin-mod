package dev.floatingadmin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** A button drawn with the neon sprites. Four colour themes (0 violet ... 3 cyan). */
final class FancyButton extends AbstractButton {
	private static final Identifier[][] SPRITES = new Identifier[4][3];

	static {
		String[] states = {"normal", "hover", "pressed"};
		for (int t = 0; t < 4; t++) {
			for (int s = 0; s < 3; s++) {
				SPRITES[t][s] = Identifier.fromNamespaceAndPath(FloatingAdminClient.MOD_ID, "btn" + t + "_" + states[s]);
			}
		}
	}

	private final String label;
	private final int theme;
	private final Runnable action;
	private final boolean confirm;
	private ItemStack icon = ItemStack.EMPTY;
	private Identifier iconSprite = null;
	private String tooltip = null;
	private boolean highlighted = false;
	private long pressedUntil = 0;
	private long confirmUntil = 0;

	FancyButton(int x, int y, int w, int h, String label, int theme, boolean confirm, Runnable action) {
		super(x, y, w, h, Component.literal(label));
		this.label = label;
		this.theme = Math.max(0, Math.min(3, theme));
		this.confirm = confirm;
		this.action = action;
	}

	FancyButton icon(ItemStack stack) {
		this.icon = stack;
		return this;
	}

	/** Draws a 16x16 sprite from textures/gui/sprites instead of text. */
	FancyButton sprite(String name) {
		this.iconSprite = Identifier.fromNamespaceAndPath(FloatingAdminClient.MOD_ID, name);
		return this;
	}

	FancyButton tooltip(String text) {
		this.tooltip = text;
		return this;
	}

	/** Draws the button in its hover look to mark it as the current selection. */
	FancyButton highlighted(boolean on) {
		this.highlighted = on;
		return this;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		int state = now < pressedUntil ? 2 : (this.isHovered() || highlighted) ? 1 : 0;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES[theme][state], this.getX(), this.getY(), this.width, this.height);

		Font font = Minecraft.getInstance().font;
		if (!icon.isEmpty()) {
			graphics.fakeItem(icon, this.getX() + (this.width - 16) / 2, this.getY() + (this.height - 16) / 2);
		} else if (iconSprite != null) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, iconSprite, this.getX() + (this.width - 16) / 2,
					this.getY() + (this.height - 16) / 2, 16, 16);
		} else {
			boolean asking = confirm && now < confirmUntil;
			Component text = Component.literal(asking ? "Sure?" : label);
			int color = asking ? 0xFFFF6B6B : (this.isHovered() ? 0xFFFFFFFF : 0xFFD6D8FF);
			int tw = font.width(text);
			graphics.text(font, text, this.getX() + (this.width - tw) / 2,
					this.getY() + (this.height - font.lineHeight) / 2 + 1, color, true);
		}

		if (this.isHovered() && tooltip != null) {
			graphics.setTooltipForNextFrame(font, Component.literal(tooltip), mouseX, mouseY);
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		long now = System.currentTimeMillis();
		pressedUntil = now + 140;
		if (confirm) {
			if (now < confirmUntil) {
				confirmUntil = 0;
				action.run();
			} else {
				confirmUntil = now + 2500;
			}
			return;
		}
		action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
