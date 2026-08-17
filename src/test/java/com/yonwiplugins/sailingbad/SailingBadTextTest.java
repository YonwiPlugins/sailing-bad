package com.yonwiplugins.sailingbad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.FontID;
import org.junit.Test;

public class SailingBadTextTest
{
	@Test
	public void keepsFivePixelsOfStoneBelowTheFinalRow()
	{
		int contentBottom = SailingBadPlugin.contentBottom(261, 260);
		assertEquals(256, contentBottom);
		assertEquals(5, 261 - contentBottom);
	}

	@Test
	public void keepsTheBlackInsetInsideTheOriginalBottomRowSkin()
	{
		assertEquals(32, SailingBadPlugin.skinHeight(33, 32));
		assertEquals(32, SailingBadPlugin.skinHeight(32, 32));
		assertEquals(30, SailingBadPlugin.skinHeight(30, 32));
	}

	@Test
	public void usesTheApprovedFixedTotalTileMask()
	{
		assertArrayEquals(new int[]{2, 6, 58, 20, 0}, SailingBadPlugin.panelLayer(0, 62, 32));
		assertArrayEquals(new int[]{4, 5, 54, 1, 0}, SailingBadPlugin.panelLayer(1, 62, 32));
		assertArrayEquals(new int[]{5, 3, 52, 2, 0}, SailingBadPlugin.panelLayer(2, 62, 32));
		assertArrayEquals(new int[]{4, 26, 54, 1, 0}, SailingBadPlugin.panelLayer(3, 62, 32));
		assertArrayEquals(new int[]{5, 27, 52, 2, 0}, SailingBadPlugin.panelLayer(4, 62, 32));
	}

	@Test
	public void usesTheReadableRuneScapeFontForTheReplacementTooltip()
	{
		assertEquals(FontID.PLAIN_12, SailingBadPlugin.TOOLTIP_FONT_ID);
	}

	@Test
	public void buildsTheHiscoresJoinUrlWithTheCurrentCharacter()
	{
		assertEquals(
			"https://2277.telfardo.com/join?username=Yonwi%20OSRS",
			SailingBadPanel.buildHiscoresUrl("Yonwi OSRS"));
	}

	@Test
	public void buildsAPlainJoinUrlWhenLoggedOut()
	{
		assertEquals("https://2277.telfardo.com/join", SailingBadPanel.buildHiscoresUrl(null));
	}

	@Test
	public void replacesTheTotalLevelOnTheTile()
	{
		assertEquals(
			"Total level:<br>2277",
			SailingBadPlugin.replaceLastNumber("Total level:<br>2376", 2277));
	}

	@Test
	public void subtractsSailingFromTheDisplayedTotalLevel()
	{
		assertEquals(2277, SailingBadPlugin.totalWithoutSailing(2376, 99, true));
		assertEquals(2376, SailingBadPlugin.totalWithoutSailing(2376, 99, false));
	}

	@Test
	public void subtractsSailingFromTheDisplayedTotalExperience()
	{
		assertEquals(
			299_791_913L,
			SailingBadPlugin.experienceWithoutSailing(313_791_913L, 14_000_000L, true));
		assertEquals(
			313_791_913L,
			SailingBadPlugin.experienceWithoutSailing(313_791_913L, 14_000_000L, false));
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
	public void restoresTheNativeSingleLineTextWhenDisabled()
	{
		assertEquals(
			"Total level: 2376",
			SailingBadPlugin.placeLabelBesideNumber("Total level:<br>2376"));
	}

	@Test
	public void restoringSingleLineTextKeepsMarkupIntact()
	{
		assertEquals(
			"<col=ff981f>Total level:</col> 2376",
			SailingBadPlugin.placeLabelBesideNumber(
				"<col=ff981f>Total level:</col><br>2376"));
	}

	@Test
	public void restoringSingleLineTextIsIdempotent()
	{
		assertEquals(
			"Total level: 2376",
			SailingBadPlugin.placeLabelBesideNumber("Total level: 2376"));
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
	public void identifiesOnlyTheSailingExperienceTooltip()
	{
		assertTrue(SailingBadPlugin.isSailingExperienceTooltip(
			"<col=ff981f>Sailing XP:</col><br>Next level at:<br>Remaining XP:"));
		assertFalse(SailingBadPlugin.isSailingExperienceTooltip("Total XP:"));
		assertFalse(SailingBadPlugin.isSailingExperienceTooltip("Sailing:<br>XP:"));
		assertFalse(SailingBadPlugin.isSailingExperienceTooltip(null));
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
