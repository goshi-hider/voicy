package dev.goshi.voicy.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class VoicyScreen extends Screen {
	private static final int DIM_COLOR = 0x90101010;

	private boolean backgroundDone;

	protected VoicyScreen(Text title) {
		super(title);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		if (backgroundDone) {
			return;
		}

		context.fill(0, 0, this.width, this.height, dimColor());
	}

	@Override
	public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
		backgroundDone = false;
		renderBackground(context, mouseX, mouseY, delta);
		backgroundDone = true;
		try {
			renderPanels(context, mouseX, mouseY, delta);
			super.render(context, mouseX, mouseY, delta);
		} finally {
			backgroundDone = false;
		}
	}

	protected int dimColor() {
		return DIM_COLOR;
	}

	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
