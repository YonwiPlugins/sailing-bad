package com.yonwiplugins.sailingbad;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.runelite.client.ui.FontManager;

public final class TotalTilePreview
{
	private static final int WIDTH = 62;
	private static final int HEIGHT = 32;
	private static final int SCALE = 4;
	private static final int REFERENCE_WIDTH = 296;
	private static final int REFERENCE_HEIGHT = 412;
	private static final int SAILING_X = 199;
	private static final int SAILING_Y = 333;
	private static final int SAILING_WIDTH = 97;
	private static final int SAILING_HEIGHT = 47;

	private TotalTilePreview()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File output = new File(args[0]);
		output.getParentFile().mkdirs();

		BufferedImage preview;
		double scaleX;
		double scaleY;
		if (args.length > 1)
		{
			BufferedImage skillsTab = ImageIO.read(new File(args[1]));
			// Use Sailing itself from the untouched Skills tab reference. Construction's
			// bottom-left shell has different grid joins; only Sailing has the exact
			// top-right, bottom-right and outer-panel corners the replacement occupies.
			int x = skillsTab.getWidth() * SAILING_X / REFERENCE_WIDTH;
			int y = skillsTab.getHeight() * SAILING_Y / REFERENCE_HEIGHT;
			int width = skillsTab.getWidth() * SAILING_WIDTH / REFERENCE_WIDTH;
			int height = skillsTab.getHeight() * SAILING_HEIGHT / REFERENCE_HEIGHT;
			preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D copy = preview.createGraphics();
			copy.drawImage(skillsTab, 0, 0, width, height, x, y, x + width, y + height, null);
			copy.dispose();
			scaleX = (double) width / WIDTH;
			scaleY = (double) height / HEIGHT;
		}
		else
		{
			preview = new BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_RGB);
			scaleX = SCALE;
			scaleY = SCALE;
		}

		Graphics2D graphics = preview.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		if (args.length == 1)
		{
			// Synthetic fallback when no screenshot is supplied.
			graphics.setColor(new Color(0x2C2A23));
			graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
			graphics.setColor(new Color(0x716350));
			graphics.setStroke(new java.awt.BasicStroke(SCALE));
			graphics.drawRect(SCALE, SCALE, (WIDTH - 3) * SCALE, (HEIGHT - 3) * SCALE);
		}

		for (int layer = 0; layer < 5; layer++)
		{
			int[] rectangle = SailingBadPlugin.panelLayer(layer, WIDTH, HEIGHT);
			graphics.setColor(new Color(rectangle[4]));
			graphics.fillRect(
				(int) Math.round(rectangle[0] * scaleX),
				(int) Math.round(rectangle[1] * scaleY),
				(int) Math.round(rectangle[2] * scaleX),
				(int) Math.round(rectangle[3] * scaleY));
		}

		graphics.setColor(Color.YELLOW);
		Font font = fitFont(
			graphics,
			FontManager.getRunescapeSmallFont(),
			(float) (14.5 * scaleY),
			"Total level:",
			(int) Math.round((WIDTH - 12) * scaleX));
		graphics.setFont(font);
		drawCentered(graphics, preview.getWidth(), "Total level:", (int) Math.round(14 * scaleY));
		drawCentered(graphics, preview.getWidth(), "2277", (int) Math.round(28 * scaleY));
		graphics.dispose();

		ImageIO.write(preview, "png", output);
		System.out.println(output.getAbsolutePath());
	}

	private static void drawCentered(Graphics2D graphics, int width, String text, int baseline)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.drawString(text, (width - metrics.stringWidth(text)) / 2, baseline);
	}

	private static Font fitFont(Graphics2D graphics, Font base, float maximumSize, String text, int maximumWidth)
	{
		Font candidate = base.deriveFont(maximumSize);
		FontMetrics metrics = graphics.getFontMetrics(candidate);
		if (metrics.stringWidth(text) <= maximumWidth)
		{
			return candidate;
		}

		return candidate.deriveFont(maximumSize * maximumWidth / metrics.stringWidth(text));
	}
}
