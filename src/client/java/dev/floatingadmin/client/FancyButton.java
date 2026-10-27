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

/** A widget drawn with the crimson sprites. Looks: BUTTON, ROW (list line), SLOT (item square), SPRITE (any sprite). */
final class FancyButton extends AbstractButton {
	enum Look { BUTTON, ROW, SLOT, SPRITE }

	private static final Identifier[][] SPRITES = new Identifier[4][3];
	private static final Identifier ROW_NORMAL = id("row_normal");
	private static final Identifier ROW_SELECTED = id("row_selected");
	private static final Identifier SLOT_NORMAL = id("slot_normal");
	private static final Identifier SLOT_HOVER = id("slot_hover");
	private static final Identifier SLOT_SELECTED = id("slot_selected");

	static {
		String[] states = {"normal", "hover", "pressed"};
		for (int t = 0; t < 4; t++) {
			for (int s = 0; s < 3; s++) {
				SPRITES[t][s] = id("btn" + t + "_" + states[s]);
			}
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(FloatingAdminClient.MOD_ID, path);
	}

	private final String label;
	private final int theme;
	private final Runnable action;
	private final boolean confirm;
	private Look look = Look.BUTTON;
	private ItemStack icon = ItemStack.EMPTY;
	private Identifier iconSprite = null;
	private Identifier baseSprite = null;
	private String tooltip = null;
	private String info = null;
	private boolean leftAligned = false;
	private boolean highlighted = false;
	private int textColor = 0;
	private long pressedUntil = 0;
	private long confirmUntil = 0;

	FancyButton(int x, int y, int w, int h, String label, int theme, boolean confirm, Runnable action) {
		super(x, y, w, h, Component.literal(label));
		this.label = label;
		this.theme = Math.max(0, Math.min(3, theme));
		this.confirm = confirm;
		this.action = action;
	}

	FancyButton look(Look l) {
		this.look = l;
		return this;
	}

	/** Draws this sprite as the whole button (look SPRITE). */
	FancyButton base(String spriteName) {
		this.look = Look.SPRITE;
		this.baseSprite = id(spriteName);
		return this;
	}

	FancyButton icon(ItemStack stack) {
		this.icon = stack;
		return this;
	}

	/** Draws a small sprite from textures/gui/sprites instead of text. */
	FancyButton sprite(String name) {
		this.iconSprite = id(name);
		return this;
	}

	FancyButton tooltip(String text) {
		this.tooltip = text;
		return this;
	}

	/** Gray text on the right side (rows). */
	FancyButton info(String text) {
		this.info = text;
		return this;
	}

	FancyButton left(boolean on) {
		this.leftAligned = on;
		return this;
	}

	FancyButton color(int argb) {
		this.textColor = argb;
		return this;
	}

	/** Draws the widget in its hover/selected look to mark it as the current choice. */
	FancyButton highlighted(boolean on) {
		this.highlighted = on;
		return this;
	}

	private static String fit(Font font, String text, int maxPx) {
		String t = text;
		while (t.length() > 1 && font.width(t) > maxPx) {
			t = t.substring(0, t.length() - 2) + "…";
		}
		return t;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		boolean hover = this.isHovered();
		Identifier sprite;
		switch (look) {
			case ROW -> sprite = (hover || highlighted) ? ROW_SELECTED : ROW_NORMAL;
			case SLOT -> sprite = highlighted ? SLOT_SELECTED : hover ? SLOT_HOVER : SLOT_NORMAL;
			case SPRITE -> sprite = baseSprite;
			default -> sprite = SPRITES[theme][now < pressedUntil ? 2 : (hover || highlighted) ? 1 : 0];
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.width, this.height);
		if (look == Look.SPRITE && hover) {
			graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, 0x30FFFFFF);
		}

		Font font = Minecraft.getInstance().font;
		if (!icon.isEmpty()) {
			graphics.fakeItem(icon, this.getX() + (this.width - 16) / 2, this.getY() + (this.height - 16) / 2);
		} else if (iconSprite != null) {
			int size = Math.max(8, Math.min(16, Math.min(this.width, this.height) - 2));
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, iconSprite, this.getX() + (this.width - size) / 2,
					this.getY() + (this.height - size) / 2, size, size);
		} else if (!label.isEmpty()) {
			boolean asking = confirm && now < confirmUntil;
			int infoW = info != null ? Math.min(font.width(info), this.width / 2) : 0;
			String shown = fit(font, asking ? "Sure?" : label, this.width - 10 - (infoW > 0 ? infoW + 6 : 0));
			int color = asking ? 0xFFFF6B6B : textColor != 0 ? textColor : (hover ? 0xFFFFFFFF : 0xFFD8D9DE);
			int tw = font.width(shown);
			int tx = leftAligned ? this.getX() + 6 : this.getX() + (this.width - tw) / 2;
			int ty = this.getY() + (this.height - font.lineHeight) / 2 + 1;
			graphics.text(font, Component.literal(shown), tx, ty, color, true);
			if (info != null && infoW > 0) {
				String infoShown = fit(font, info, infoW);
				graphics.text(font, Component.literal(infoShown), this.getX() + this.width - 6 - font.width(infoShown), ty, 0xFF8A8D96, false);
			}
		}

		if (hover && tooltip != null) {
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
