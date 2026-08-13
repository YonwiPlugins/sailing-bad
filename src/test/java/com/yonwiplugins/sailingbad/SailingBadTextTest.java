package com.yonwiplugins.sailingbad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SailingBadTextTest
{
	@Test
	public void replacesTheTotalLevelOnTheTile()
	{
		assertEquals(
			"Total level:<br>2277",
			SailingBadPlugin.replaceLastNumber("Total level:<br>2376", 2277));
	}

	@Test
	public void reapplyingTheSameTotalChangesNothing()
	{
		String once = SailingBadPlugin.replaceLastNumber("Total level:<br>2376", 2277);
		assertEquals(once, SailingBadPlugin.replaceLastNumber(once, 2277));
	}

	@Test
	public void keepsThousandsSeparatorsWhenTheOriginalHadThem()
	{
		assertEquals("1,234,567", SailingBadPlugin.replaceLastNumber("2,345,678", 1234567));
		assertEquals("1234567", SailingBadPlugin.replaceLastNumber("2345678", 1234567));
	}

	@Test
	public void ignoresNumbersThatAreNotTheValue()
	{
		assertEquals("<col=ff981f>Total level:</col><br>2277",
			SailingBadPlugin.replaceLastNumber("<col=ff981f>Total level:</col><br>2376", 2277));
	}

	@Test
	public void doesNotRewriteDigitsInsideColourTags()
	{
		assertEquals(
			"<col=ff981f>Total level:</col><br><col=ffffff>2277</col>",
			SailingBadPlugin.replaceLastNumber(
				"<col=ff981f>Total level:</col><br><col=ffffff>2376</col>", 2277));
	}

	@Test
	public void returnsNullWhenTheOnlyDigitsAreMarkup()
	{
		assertNull(SailingBadPlugin.replaceLastNumber("<col=ff981f>Total level:</col>", 2277));
	}

	@Test
	public void returnsNullWhenThereIsNoNumberYet()
	{
		assertNull(SailingBadPlugin.replaceLastNumber("Total level:", 2277));
		assertNull(SailingBadPlugin.replaceLastNumber(null, 2277));
	}

	@Test
	public void correctsBothTooltipRows()
	{
		assertEquals(
			"2277<br>299,791,913",
			SailingBadPlugin.correctTooltipValues(
				"Total level:<br>Total XP:",
				"2376<br>313,791,913",
				2277,
				299_791_913L));
	}

	@Test
	public void leavesTooltipsForOtherSkillsAlone()
	{
		assertNull(SailingBadPlugin.correctTooltipValues(
			"Attack:<br>XP:",
			"99<br>13,034,431",
			2277,
			299_791_913L));
	}

	@Test
	public void bailsOutWhenLabelsAndValuesDoNotLineUp()
	{
		assertNull(SailingBadPlugin.correctTooltipValues(
			"Total level:<br>Total XP:",
			"2376",
			2277,
			299_791_913L));
	}

	@Test
	public void reportsNoChangeWhenTheTooltipIsAlreadyCorrect()
	{
		assertNull(SailingBadPlugin.correctTooltipValues(
			"Total level:<br>Total XP:",
			"2277<br>299,791,913",
			2277,
			299_791_913L));
	}
}
