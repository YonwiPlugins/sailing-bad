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

	// Where the total level tile sits before we slide it into the Sailing slot.
	private int totalHomeX = -1;
	private int totalHomeY = -1;
	private boolean totalMoved;
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

		// A client script rebuilds the skills tab whenever a level changes or the
		// tab is reopened, so every override has to be reapplied each frame. Each
		// one writes an absolute value rather than a delta so that reapplying an
		// override that is already in place is a no-op.
		applySkillTile();
		applyTotalLevel();
		applyTooltip();
	}

	private void applySkillTile()
	{
		Widget sailing = client.getWidget(InterfaceID.Stats.SAILING);
		if (sailing == null)
		{
			return;
		}

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

		applyTotalTilePosition(sailing);
	}

	private void applyTotalTilePosition(Widget sailing)
	{
		Widget total = client.getWidget(InterfaceID.Stats.TOTAL);
		if (total == null)
		{
			return;
		}

		if (!config.hideSailing() || !config.moveTotalLevel())
		{
			if (totalMoved)
			{
				restoreTotalTilePosition(total);
			}

			return;
		}

		if (!totalMoved)
		{
			totalHomeX = total.getOriginalX();
			totalHomeY = total.getOriginalY();
			totalMoved = true;
		}

		// Sailing pushes the total level tile onto a row of its own. Dropping it
		// into the gap Sailing leaves behind closes the grid back up to 8 rows.
		if (total.getOriginalX() != sailing.getOriginalX()
			|| total.getOriginalY() != sailing.getOriginalY())
		{
			total.setOriginalX(sailing.getOriginalX());
			total.setOriginalY(sailing.getOriginalY());
			total.revalidate();
		}
	}

	private void restoreTotalTilePosition(Widget total)
	{
		if (totalHomeX >= 0)
		{
			total.setOriginalX(totalHomeX);
			total.setOriginalY(totalHomeY);
			total.revalidate();
		}

		totalMoved = false;
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
		if (updated != null && !updated.equals(text))
		{
			totalText.setText(updated);
		}
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

			Widget total = client.getWidget(InterfaceID.Stats.TOTAL);
			if (total != null && totalMoved)
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
		totalHomeX = -1;
		totalHomeY = -1;
		totalMoved = false;
		sailingHidden = false;
	}

	/**
	 * Rewrites the last number in {@code text}, keeping the thousands separators
	 * the original number was written with. Returns null when there is no number
	 * to replace, which is how a tab that a script has not filled in yet reads.
	 */
	static String replaceLastNumber(String text, long value)
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

			if (c >= '0' && c <= '9')
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

		if (start < 0)
		{
			return null;
		}

		String replacement = text.substring(start, end).indexOf(',') >= 0
			? String.format(Locale.ENGLISH, "%,d", value)
			: Long.toString(value);

		return text.substring(0, start) + replacement + text.substring(end);
	}

	private static boolean isDigit(char c)
	{
		return c >= '0' && c <= '9';
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
