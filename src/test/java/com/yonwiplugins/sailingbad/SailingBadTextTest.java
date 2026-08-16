package com.yonwiplugins.sailingbad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SailingBadTextTest
{
	@Test
	public void usesTheMeasuredOriginalTotalTileMask()
	{
		assertArrayEquals(new int[]{2, 5, 58, 22, 0}, SailingBadPlugin.panelLayer(0, 62, 32));
		assertArrayEquals(new int[]{4, 4, 54, 1, 0}, SailingBadPlugin.panelLayer(1, 62, 32));
		assertArrayEquals(new int[]{5, 2, 52, 2, 0}, SailingBadPlugin.panelLayer(2, 62, 32));
		assertArrayEquals(new int[]{4, 27, 54, 1, 0}, SailingBadPlugin.panelLayer(3, 62, 32));
		assertArrayEquals(new int[]{5, 28, 52, 2, 0}, SailingBadPlugin.panelLayer(4, 62, 32));
	}

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
	public void stacksTheLevelUnderTheLabelForANarrowTile()
	{
		assertEquals(
			"Total level:<br>2277",
			SailingBadPlugin.stackLabelAboveNumber("Total level: 2277"));
	}

	@Test
	public void stackingKeepsMarkupIntact()
	{
		assertEquals(
			"<col=ff981f>Total level:</col><br>2277",
			SailingBadPlugin.stackLabelAboveNumber("<col=ff981f>Total level:</col> 2277"));
	}

	@Test
	public void doesNotStackTextThatIsAlreadySplit()
	{
		assertEquals(
			"Total level:<br>2277",
			SailingBadPlugin.stackLabelAboveNumber("Total level:<br>2277"));
	}

	@Test
	public void stackingIsIdempotent()
	{
		String once = SailingBadPlugin.stackLabelAboveNumber("Total level: 2277");
		assertEquals(once, SailingBadPlugin.stackLabelAboveNumber(once));
	}

	@Test
	public void leavesTextWithNoLabelAlone()
	{
		assertEquals("2277", SailingBadPlugin.stackLabelAboveNumber("2277"));
		assertEquals("Total level:", SailingBadPlugin.stackLabelAboveNumber("Total level:"));
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
	public void leavesTheFreeTotalLevelRowAlone()
	{
		// The total level tooltip is built by script 396 from "Total level:",
		// "Total XP:" and "Free Total Level:". The free total counts only
		// free-to-play skills, so Sailing was never in it to take out.
		assertEquals(
			"2277<br>299,791,913<br>1500",
			SailingBadPlugin.correctTooltipValues(
				"Total level:<br>Total XP:<br>Free Total Level:",
				"2376<br>313,791,913<br>1500",
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
