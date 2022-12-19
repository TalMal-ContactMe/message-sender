package talmal.contact.messageSender.models.jsonElements;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.RichTextSectionElement;

import lombok.extern.slf4j.Slf4j;
import talmal.contact.messageSender.config.SlackGson;

@Slf4j
public class BlockElementDeserializer implements JsonDeserializer<BlockElement>
{
	@Override
	public BlockElement deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
	{
		BlockElement result = null;
		JsonObject jsonObject = json.getAsJsonObject();
		JsonElement blockElementType = jsonObject.get("type");

		if (blockElementType != null)
		{
			switch (blockElementType.toString())
			{
				case "\"rich_text_section\"":
				{
					result = SlackGson.fromJson(json, RichTextSectionElement.class);
					break;
				}
				default:
				{
					log.error("Not Implamented yet, add BlockElement of type: {}", blockElementType.toString());
					break;
				}
			}
		}

		return result;
	}
}
