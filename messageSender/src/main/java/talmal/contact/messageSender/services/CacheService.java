package talmal.contact.messageSender.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.openjdk.jol.info.GraphLayout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import talmal.contact.messageSender.models.context.ChatMessage;

@Component(value = "inMemory")
public class CacheService implements CacheInterface
{
	private Map<String, List<ChatMessage>> openChats;

	@Value(value = "${services.cache.maxMemory:10}")
	private int cacheMaxMemoryInMb;

	@Value(value = "${services.cache.isActive:true}")
	private boolean isActive;

	public CacheService()
	{
		this.init();
	}
	
	public CacheService(boolean isActive, int cacheMaxMemoryInMb)
	{
		this.isActive = isActive;
		this.cacheMaxMemoryInMb = cacheMaxMemoryInMb;
		
		if (isActive)
		{
			this.init();
		}
	}
	
	public void init()
	{
		this.openChats = new HashMap<String, List<ChatMessage>>();
	}

	@Override
	public List<ChatMessage> getMessages(String chatId)
	{
		List<ChatMessage> result = null;
		if (this.isActive && chatId != null)
		{
			result = this.openChats.get(chatId);
		}

		return result;
	}

	@Override
	public boolean isChatExists(String chatId)
	{
		boolean result = false;
		if (this.isActive && chatId != null)
		{
			result = this.openChats.containsKey(chatId);
		}

		return result;
	}

	@Override
	public Set<String> getChatIds()
	{
		Set<String> result = null;
		if (this.isActive)
		{
			result = this.openChats.keySet();
		}

		return result;
	}

	@Override
	public boolean addMessage(ChatMessage message)
	{
		boolean result = false;
		if (this.isActive && message != null && message.getChatId() != null)
		{
			String chatId = message.getChatId();
			List<ChatMessage> existingMessages = this.openChats.get(chatId);
			if (existingMessages != null)
			{
				// chat id exists in memory
				existingMessages.add(message);
			}
			else
			{
				// chat id does not exist in memory
				List<ChatMessage> newMessages = new ArrayList<ChatMessage>();
				newMessages.add(message);
				this.openChats.put(chatId, newMessages);
			}

			result = true;
		}

		return result;
	}

	@Override
	public boolean addMessages(List<ChatMessage> messages)
	{
		boolean result = false;

		if (this.isActive && messages != null && !messages.isEmpty())
		{
			// add first message to initialize cache entry
			messages.stream().forEach(message -> 
			{
				List<ChatMessage> existingMessages = this.openChats.get(message.getChatId());
				if (existingMessages != null)
				{
					// add messages to existing entry
					existingMessages.add(message);
				}
				else
				{
					// create a new entry
					existingMessages = new ArrayList<ChatMessage>();
					existingMessages.add(message);
					this.openChats.put(message.getChatId(), existingMessages);
				}
	
			});
			
			result = true;
		}

		return result;
	}

	@Override
	public void removeMessages(String chatId)
	{
		if (this.isActive && chatId != null)
		{
			List<ChatMessage> existingMessages = this.openChats.remove(chatId);
			if (existingMessages != null)
			{
				existingMessages.clear();
			}
		}
	}

	@Override
	public String removeOldest()
	{
		String result = null;

		if (this.isActive)
		{
			// sort by keys (keys are assumed to be timestemps), and remove first which is
			// the oldest entry
			Optional<String> oldestChat = this.openChats.keySet().stream().sorted().findFirst();
			if (oldestChat.isPresent())
			{
				result = oldestChat.get();
				this.openChats.remove(result);
			}
		}

		return result;
	}

	@Override
	public boolean isMemoryLimitReached()
	{
		boolean result = false;
		if (this.isActive)
		{
			// TODO: fix memory calculator (WARNING: Unable to attach Serviceability Agent. sun.jvm.hotspot.memory.Universe.getNarrowOopBase())
			long sizeInBytes = GraphLayout.parseInstance(this.openChats).totalSize();
			//log.info("isMemoryLimitReached with size of {} bytes ", sizeInBytes);
			result = this.cacheMaxMemoryInMb <= (sizeInBytes / 1000000);
		}
		return result;
	}
	
	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		
		if(this.isActive)
		{
			for(String chatId: this.getChatIds())
			{
				builder.append(chatId);
				builder.append(System.lineSeparator());
				for(ChatMessage message : this.getMessages(chatId))
				{
					builder.append(message);
					builder.append(System.lineSeparator());
				}
				
				builder.append(System.lineSeparator());
				builder.append(System.lineSeparator());
			}
		}
		
		return builder.toString();
	}

	@Override
	public void close()
	{
		// nothing to close here
	}
}
