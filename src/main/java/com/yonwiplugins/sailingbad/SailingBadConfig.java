package com.yonwiplugins.sailingbad;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SailingBadConfig.GROUP)
public interface SailingBadConfig extends Config
{
	String GROUP = "sailing-bad";

	@ConfigItem(
		keyName = "hideSailing",
		name = "Hide Sailing",
		description = "Removes the Sailing tile from the skills tab",
		position = 0
	)
	default boolean hideSailing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "moveTotalLevel",
		name = "Close the gap",
		description = "Moves the total level tile into the slot Sailing leaves behind",
		position = 1
	)
	default boolean moveTotalLevel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fixTotalLevel",
		name = "Total level without Sailing",
		description = "Takes Sailing back out of the total level and total XP that the skills tab shows",
		position = 2
	)
	default boolean fixTotalLevel()
	{
		return true;
	}
}
