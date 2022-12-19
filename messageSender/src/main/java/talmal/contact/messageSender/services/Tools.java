package talmal.contact.messageSender.services;

import java.time.Instant;

public class Tools
{
	/**
	 * parse slack timestamp into java Instant date
	 * 
	 * @param ts - slack message.ts
	 * @return slack message.ts value to timestamp, or null on invalid input ts
	 */
	public static Instant slackTsToInstant(String ts)
	{
		Instant result = null;
		if (ts != null)
		{
			String[] tsParts = ts.split("\\.");
			if (tsParts.length == 2)
			{
				result = Instant.ofEpochSecond(Long.parseLong(tsParts[0]));
			}
		}

		return result;
	}
}
