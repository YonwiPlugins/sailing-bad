package com.yonwiplugins.sailingbad;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import okhttp3.HttpUrl;

public class SailingBadPanel extends PluginPanel
{
	static final String HISCORES_HOST = "2277.telfardo.com";

	private final Client client;

	@Inject
	public SailingBadPanel(Client client)
	{
		this.client = client;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Sailing Bad Hiscores", SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		content.add(title, BorderLayout.NORTH);

		JLabel help = new JLabel(
			"<html><div style='text-align:center'>Join the 2277 leaderboard.<br>"
				+ "Your current character name will be filled in.</div></html>",
			SwingConstants.CENTER);
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(help, BorderLayout.CENTER);

		JButton join = new JButton("Add me to the hiscores");
		join.addActionListener(event -> LinkBrowser.browse(buildHiscoresUrl(currentPlayerName())));
		content.add(join, BorderLayout.SOUTH);

		add(content, BorderLayout.NORTH);
	}

	private String currentPlayerName()
	{
		Player localPlayer = client.getLocalPlayer();
		return localPlayer == null ? null : localPlayer.getName();
	}

	static String buildHiscoresUrl(String playerName)
	{
		HttpUrl.Builder url = new HttpUrl.Builder()
			.scheme("https")
			.host(HISCORES_HOST)
			.addPathSegment("join");
		if (playerName != null && !playerName.trim().isEmpty())
		{
			url.addQueryParameter("username", playerName);
		}

		return url.build().toString();
	}
}
