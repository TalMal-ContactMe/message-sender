package talmal.contact.messageSender.services;

import java.util.List;
import java.util.Set;

import talmal.contact.messageSender.models.context.ChatMessage;

public interface CacheInterface
{
	/**
	 * get messages for chat id
	 * @param chatId
	 * @return existing message list or null
	 */
	public List<ChatMessage> getMessages(String chatId);
	
	/**
	 * add message to message list according to chat id
	 * @param message
	 * @return true if message was added, false if not
	 */
	public boolean addMessage(ChatMessage message);
	
	/**
	 * add messages to message list according to chat id
	 * @param messages
	 * @return true if message was added, false if not
	 */
	public boolean addMessages(List<ChatMessage> messages);
	
	/**
	 * remove message list and chat id from memory
	 * @param chatId
	 */
	public void removeMessages(String chatId);
	
	public boolean isChatExists(String chatId);
	
	public Set<String> getChatIds();
	
	/**
	 * check if current memory size is larger then config defined limit (services.cache.maxMemory)
	 * @return true if cache occupies more memory then defined, false otherwise
	 */
	public boolean isMemoryLimitReached();
	
	/**
	 * remove oldest entry
	 * @return key of removed entry
	 */
	public String removeOldest();
	
	public void close();
}
