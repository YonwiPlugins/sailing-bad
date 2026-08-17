package com.yonwiplugins.sailingbad;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.IOException;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SailingBadPanel extends PluginPanel
{
	static final String HISCORES_HOST = "2277.telfardo.com";
	static final String HISCORES_API_URL = "https://2277.telfardo.com/api/hiscores";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final Client client;
	private final SailingBadConfig config;
	private final OkHttpClient httpClient;
	private final JButton join = new JButton("Add me to the HiScores");
	private final JLabel status = new JLabel("Enable HiScores opt-in in the plugin settings first.", SwingConstants.CENTER);

	@Inject
	public SailingBadPanel(Client client, SailingBadConfig config, OkHttpClient httpClient)
	{
		this.client = client;
		this.config = config;
		this.httpClient = httpClient;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Sailing Bad HiScores", SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		content.add(title, BorderLayout.NORTH);

		JLabel help = new JLabel(
			"<html><div style='text-align:center'>Join the 2277 leaderboard.<br>"
				+ "Your current character name is only sent when you click below.</div></html>",
			SwingConstants.CENTER);
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(help, BorderLayout.CENTER);

		join.addActionListener(event -> submitCurrentPlayer());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel controls = new JPanel(new BorderLayout(0, 6));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.add(join, BorderLayout.NORTH);
		controls.add(status, BorderLayout.SOUTH);
		content.add(controls, BorderLayout.SOUTH);

		add(content, BorderLayout.NORTH);
	}

	private void submitCurrentPlayer()
	{
		if (!config.enableHiscoresOptIn())
		{
			setStatus("Enable HiScores opt-in in the plugin settings first.");
			return;
		}

		String playerName = normalizePlayerName(currentPlayerName());
		if (!isValidPlayerName(playerName))
		{
			setStatus("Log in to a character before joining the HiScores.");
			return;
		}

		join.setEnabled(false);
		setStatus("Adding " + playerName + "...");

		httpClient.newCall(buildOptInRequest(playerName)).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				finish("The HiScores service could not be reached.", null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (response.isSuccessful())
					{
						finish(playerName + " was added.", buildResultUrl(playerName));
					}
					else
					{
						finish("Could not join the HiScores (HTTP " + response.code() + ").", null);
					}
				}
			}
		});
	}

	private void finish(String message, String resultUrl)
	{
		SwingUtilities.invokeLater(() ->
		{
			join.setEnabled(true);
			setStatus(message);
			if (resultUrl != null)
			{
				LinkBrowser.browse(resultUrl);
			}
		});
	}

	private void setStatus(String message)
	{
		status.setText("<html><div style='text-align:center'>" + message + "</div></html>");
	}

	private String currentPlayerName()
	{
		Player localPlayer = client.getLocalPlayer();
		return localPlayer == null ? null : localPlayer.getName();
	}

	static Request buildOptInRequest(String playerName)
	{
		return new Request.Builder()
			.url(HISCORES_API_URL)
			.post(RequestBody.create(JSON, buildOptInBody(playerName)))
			.build();
	}

	static String buildOptInBody(String playerName)
	{
		return "{\"player\":\"" + playerName + "\"}";
	}

	static String buildResultUrl(String playerName)
	{
		return new HttpUrl.Builder()
			.scheme("https")
			.host(HISCORES_HOST)
			.addQueryParameter("player", playerName)
			.fragment("player-result")
			.build()
			.toString();
	}

	static boolean isValidPlayerName(String playerName)
	{
		return playerName != null
			&& playerName.length() >= 1
			&& playerName.length() <= 12
			&& playerName.matches("[A-Za-z0-9 -]+");
	}

	static String normalizePlayerName(String playerName)
	{
		return playerName == null ? null : playerName.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
	}
}
