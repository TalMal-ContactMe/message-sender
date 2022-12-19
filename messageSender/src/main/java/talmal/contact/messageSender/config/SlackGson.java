package talmal.contact.messageSender.config;

import java.lang.reflect.Type;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.RichTextElement;

import talmal.contact.messageSender.models.jsonElements.BlockElementDeserializer;
import talmal.contact.messageSender.models.jsonElements.InstantDeserializer;
import talmal.contact.messageSender.models.jsonElements.InstantSerializer;
import talmal.contact.messageSender.models.jsonElements.MessageBlockDeserializer;
import talmal.contact.messageSender.models.jsonElements.RichTextElementDeserializer;

public class SlackGson
{
	private static Gson GSON;
	
	static
	{
		// Instant needs a custom json serializer, deSerializer
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Instant.class, new InstantSerializer());
        gsonBuilder.registerTypeAdapter(Instant.class, new InstantDeserializer());
        gsonBuilder.registerTypeAdapter(LayoutBlock.class, new MessageBlockDeserializer());
        
        gsonBuilder.registerTypeAdapter(BlockElement.class,new BlockElementDeserializer());
        gsonBuilder.registerTypeAdapter(RichTextElement.class,new RichTextElementDeserializer());
        
        SlackGson.GSON = gsonBuilder.setPrettyPrinting().create();
	}
	
	public static String toJson(Object object)
	{
		return SlackGson.GSON.toJson(object);
	}
	
	public static String toJson(Object object, Type typeOfSrc)
	{
		return SlackGson.GSON.toJson(object);
	}
	
	public static <T> T fromJson(String json, Class<T> classOfT)
	{
		return SlackGson.GSON.fromJson(json, classOfT);
	}
	
	public static <T> T fromJson(JsonElement json, Class<T> classOfT)
	{
		return SlackGson.GSON.fromJson(json, classOfT);
	}
}
