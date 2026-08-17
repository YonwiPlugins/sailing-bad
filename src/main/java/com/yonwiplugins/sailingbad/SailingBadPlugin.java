package com.yonwiplugins.sailingbad;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Sailing Bad",
	description = "Hides Sailing from the skills tab and puts the total level back to 2277",
	tags = {"sailing", "skills", "total", "level", "2277", "hide"}
)
@Slf4j
public class SailingBadPlugin extends Plugin
{
	private static final String ROW_SEPARATOR = "<br>";
	// The tiles are laid out column by column, eight rows to a column, so the
	// constants run Attack..Construction, Hitpoints..Hunter, Mining..Sailing.
	private static final int FIRST_TILE = InterfaceID.Stats.ATTACK;
	private static final int LAST_TILE = InterfaceID.Stats.SAILING;
	private static final int ROW_COUNT = 8;
	private static final int TYPE_RECTANGLE = 3;
	private static final int TYPE_TEXT = 4;
	private static final int TYPE_GRAPHIC = 5;
	private static final int PANEL_COLOUR = 0x000000;
	private static final int PANEL_LAYER_COUNT = 5;
	private static final int BOTTOM_PANEL_PADDING = 5;
	private static final String TOTAL_LEVEL_LABEL = "total level";
	private static final String TOTAL_XP_LABEL = "total xp";
	private static final String SAILING_LABEL = "sailing";
	private static final String SAILING_XP_LABEL = "sailing xp";
	private static final int TOOLTIP_BUILD_SCRIPT = 2344;
	static final int TOOLTIP_FONT_ID = FontID.PLAIN_12;
	private static final int TOOLTIP_LABEL_CHILD_INDEX = 2;
	private static final int TOOLTIP_VALUE_CHILD_INDEX = 3;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SailingBadConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	// The bar the total level sits in before we shrink it into the Sailing slot.
	private Layout totalHome;
	// The y and height of each row as Sailing leaves them.
	private int[] rowHomeY;
	private int[] rowHomeHeight;
	// The total level bar's own children, before we repaint them as a tile.
	private ChildState[] totalSkinHome;
	private final Map<Widget, Boolean> sailingContentHome = new IdentityHashMap<>();
	private TextStyle totalTextHomeStyle;
	private boolean sailingHidden;
	private SailingBadPanel panel;
	private NavigationButton navButton;

	@Provides
	SailingBadConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SailingBadConfig.class);
	}

	@Override
	protected void startUp()
	{
		forgetLayout();
		panel = injector.getInstance(SailingBadPanel.class);
		navButton = NavigationButton.builder()
			.tooltip("Sailing Bad")
			.icon(createPanelIcon())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			panel = null;
		}

		clientThread.invokeLater(this::restore);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING)
		{
			// The skills tab is rebuilt from scratch on the next login, so the
			// positions we remembered no longer describe anything that exists.
			forgetLayout();
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Widget sailing = client.getWidget(InterfaceID.Stats.SAILING);
		Widget total = client.getWidget(InterfaceID.Stats.TOTAL);
		if (sailing == null || total == null)
		{
			return;
		}

		// Read the untouched layout before anything below writes to it.
		if (totalHome == null)
		{
			totalHome = Layout.of(total);
		}

		// A client script rebuilds the skills tab whenever a level changes or the
		// tab is reopened, so every override has to be reapplied each frame. Each
		// one writes an absolute value rather than a delta so that reapplying an
		// override that is already in place is a no-op.
		applySailingVisibility(sailing);
		applyRowSpacing();
		applyTotalTilePosition(sailing, total);
		applyTotalLevel();
		applyTooltip();
	}

	private void applySailingVisibility(Widget sailing)
	{
		if (config.hideSailing())
		{
			// When Total occupies this slot, Sailing must remain rendered underneath:
			// its widget owns the genuine bevel around the tile. The inset Total panel
			// covers Sailing's icon and levels while leaving that frame visible.
			boolean hideTile = !config.moveTotalLevel();
			if (hideTile)
			{
				restoreSailingContents();
			}
			else
			{
				hideSailingContents(sailing);
			}

			if (sailing.isSelfHidden() != hideTile)
			{
				sailing.setHidden(hideTile);
			}

			sailingHidden = hideTile;
		}
		else
		{
			restoreSailingContents();
			if (sailing.isSelfHidden())
			{
				sailing.setHidden(false);
			}

			sailingHidden = false;
		}
	}

	private void hideSailingContents(Widget sailing)
	{
		Widget[] children = sailing.getChildren();
		if (children == null)
		{
			return;
		}

		int tileWidth = sailing.getWidth();
		int tileHeight = sailing.getHeight();
		for (Widget child : children)
		{
			if (child == null)
			{
				continue;
			}

			boolean isText = child.getType() == TYPE_TEXT;
			boolean isSmallGraphic = child.getType() == TYPE_GRAPHIC
				&& child.getWidth() < tileWidth - 4
				&& child.getHeight() < tileHeight - 4;
			if (isText || isSmallGraphic)
			{
				sailingContentHome.putIfAbsent(child, child.isSelfHidden());
				child.setHidden(true);
			}
		}
	}

	private void restoreSailingContents()
	{
		for (Map.Entry<Widget, Boolean> entry : sailingContentHome.entrySet())
		{
			entry.getKey().setHidden(entry.getValue());
		}
		sailingContentHome.clear();
	}

	private void applyTotalTilePosition(Widget sailing, Widget total)
	{
		if (!config.hideSailing() || !config.moveTotalLevel())
		{
			restoreTotalTilePosition(total);
			return;
		}

		// Sailing pushes the total level onto a row of its own, as a bar spanning
		// all three columns. Taking the Sailing tile's whole layout puts it back in
		// the gap Sailing leaves behind, shaped like the tile it replaces rather
		// than as a full-width bar that would overflow the column. Row spacing has
		// already run, so this picks up the Sailing slot's final geometry.
		Layout slot = Layout.of(sailing);
		if (!slot.matches(total))
		{
			slot.applyTo(total);
			relayout(total);
		}

		applyTotalTileSkin(total);
	}

	/**
	 * Reuses Sailing's complete genuine tile frame and replaces only its grey
	 * interior with a chamfered black fill. Total text renders above it.
	 */
	private void applyTotalTileSkin(Widget total)
	{
		Widget[] children = total.getStaticChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		if (totalSkinHome == null)
		{
			totalSkinHome = new ChildState[children.length];
			for (int i = 0; i < children.length; i++)
			{
				totalSkinHome[i] = ChildState.of(children[i]);
			}
		}

		int width = total.getOriginalWidth();
		int height = total.getOriginalHeight();
		if (rowHomeHeight != null && rowHomeHeight.length == ROW_COUNT)
		{
			// Closing the old Total-bar gap grows the last layout row from 32 to
			// 33 pixels. Sailing's genuine stone skin remains 32 pixels high, just
			// like Construction and Hunter, so the inset must stop at that original
			// edge instead of painting over its bottom bevel and corner joins.
			height = skinHeight(height, rowHomeHeight[ROW_COUNT - 1]);
		}

		int layer = 0;
		for (Widget child : children)
		{
			if (child == null || child.getType() == TYPE_TEXT)
			{
				continue;
			}

			if (layer < PANEL_LAYER_COUNT)
			{
				int[] geometry = panelLayer(layer, width, height);
				paintRectangle(child, geometry);
				layer++;
			}
			else if (!child.isSelfHidden())
			{
				child.setHidden(true);
			}
		}
	}

	static int skinHeight(int layoutHeight, int originalSkinHeight)
	{
		return originalSkinHeight > 0
			? Math.min(layoutHeight, originalSkinHeight)
			: layoutHeight;
	}

	/**
	 * These five horizontal bands are the black inset measured from the original
	 * 62x32 Total tile. They stop above the lower bevel deliberately: Sailing's
	 * genuine shell supplies both colours in that bevel and the asymmetric joins
	 * into the outer panel. Painting a flat highlight there loses the exact
	 * two-step bottom corners.
	 */
	static int[] panelLayer(int layer, int width, int height)
	{
		switch (layer)
		{
			case 0:
				return new int[]{2, 6, Math.max(1, width - 4), Math.max(1, height - 12), PANEL_COLOUR};
			case 1:
				return new int[]{4, 5, Math.max(1, width - 8), 1, PANEL_COLOUR};
			case 2:
				return new int[]{5, 3, Math.max(1, width - 10), 2, PANEL_COLOUR};
			case 3:
				return new int[]{4, Math.max(0, height - 6), Math.max(1, width - 8), 1, PANEL_COLOUR};
			case 4:
				return new int[]{5, Math.max(0, height - 5), Math.max(1, width - 10), 2, PANEL_COLOUR};
			default:
				throw new IllegalArgumentException("Unknown panel layer " + layer);
		}
	}

	private static BufferedImage createPanelIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		graphics.setColor(new Color(0x443B32));
		graphics.fillRect(0, 0, 16, 16);
		graphics.setColor(Color.BLACK);
		graphics.fillRect(2, 2, 12, 12);
		graphics.setColor(Color.YELLOW);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		graphics.drawString("S", 4, 12);
		graphics.dispose();
		return icon;
	}

	private static void paintRectangle(Widget panel, int[] geometry)
	{
		int x = geometry[0];
		int y = geometry[1];
		int width = geometry[2];
		int height = geometry[3];
		int colour = geometry[4];

		panel.setType(TYPE_RECTANGLE);
		panel.setFilled(true);
		panel.setSpriteId(-1);
		panel.setTextColor(colour);
		panel.setOpacity(0);
		panel.setHidden(false);
		panel.setXPositionMode(0);
		panel.setYPositionMode(0);
		panel.setWidthMode(0);
		panel.setHeightMode(0);
		panel.setOriginalX(x);
		panel.setOriginalY(y);
		panel.setOriginalWidth(width);
		panel.setOriginalHeight(height);
		panel.revalidate();
	}

	private void restoreTotalTileSkin(Widget total)
	{
		if (totalSkinHome == null)
		{
			return;
		}

		Widget[] children = total.getStaticChildren();
		if (children != null)
		{
			for (int i = 0; i < children.length && i < totalSkinHome.length; i++)
			{
				if (children[i] != null)
				{
					totalSkinHome[i].restore(children[i]);
				}
			}
		}

		totalSkinHome = null;
	}

	private void applyRowSpacing()
	{
		if (rowHomeY == null && !captureRowHome())
		{
			return;
		}

		if (!tileIsNarrowed() || !config.removeBottomGap())
		{
			restoreRowSpacing();
			return;
		}

		// The tab is a fixed 190x261 panel: eight rows of tiles ending at y=243,
		// then the total level bar occupying y=241 to 260. Moving that bar up into
		// the Sailing slot frees its strip at the foot of the panel, and nothing
		// reflows to cover it. Share that space between the rows, but retain a
		// five-pixel stone footer matching the black inset's five-pixel top pad.
		// This also keeps the final row at its native 32-pixel height.
		int top = rowHomeY[0];
		int totalBottom = totalHome.y + totalHome.height;
		Widget panel = client.getWidget(InterfaceID.Stats.UNIVERSE);
		int panelHeight = panel == null ? totalBottom + 1 : panel.getOriginalHeight();
		int span = contentBottom(panelHeight, totalBottom) - top;
		if (span <= 0)
		{
			return;
		}

		for (int id = FIRST_TILE; id <= LAST_TILE; id++)
		{
			int row = rowOf(id);
			int y = top + (int) ((long) row * span / ROW_COUNT);
			int next = top + (int) ((long) (row + 1) * span / ROW_COUNT);
			setTileRow(id, y, next - y);
		}
	}

	static int contentBottom(int panelHeight, int totalBottom)
	{
		return Math.min(totalBottom, Math.max(0, panelHeight - BOTTOM_PANEL_PADDING));
	}

	private void restoreRowSpacing()
	{
		if (rowHomeY == null)
		{
			return;
		}

		for (int id = FIRST_TILE; id <= LAST_TILE; id++)
		{
			setTileRow(id, rowHomeY[rowOf(id)], rowHomeHeight[rowOf(id)]);
		}
	}

	private void setTileRow(int id, int y, int height)
	{
		Widget tile = client.getWidget(id);
		if (tile == null || (tile.getOriginalY() == y && tile.getOriginalHeight() == height))
		{
			return;
		}

		tile.setOriginalY(y);
		tile.setOriginalHeight(height);
		relayout(tile);
	}

	private boolean captureRowHome()
	{
		int[] y = new int[ROW_COUNT];
		int[] heights = new int[ROW_COUNT];
		for (int row = 0; row < ROW_COUNT; row++)
		{
			// The first column runs top to bottom, one tile per row. The bottom row
			// is two pixels taller than the rest, so heights are kept per row.
			Widget tile = client.getWidget(FIRST_TILE + row);
			if (tile == null)
			{
				return false;
			}

			y[row] = tile.getOriginalY();
			heights[row] = tile.getOriginalHeight();
		}

		rowHomeY = y;
		rowHomeHeight = heights;
		return true;
	}

	private static int rowOf(int id)
	{
		return (id - FIRST_TILE) % ROW_COUNT;
	}

	private void restoreTotalTilePosition(Widget total)
	{
		restoreTotalTileSkin(total);

		if (totalHome != null)
		{
			totalHome.applyTo(total);
			relayout(total);
			totalHome = null;
		}
	}

	/**
	 * The bar-child properties retained so it can be handed back as it was found.
	 */
	private static final class ChildState
	{
		private final int type;
		private final int spriteId;
		private final int textColor;
		private final int opacity;
		private final boolean filled;
		private final boolean hidden;
		private final Layout layout;

		private ChildState(Widget widget)
		{
			type = widget.getType();
			spriteId = widget.getSpriteId();
			textColor = widget.getTextColor();
			opacity = widget.getOpacity();
			filled = widget.isFilled();
			hidden = widget.isSelfHidden();
			layout = Layout.of(widget);
		}

		static ChildState of(Widget widget)
		{
			return widget == null ? null : new ChildState(widget);
		}

		void restore(Widget widget)
		{
			widget.setType(type);
			widget.setSpriteId(spriteId);
			widget.setTextColor(textColor);
			widget.setOpacity(opacity);
			widget.setFilled(filled);
			widget.setHidden(hidden);
			layout.applyTo(widget);
			widget.revalidate();
		}
	}

	private static final class TextStyle
	{
		private final int fontId;
		private final int lineHeight;
		private final int xTextAlignment;
		private final int yTextAlignment;
		private final boolean textShadowed;
		private final Layout layout;

		private TextStyle(Widget widget)
		{
			fontId = widget.getFontId();
			lineHeight = widget.getLineHeight();
			xTextAlignment = widget.getXTextAlignment();
			yTextAlignment = widget.getYTextAlignment();
			textShadowed = widget.getTextShadowed();
			layout = Layout.of(widget);
		}

		static TextStyle of(Widget widget)
		{
			return new TextStyle(widget);
		}

		void restore(Widget widget)
		{
			layout.applyTo(widget);
			widget.setFontId(fontId);
			widget.setLineHeight(lineHeight);
			widget.setXTextAlignment(xTextAlignment);
			widget.setYTextAlignment(yTextAlignment);
			widget.setTextShadowed(textShadowed);
			widget.revalidate();
		}
	}

	/**
	 * Revalidates a widget and its children. The background sprites and the label
	 * inside the total level tile are sized against their parent, so resizing the
	 * tile alone leaves them laid out for the width it used to have.
	 */
	private static void relayout(Widget widget)
	{
		widget.revalidate();

		Widget[] children = widget.getChildren();
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			if (child != null)
			{
				child.revalidate();
			}
		}
	}

	private void applyTotalLevel()
	{
		Widget totalText = client.getWidget(InterfaceID.Stats.TOTAL_TEXT6);
		if (totalText == null)
		{
			return;
		}

		String text = totalText.getText();
		String updated = replaceLastNumber(text, totalLevel());
		if (updated == null)
		{
			return;
		}

		if (tileIsNarrowed())
		{
			updated = stackLabelAboveNumber(updated);
			if (totalTextHomeStyle == null)
			{
				totalTextHomeStyle = TextStyle.of(totalText);
			}
			applyTotalTextStyle(totalText);
		}
		else
		{
			updated = placeLabelBesideNumber(updated);
			restoreTotalTextStyle(totalText);
		}

		if (!updated.equals(text))
		{
			totalText.setText(updated);
		}
	}

	private void applyTotalTextStyle(Widget totalText)
	{
		TextStyle home = totalTextHomeStyle;
		if (home == null)
		{
			return;
		}

		home.layout.applyTo(totalText);
		totalText.setFontId(FontID.PLAIN_11);
		totalText.setLineHeight(home.lineHeight);
		totalText.setXTextAlignment(home.xTextAlignment);
		totalText.setYTextAlignment(home.yTextAlignment);
		totalText.setTextShadowed(home.textShadowed);
		totalText.revalidate();
	}

	private boolean tileIsNarrowed()
	{
		return config.hideSailing() && config.moveTotalLevel();
	}

	private void applyTooltip()
	{
		Widget tooltip = client.getWidget(InterfaceID.Stats.TOOLTIP);
		if (tooltip == null || tooltip.isHidden())
		{
			return;
		}

		Widget[] children = tooltip.getDynamicChildren();
		if (children == null || children.length <= TOOLTIP_VALUE_CHILD_INDEX)
		{
			return;
		}

		Widget labels = children[TOOLTIP_LABEL_CHILD_INDEX];
		Widget values = children[TOOLTIP_VALUE_CHILD_INDEX];
		if (labels == null || values == null)
		{
			return;
		}

		String labelText = labels.getText();
		if (tileIsNarrowed() && isSailingTooltip(labelText))
		{
			// Sailing remains underneath Total so its genuine stone shell can render.
			// Its mouse listener therefore opens a Sailing tooltip first. Rebuilding
			// that tooltip through the same native script gives this replacement tile
			// the correctly sized, one-row Total XP box. PLAIN_12 matches the readable
			// native tooltip path instead of occasionally showing the tiny PLAIN_11 text.
			client.runScript(
				TOOLTIP_BUILD_SCRIPT,
				InterfaceID.Stats.SAILING,
				-1,
				InterfaceID.Stats.TOOLTIP,
				"Total XP:",
				String.format(Locale.ENGLISH, "%,d", totalExperience()),
				TOOLTIP_FONT_ID);
			return;
		}

		String updated = correctTooltipValues(
			labelText,
			values.getText(),
			totalLevel(),
			totalExperience());
		if (updated != null)
		{
			values.setText(updated);
		}
	}

	/**
	 * The total level to display. Falls back to the real total when the setting is
	 * off, which is what makes turning it off restore the tab straight away rather
	 * than leaving our last figure up until a script next redraws it.
	 */
	private int totalLevel()
	{
		return totalWithoutSailing(
			client.getTotalLevel(),
			client.getRealSkillLevel(Skill.SAILING),
			config.fixTotalLevel());
	}

	private long totalExperience()
	{
		return experienceWithoutSailing(
			client.getOverallExperience(),
			client.getSkillExperience(Skill.SAILING),
			config.fixTotalLevel());
	}

	static int totalWithoutSailing(int totalLevel, int sailingLevel, boolean enabled)
	{
		return enabled ? totalLevel - sailingLevel : totalLevel;
	}

	static long experienceWithoutSailing(long totalExperience, long sailingExperience, boolean enabled)
	{
		return enabled ? totalExperience - sailingExperience : totalExperience;
	}

	private void restore()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			Widget sailing = client.getWidget(InterfaceID.Stats.SAILING);
			restoreSailingContents();
			if (sailing != null && sailingHidden)
			{
				sailing.setHidden(false);
			}

			restoreRowSpacing();

			Widget total = client.getWidget(InterfaceID.Stats.TOTAL);
			if (total != null)
			{
				restoreTotalTilePosition(total);
			}

			Widget totalText = client.getWidget(InterfaceID.Stats.TOTAL_TEXT6);
			if (totalText != null)
			{
				restoreTotalTextStyle(totalText);

				String inlineText = placeLabelBesideNumber(totalText.getText());
				String updated = replaceLastNumber(inlineText, client.getTotalLevel());
				if (updated != null)
				{
					totalText.setText(updated);
				}
			}

			// The plugin has been unregistered before this callback runs, so these
			// native skill-change listeners can repaint the tab without our overrides.
			// This repairs any client-script state that was rebuilt while the plugin
			// was active and makes disabling it equivalent to reopening the interface.
			for (Skill skill : Skill.values())
			{
				client.queueChangedSkill(skill);
			}
		}

		forgetLayout();
	}

	private void forgetLayout()
	{
		totalHome = null;
		rowHomeY = null;
		rowHomeHeight = null;
		totalSkinHome = null;
		sailingContentHome.clear();
		totalTextHomeStyle = null;
		sailingHidden = false;
	}

	private void restoreTotalTextStyle(Widget totalText)
	{
		if (totalTextHomeStyle != null)
		{
			totalTextHomeStyle.restore(totalText);
			totalTextHomeStyle = null;
		}
	}

	/**
	 * Rewrites the last number in {@code text}, keeping the thousands separators
	 * the original number was written with. Returns null when there is no number
	 * to replace, which is how a tab that a script has not filled in yet reads.
	 */
	static String replaceLastNumber(String text, long value)
	{
		int[] bounds = lastNumberBounds(text);
		if (bounds == null)
		{
			return null;
		}

		int start = bounds[0];
		int end = bounds[1];
		String replacement = text.substring(start, end).indexOf(',') >= 0
			? String.format(Locale.ENGLISH, "%,d", value)
			: Long.toString(value);

		return text.substring(0, start) + replacement + text.substring(end);
	}

	/**
	 * Breaks {@code text} so the number sits on a line of its own. The full-width
	 * bar writes the label and the level side by side, which does not fit once the
	 * tile is one column wide. Text already split over two lines is left alone.
	 */
	static String stackLabelAboveNumber(String text)
	{
		int[] bounds = lastNumberBounds(text);
		if (bounds == null)
		{
			return text;
		}

		int start = bounds[0];
		int gap = start;
		while (gap > 0 && Character.isWhitespace(text.charAt(gap - 1)))
		{
			gap--;
		}

		if (gap == start || gap == 0)
		{
			// Either nothing separates the label from the number, or there is no
			// label at all. A <br> already ends in '>', so this covers that too.
			return text;
		}

		return text.substring(0, gap) + ROW_SEPARATOR + text.substring(start);
	}

	/**
	 * Returns the narrow tile's stacked label to the native full-width layout.
	 * Only the final break immediately before the displayed number is changed,
	 * so unrelated multi-line widget text and markup are left untouched.
	 */
	static String placeLabelBesideNumber(String text)
	{
		int[] bounds = lastNumberBounds(text);
		if (bounds == null)
		{
			return text;
		}

		int numberStart = bounds[0];
		int breakStart = text.lastIndexOf(ROW_SEPARATOR, numberStart);
		if (breakStart < 0)
		{
			return text;
		}

		int breakEnd = breakStart + ROW_SEPARATOR.length();
		if (!text.substring(breakEnd, numberStart).trim().isEmpty())
		{
			return text;
		}

		return text.substring(0, breakStart) + " " + text.substring(numberStart);
	}

	/**
	 * Locates the last number in {@code text} as {@code {start, end}}, or null when
	 * there is none. Digits inside markup do not count.
	 */
	private static int[] lastNumberBounds(String text)
	{
		if (text == null)
		{
			return null;
		}

		int start = -1;
		int end = -1;
		boolean inTag = false;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);

			// Skip over markup: a colour tag such as <col=ff981f> holds digits of
			// its own, and matching those would rewrite the tag instead.
			if (inTag)
			{
				inTag = c != '>';
				continue;
			}

			if (c == '<')
			{
				inTag = true;
				continue;
			}

			if (isDigit(c))
			{
				int from = i;
				while (i < text.length() && (isDigit(text.charAt(i)) || text.charAt(i) == ','))
				{
					i++;
				}

				// A comma that trails the digits belongs to the sentence, not the number.
				while (i > from && text.charAt(i - 1) == ',')
				{
					i--;
				}

				start = from;
				end = i;
				i--;
			}
		}

		return start < 0 ? null : new int[]{start, end};
	}

	private static boolean isDigit(char c)
	{
		return c >= '0' && c <= '9';
	}

	/**
	 * A widget's layout inputs. The modes have to travel with the values: they
	 * decide what the values mean, so a width of 62 against the bar's mode is not
	 * the 62 pixels a tile means by it, and an x of 0 can be an offset from the
	 * centre rather than from the left edge.
	 */
	private static final class Layout
	{
		private final int x;
		private final int y;
		private final int width;
		private final int height;
		private final int xPositionMode;
		private final int yPositionMode;
		private final int widthMode;
		private final int heightMode;

		private Layout(Widget widget)
		{
			x = widget.getOriginalX();
			y = widget.getOriginalY();
			width = widget.getOriginalWidth();
			height = widget.getOriginalHeight();
			xPositionMode = widget.getXPositionMode();
			yPositionMode = widget.getYPositionMode();
			widthMode = widget.getWidthMode();
			heightMode = widget.getHeightMode();
		}

		static Layout of(Widget widget)
		{
			return new Layout(widget);
		}

		boolean matches(Widget widget)
		{
			return x == widget.getOriginalX()
				&& y == widget.getOriginalY()
				&& width == widget.getOriginalWidth()
				&& height == widget.getOriginalHeight()
				&& xPositionMode == widget.getXPositionMode()
				&& yPositionMode == widget.getYPositionMode()
				&& widthMode == widget.getWidthMode()
				&& heightMode == widget.getHeightMode();
		}

		void applyTo(Widget widget)
		{
			widget.setXPositionMode(xPositionMode);
			widget.setYPositionMode(yPositionMode);
			widget.setWidthMode(widthMode);
			widget.setHeightMode(heightMode);
			widget.setOriginalX(x);
			widget.setOriginalY(y);
			widget.setOriginalWidth(width);
			widget.setOriginalHeight(height);
		}
	}

	/**
	 * The tooltip holds one widget of {@code <br>}-joined labels beside one widget
	 * of {@code <br>}-joined values. Returns the rewritten values, or null when
	 * nothing about this tooltip needs changing.
	 */
	static String correctTooltipValues(String labels, String values, int totalLevel, long totalExperience)
	{
		if (labels == null || values == null)
		{
			return null;
		}

		String[] labelRows = labels.split(ROW_SEPARATOR, -1);
		String[] valueRows = values.split(ROW_SEPARATOR, -1);
		if (labelRows.length != valueRows.length)
		{
			return null;
		}

		boolean changed = false;
		for (int i = 0; i < labelRows.length; i++)
		{
			String label = Text.removeTags(labelRows[i]).trim().toLowerCase(Locale.ENGLISH);

			String updated;
			if (label.startsWith(TOTAL_LEVEL_LABEL))
			{
				updated = replaceLastNumber(valueRows[i], totalLevel);
			}
			else if (label.startsWith(TOTAL_XP_LABEL))
			{
				updated = replaceLastNumber(valueRows[i], totalExperience);
			}
			else
			{
				continue;
			}

			if (updated != null && !updated.equals(valueRows[i]))
			{
				valueRows[i] = updated;
				changed = true;
			}
		}

		return changed ? String.join(ROW_SEPARATOR, valueRows) : null;
	}

	static boolean isSailingTooltip(String labels)
	{
		if (labels == null)
		{
			return false;
		}

		int rowEnd = labels.indexOf(ROW_SEPARATOR);
		String firstRow = rowEnd < 0 ? labels : labels.substring(0, rowEnd);
		String normalized = Text.removeTags(firstRow).trim().toLowerCase(Locale.ENGLISH);
		return normalized.equals(SAILING_LABEL)
			|| normalized.startsWith(SAILING_LABEL + ":")
			|| normalized.equals(SAILING_XP_LABEL)
			|| normalized.startsWith(SAILING_XP_LABEL + ":");
	}
}
