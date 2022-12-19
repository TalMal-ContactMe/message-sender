package talmal.contact.messageSender.models.jsonElements;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.RichTextBlock;

import lombok.extern.slf4j.Slf4j;
import talmal.contact.messageSender.config.SlackGson;

@Slf4j
public class MessageBlockDeserializer implements JsonDeserializer<LayoutBlock>
{
	@Override
	public LayoutBlock deserialize(JsonElement json, Type typeOf, JsonDeserializationContext context) throws JsonParseException
	{
		LayoutBlock result = null;
		JsonObject jsonObject = json.getAsJsonObject();
		JsonElement blockType = jsonObject.get("type");

		if (blockType != null)
		{
			switch (blockType.toString())
			{
				case "\"rich_text\"":
				{
					result = SlackGson.fromJson(json, RichTextBlock.class);
					break;
				}
				default:
				{
					log.error("Not Implamented yet, add LayoutBlock of type: {}", blockType.toString());
					break;
				}
			}
		}

		return result;
	}
}
