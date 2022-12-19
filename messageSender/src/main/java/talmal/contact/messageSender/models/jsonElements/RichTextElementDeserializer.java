package talmal.contact.messageSender.models.jsonElements;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.slack.api.model.block.element.RichTextElement;
import com.slack.api.model.block.element.RichTextSectionElement.Text;

import lombok.extern.slf4j.Slf4j;
import talmal.contact.messageSender.config.SlackGson;

@Slf4j
public class RichTextElementDeserializer implements JsonDeserializer<RichTextElement>
{
	@Override
	public RichTextElement deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
	{
		RichTextElement result = null;
		JsonObject jsonObject = json.getAsJsonObject();
		JsonElement blockElementType = jsonObject.get("type");

		if (blockElementType != null)
		{
			switch (blockElementType.toString())
			{
				case "\"text\"":
				{
					result = SlackGson.fromJson(json, Text.class);
					break;
				}
				default:
				{
					log.error("Not Implamented yet, add RichTextElement of type: {}", blockElementType.toString());
					break;
				}
			}
		}

		return result;
	}
}
