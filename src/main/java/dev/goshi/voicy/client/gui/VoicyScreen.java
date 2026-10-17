package dev.goshi.voicy.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base class for every Voicy screen.
 *
 * Vanilla's {@link Screen#renderBackground} applies a full-screen post-process blur (scaled by the
 * player's "Menu Background Blurriness" option) plus a darkening layer. In 1.21 {@link Screen#render}
 * already calls renderBackground itself, so screens that also called it manually blurred twice.
 * We override it with a flat translucent dim: crisp text, no blur, no global option juggling.
 */
public abstract class VoicyScreen extends Screen {

	private static final int DIM_COLOR = 0x90101010;

	/** True while super.render() runs, so its internal renderBackground() call doesn't paint over our panels. */
	private boolean backgroundDone;

	protected VoicyScreen(Text title) {
		super(title);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		if (backgroundDone) {
			return;
		}
		// Intentionally NOT calling super: that is what triggers the blur.
		context.fill(0, 0, this.width, this.height, DIM_COLOR);
	}

	@Override
	public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
		backgroundDone = false;
		renderBackground(context, mouseX, mouseY, delta);
		backgroundDone = true;
		try {
			renderPanels(context, mouseX, mouseY, delta);
			super.render(context, mouseX, mouseY, delta); // widgets, drawn on top of the panels
		} finally {
			backgroundDone = false;
		}
	}

	/** Draw custom panels/text here: runs after the dim background and before the widgets. */
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
