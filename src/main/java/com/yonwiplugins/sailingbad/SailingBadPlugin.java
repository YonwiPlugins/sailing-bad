package com.yonwiplugins.sailingbad;

import com.google.inject.Provides;
import java.util.Locale;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
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
	private static final int PANEL_COLOUR = 0x000000;
	private static final String TOTAL_LEVEL_LABEL = "total level";
	private static final String TOTAL_XP_LABEL = "total xp";
	private static final int TOOLTIP_LABEL_CHILD_INDEX = 2;
	private static final int TOOLTIP_VALUE_CHILD_INDEX = 3;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SailingBadConfig config;

	// The bar the total level sits in before we shrink it into the Sailing slot.
	private Layout totalHome;
	// The y and height of each row as Sailing leaves them.
	private int[] rowHomeY;
	private int[] rowHomeHeight;
	// The total level bar's own children, before we repaint them as a tile.
	private ChildState[] totalSkinHome;
	private boolean sailingHidden;

	@Provides
	SailingBadConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SailingBadConfig.class);
	}

	@Override
	protected void startUp()
	{
		forgetLayout();
	}

	@Override
	protected void shutDown()
	{
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
			if (!sailing.isSelfHidden())
			{
				sailing.setHidden(true);
				sailingHidden = true;
			}
		}
		else if (sailingHidden)
		{
			sailing.setHidden(false);
			sailingHidden = false;
		}
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
	 * Repaints the total level bar's background as a tile-shaped panel. The bar is
	 * drawn as a three part pill: a rounded left cap, a repeating middle and a
	 * rounded right cap, all 36x19 sprites. Those are the wrong shape for a 62x32
	 * tile and the caps would overlap in it, so the first of them becomes a plain
	 * filled panel covering the tile and the rest are hidden.
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

		boolean painted = false;
		for (Widget child : children)
		{
			if (child == null || child.getType() == TYPE_TEXT)
			{
				continue;
			}

			if (painted)
			{
				if (!child.isSelfHidden())
				{
					child.setHidden(true);
				}

				continue;
			}

			paintPanel(child, total);
			painted = true;
		}
	}

	private static void paintPanel(Widget panel, Widget tile)
	{
		int width = tile.getOriginalWidth();
		int height = tile.getOriginalHeight();
		if (panel.getType() == TYPE_RECTANGLE
			&& panel.getOriginalWidth() == width
			&& panel.getOriginalHeight() == height)
		{
			return;
		}

		panel.setType(TYPE_RECTANGLE);
		panel.setFilled(true);
		panel.setSpriteId(-1);
		panel.setTextColor(PANEL_COLOUR);
		panel.setOpacity(0);
		panel.setHidden(false);
		panel.setXPositionMode(0);
		panel.setYPositionMode(0);
		panel.setWidthMode(0);
		panel.setHeightMode(0);
		panel.setOriginalX(0);
		panel.setOriginalY(0);
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
		// reflows to cover it. Sharing the whole span between the eight remaining
		// rows spreads that strip across them instead of leaving it dead.
		int top = rowHomeY[0];
		int span = totalHome.y + totalHome.height - top;
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
	 * The properties {@link #paintPanel} overwrites, so the bar can be handed back
	 * as it was found.
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
		}

		if (!updated.equals(text))
		{
			totalText.setText(updated);
		}
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

		String updated = correctTooltipValues(
			labels.getText(),
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
		int total = client.getTotalLevel();
		return config.fixTotalLevel()
			? total - client.getRealSkillLevel(Skill.SAILING)
			: total;
	}

	private long totalExperience()
	{
		long total = client.getOverallExperience();
		return config.fixTotalLevel()
			? total - client.getSkillExperience(Skill.SAILING)
			: total;
	}

	private void restore()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			Widget sailing = client.getWidget(InterfaceID.Stats.SAILING);
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
				String updated = replaceLastNumber(totalText.getText(), client.getTotalLevel());
				if (updated != null)
				{
					totalText.setText(updated);
				}
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
		sailingHidden = false;
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
}
