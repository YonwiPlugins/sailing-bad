package com.yonwiplugins.sailingbad;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SailingBadPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SailingBadPlugin.class);
		RuneLite.main(args);
	}
}
