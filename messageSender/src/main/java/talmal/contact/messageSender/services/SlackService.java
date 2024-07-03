package talmal.contact.messageSender.services;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.slack.api.Slack;
import com.slack.api.app_backend.events.payload.MessagePayload;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatPostMessageRequest.ChatPostMessageRequestBuilder;
import com.slack.api.methods.response.chat.ChatDeleteResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.Message;
import com.slack.api.socket_mode.SocketModeClient;
import com.slack.api.socket_mode.listener.EnvelopeListener;
import com.slack.api.socket_mode.listener.WebSocketCloseListener;
import com.slack.api.socket_mode.listener.WebSocketErrorListener;
import com.slack.api.socket_mode.request.EventsApiEnvelope;
import com.slack.api.socket_mode.request.InteractiveEnvelope;
import com.slack.api.socket_mode.response.AckResponse;

import lombok.extern.slf4j.Slf4j;
import talmal.contact.messageSender.config.MessagingConfig;
import talmal.contact.messageSender.config.SlackGson;
import talmal.contact.messageSender.models.ContactDetails;
import talmal.contact.messageSender.models.MessageToSlack;
import talmal.contact.messageSender.models.context.ChatIdFlag;
import talmal.contact.messageSender.models.context.ChatMessage;
import talmal.contact.messageSender.models.context.MessageIdFlag;
import talmal.contact.messageSender.models.context.SenderType;

@Slf4j
@Service
public class SlackService
{
	private String token;
	private String socketToken;

	// static values can not be loaded from spring property files by law :/
	private static final String SLACK_USER_OWNER = "U03CSHQP35J";
	private static final String SLACK_USER_ROBOT = "U03DE3JP1DW";

	private static final String SLACK_EVENT_PAYLOAD_KEY_EVENT = "event";
	private static final String SLACK_EVENT_PAYLOAD_KEY_THREAD_TS = "thread_ts";
	private static final String SLACK_EVENT_PAYLOAD_KEY_USER = "user";
	private static final String SLACK_EVENT_PAYLOAD_KEY_TYPE = "type";
	private static final String SLACK_EVENT_PAYLOAD_KEY_SUBTYPE = "subtype";
	private static final String SLACK_EVENT_TYPE_VALUE_MESSAGE = "message";
	private static final String SLACK_EVENT_SUBTYPE_VALUE_BOT_MESSAGE = "bot_message";

	private final String SLACK_CHANNEL_OWNER_NAME;
	private final String SLACK_CHANNEL_ROBOT_NAME;

	private static final String DEFAULT_USER_NAME = "";

	private final String channelName;
	private String channelId;

	private Slack apiClient = null;
	private MethodsClient methodsClient = null;
	private SocketModeClient socketModeClient;

	@Autowired
	@Qualifier(value = "inMemory")
	private CacheInterface cache;

	@Autowired
	private MessageQueueService messageQueueService;
	
	// TODO: use @refreshScope to set config values dynamically, and verify it works
	// TODO: set load balancer cache. what is that about ?
	//@SpanName(value = "getAllBooksFromConsumer") // TODO: i could not find this "name" in zipkin, where do i see it ?
	
	public SlackService(@Value("${slack.gogo}") String token, @Value("${slack.socketToken}") String socketToken, @Value("${slack.channelOwnerName}") String slackChannelOwnerName,
		@Value("${slack.channelRobotName:Robot}") String slackChannelRobotName, @Value("${slack.channelId:}") String channelId, @Value("${slack.channelName:contact-me}") String channelName)
	{
		this.apiClient = Slack.getInstance();
		this.SLACK_CHANNEL_OWNER_NAME = slackChannelOwnerName;
		this.SLACK_CHANNEL_ROBOT_NAME = slackChannelRobotName;
		this.channelId = channelId;
		this.channelName = channelName;
		this.token = new String(Base64.getDecoder().decode(token));
		this.socketToken = socketToken;

		// Initialize an API Methods client with the given token
		this.methodsClient = this.apiClient.methods(token);
		try // i am not using this.socketModeClient with try-with-resource 
		// because i need to keep it outside of the local scope.
		{
			// open a socket to slack server
			this.socketModeClient = this.apiClient.socketMode(this.socketToken);

			// listen to slack events
			this.socketModeClient.addEventsApiEnvelopeListener(new EnvelopeListener<EventsApiEnvelope>()
			{
				@Override
				public void handle(EventsApiEnvelope envelope)
				{
					ChatMessage message = null;
					
					// acknowledge event transmission back to slack
					SlackService.this.socketModeClient.sendSocketModeResponse(new AckResponse(envelope.getEnvelopeId()));

					// differentiate between messages coming from chat-to-slack to messages coming
					// from slack-to-chat
					SenderType messageSender = SlackService.this.getMessageSenderFromPayload(envelope.getPayload());
					if (messageSender != null)
					{
						switch (messageSender)
						{
							case OWNER:
							{
								// message came from slack
								// check if message came from an active conversation
								JsonElement payloadJsonElement = envelope.getPayload();
								String threadTs = SlackService.this.getActiveChatId(payloadJsonElement);
								if (threadTs != null)
								{
									MessagePayload payload = SlackGson.fromJson(payloadJsonElement, MessagePayload.class);
									String userName = SlackService.this.parseUserName(payload.getEvent().getUser(), SlackService.DEFAULT_USER_NAME);
									message = new ChatMessage(threadTs, payload, userName);
									log.debug("Message from slack: {}", envelope);
									log.debug("Message parsed into: {}", message);
								}
								else
								{
									// message came from a closed conversation - nothing to do with it
									log.debug("message came from a closed conversation - nothing to do with it: {}", envelope);
								}
								break;
							}
							case USER:
							{
								// message came from user, than sent to slack, than slack sent this event - so
								// nothing to do here
								break;
							}
							case ROBOT:
							{
								log.debug("System message - nothing to do here.");
								break;
							}
							default:
							{
								log.error("Unimplamented SenderType {}, from message ", messageSender, envelope.toString());
								break;
							}
						}
					}

					if (message != null)
					{
						// save message to memory
						SlackService.this.cache.addMessage(message);

						// send received message to my chat using RabbitMQ
						SlackService.this.messageQueueService.convertAndSend(MessagingConfig.FROM_SLACK_NEW_MESSAGE_QUEUE, message);
					}
				}
			});

			this.socketModeClient.addWebSocketErrorListener(new WebSocketErrorListener()
			{
				@Override
				public void handle(Throwable reason)
				{
					log.error("WebSocketErrorListener: " + reason.getMessage(), reason);
				}
			});

			this.socketModeClient.addWebSocketCloseListener(new WebSocketCloseListener()
			{
				@Override
				public void handle(Integer code, String reason)
				{
					log.debug("WebSocketCloseListener: " + reason + ", code: " + code);
				}
			});

			this.socketModeClient.addInteractiveEnvelopeListener(new EnvelopeListener<InteractiveEnvelope>()
			{

				@Override
				public void handle(InteractiveEnvelope envelope)
				{
					log.debug("EnvelopeListener<InteractiveEnvelope>: " + envelope.toString());
				}
			});
			
			this.socketModeClient.connect();
		}
		catch (IOException e)
		{
			log.error(e.getMessage(), e);
		}
	}

	@PostConstruct
	public void PostConstruct() throws IOException
	{
		// make sure i have channel id called here because configuration file is only
		// loaded in constructor, so it is not available than.
		this.getChannelId();
	}

	@PreDestroy
	public void close() throws Exception
	{
		// close slack resources
		this.socketModeClient.disconnect();
		this.socketModeClient.close();
		this.apiClient.close();

		this.cache.close();
	}

	/**
	 * erase chat from memory
	 * 
	 * @param chatId
	 */
	public void closeChat(String chatId)
	{
		SlackService.this.cache.removeMessages(chatId);
	}

	/**
	 * start a conversation in slack channel, or send reply to existing conversation
	 * 
	 * @param chatMessage - message details, use MessageIdFlag as flag to signal non
	 *                    message operations, like closing chat.
	 * @return - accepted input message, or error message (flag is in messageId
	 *         field).
	 */
	public ChatMessage sendMessage(MessageToSlack chatMessage)
	{
		ChatMessage result = null;
		log.debug("in sendMessage() with message: {}", chatMessage);

		// check if "close chat" message
		if (chatMessage instanceof ChatMessage)
		{
			ChatMessage actuallChatMessage = (ChatMessage) chatMessage;

			// check for "close chat" flag
			if (actuallChatMessage.getMessageId().compareTo(MessageIdFlag.CLOSE_CHAT.name()) == 0)
			{
				// this is a "close chat" message to end chat
				// erase chat from cache
				this.cache.removeMessages(actuallChatMessage.getChatId());

				// if client side did not send a "close chat" message, the memory will
				// fill quickly, so erase oldest chat from cache.
				// if chat is not found in cache it is loaded from slack
				if (this.cache.isMemoryLimitReached())
				{
					this.cache.removeOldest();
				}

				// return input message as response, to close the conversation on the client
				// side
				result = actuallChatMessage;
			}
			else
			{
				result = this.sendMessageActual(chatMessage);
			}
		}
		else if (chatMessage instanceof ContactDetails)
		{
			result = this.sendMessageActual(chatMessage);
		}

		return result;
	}

	private ChatMessage sendMessageActual(MessageToSlack chatMessage)
	{
		ChatMessage result = null;

		// input message is a regular message - send it to slack
		try
		{
			// build request to slack
			ChatPostMessageRequestBuilder requestBuilder = ChatPostMessageRequest.builder().channel(this.getChannelId()).username(chatMessage.getName())
				// TODO: You can use a blocks[] array to send richer content
				.text(chatMessage.getText()).parse("none").unfurlLinks(false).unfurlMedia(false);

			// already existing chats have a chat id - attach input message to existing chat
			if (chatMessage.getChatId() != null)
			{
				// chat already exist - copy chat id
				requestBuilder.threadTs(chatMessage.getChatId());
			}

			// send the message to slack
			ChatPostMessageResponse response = this.methodsClient.chatPostMessage(requestBuilder.build());
			if (!response.isOk())
			{
				// tell client about error from slack
				result = new ChatMessage(ChatIdFlag.INVALID.name(), MessageIdFlag.ERROR.name(), chatMessage.getName(),
					String.format("Error: %s. Original message: %s.", response.getError(), chatMessage.toString()), Instant.now(), SenderType.USER);
				log.error("Error - message not sent: {}", response.getError());
			}
			else
			{
				// parse slack response to chat message
				result = new ChatMessage(response);
			}

		}
		catch (IOException | SlackApiException e)
		{
			// tell client about exception from slack
			result = new ChatMessage(ChatIdFlag.INVALID.name(), MessageIdFlag.ERROR.name(), chatMessage.getName(),
				String.format("Error: %s. Original message: %s.", e.getMessage(), chatMessage.toString()), Instant.now(), SenderType.USER);
			log.error(e.getMessage(), e);
		}

		// save message to memory
		this.cache.addMessage(result);
		log.debug("message accepted at slack: {}", result.toString());

		return result;
	}

	/**
	 * get messages of specific conversation
	 * 
	 * @param chatId - slack conversation timestamp to get
	 * @return - list of chat messages.
	 */
	public List<ChatMessage> getChatMessages(String chatId)
	{
		List<ChatMessage> result = new ArrayList<ChatMessage>();

		if (chatId != null)
		{
			// look for chat in cache
			List<ChatMessage> messages = this.cache.getMessages(chatId);
			if (messages != null)
			{
				result = messages;
			}
			else
			{
				// chat not found in cache - fetch chat messages from slack
				List<Message> chatMessages = this.fetchHistory(chatId);

				// parse conversation
				if (chatMessages != null && !chatMessages.isEmpty())
				{
					// parse slack messages into chat messages
					result = chatMessages.stream().map(message ->
					{
						// parse slack message into chat message
						ChatMessage chatMessage = new ChatMessage((message.getThreadTs() == null) ? message.getTs() : message.getThreadTs(), message.getTs(),
							this.parseUserName(message.getUser(), message.getUsername()), message.getText(), Tools.slackTsToInstant(message.getTs()),
							this.getMessageSender(message.getUser(), message.getSubtype()));

						return chatMessage;
					}).collect(Collectors.toList());

					// save messages in memory - for low latency user refresh
					this.cache.addMessages(result);
				}
				else
				{
					// no chat found for input chatId
					log.debug("no chat found for input chatId: {}", chatId);
				}
			}
		}

		return result;
	}

	/**
	 * Fetch conversation history using channel ID and chat id
	 * 
	 * @param chatId - conversation id to fetch (slack timestamp)
	 */
	private List<Message> fetchHistory(String chatId)
	{
		List<Message> result = new ArrayList<Message>();

		if (chatId != null)
		{
			// keep messages in memory
			try
			{
				// get a conversation from slack, created at input timestamp (chatId)
				ConversationsHistoryResponse conversationsHistoryResponse = this.methodsClient
					.conversationsHistory(r -> r.token(this.token).channel(this.channelId).latest(chatId).oldest(chatId).inclusive(true));

				if (conversationsHistoryResponse.isOk())
				{
					// only one conversation should match input chat id
					if (conversationsHistoryResponse.getMessages().size() <= 1)
					{
						conversationsHistoryResponse.getMessages().stream().filter(message -> message.getTs().compareTo(chatId) == 0).forEach(message ->
						{
							// add first message in conversation - starting message
							result.add(message);

							// load the rest of the conversation - all the replies
							result.addAll(this.fetchMessageReplies(message.getTs()));
						});
					}
					else
					{
						log.error("Error: More then one conversation found with chat id: {}", chatId);
					}
				}
				else
				{
					log.error("Error: {}, for channel id: {}, and chat Id", conversationsHistoryResponse.getError(), this.channelId, chatId);
				}
			}
			catch (IOException | SlackApiException e)
			{
				log.error("error: {}", e.getMessage(), e);
			}
		}
		else
		{
			log.error("error: Invalid input chatId: {}", chatId);
		}

		return result;
	}

	/**
	 * Fetch message replies from slack
	 * @param ts - conversation (slack timestamp) to get replies for
	 * @return list of Message (could be empty list)
	 */
	private List<Message> fetchMessageReplies(String ts)
	{
		List<Message> result = new ArrayList<Message>();
		try
		{
			// send request to slack
			ConversationsRepliesResponse conversationsRepliesResponse = this.methodsClient.conversationsReplies(request -> request.token(this.token).channel(this.channelId).ts(ts));
			// Assumption: chats are short (smaller then 100 messages) so no need for
			// pagination
//				.limit(SlackService.SLACK_REQUEST_SIZE_LIMIT));

			if (conversationsRepliesResponse.isOk())
			{
				result.addAll(conversationsRepliesResponse.getMessages().stream()
					// reply messages Ts is always different from ThreadTs
					.filter(message ->
					{
						return message.getTs() != null 
							&& message.getThreadTs() != null 
							&& message.getTs().compareTo(message.getThreadTs()) != 0;
					}).collect(Collectors.toList()));
			}
			else
			{
				log.error(conversationsRepliesResponse.getError());
			}
		}
		catch (IOException | SlackApiException e)
		{
			log.error("error: {}", e.getMessage(), e);
		}

		return result;
	}

	/**
	 * get channel id from environment file under channelId, or from slack according
	 * to channel name (set in environment file as channelName)
	 * @return string of slack channel id, or null if not found
	 */
	private String getChannelId()
	{
		String result = this.channelId;

		if (result.isBlank())
		{
			result = this.findConversation(this.channelName);
			if (result != null)
			{
				this.channelId = result;
			}
		}

		return result;
	}

	/**
	 * get channel id for slack channel with input channel name
	 * @param channelName - channel to get id for
	 * @return string of slack channel id, or null if not found
	 */
	private String findConversation(String channelName)
	{
		String result = null;

		try
		{
			if (channelName != null)
			{
				// Call the conversations.list method using the built-in WebClient
				ConversationsListResponse conversationsListResponse = this.methodsClient.conversationsList(r -> r.token(this.token));

				if (conversationsListResponse != null)
				{
					for (Conversation channel : conversationsListResponse.getChannels())
					{
						if (channel.getName().equals(channelName))
						{
							result = channel.getId();
							break;
						}
					}
				}
				else
				{
					log.error("no conversations list found");
				}
			}
		}
		catch (IOException | SlackApiException e)
		{
			log.error("error: {}", e.getMessage(), e);
		}

		return result;
	}

	/**
	 * find out if message came from SenderType.USER (frontEnd chat) or from
	 * SenderType.OWNER (slack 3rd party service) according to message data. read
	 * data from payload manually - because the api removes needed data.
	 * 
	 * @param payload - slack message payload
	 * @return SenderType or null
	 */
	private SenderType getMessageSenderFromPayload(JsonElement payload)
	{
		SenderType result = null;

		// find out if it is a USER/OWNER message
		// payload from slack contains "user" key in event
		JsonElement userElement = this.extractValueFromJsonElement(payload, SlackService.SLACK_EVENT_PAYLOAD_KEY_EVENT, SlackService.SLACK_EVENT_PAYLOAD_KEY_USER);
		if (userElement != null)
		{
			String userString = SlackGson.fromJson(userElement, String.class);
			// message came from slack
			switch (userString)
			{
				case SlackService.SLACK_USER_OWNER:
				{
					result = SenderType.OWNER;
					break;
				}
				case SlackService.SLACK_USER_ROBOT:
				{
					result = SenderType.USER;
					break;
				}
				default:
				{
					log.error("unrecognized user: {}", userString);
					break;
				}
			}
		}
		else
		{
			// payload from user does not contain "user" key
			// test if message was sent to slack, from my chat
			JsonElement eventElement = this.extractValueFromJsonElement(payload, SlackService.SLACK_EVENT_PAYLOAD_KEY_EVENT);
			if (eventElement != null)
			{
				JsonElement typeElement = this.extractValueFromJsonElement(eventElement, SlackService.SLACK_EVENT_PAYLOAD_KEY_TYPE);
				JsonElement subTypeElement = this.extractValueFromJsonElement(eventElement, SlackService.SLACK_EVENT_PAYLOAD_KEY_SUBTYPE);

				if (typeElement != null && subTypeElement != null)
				{
					String eventType = SlackGson.fromJson(typeElement, String.class);
					String eventSubType = SlackGson.fromJson(subTypeElement, String.class);

					if (eventType.compareTo(SlackService.SLACK_EVENT_TYPE_VALUE_MESSAGE) == 0 
						&& eventSubType.compareTo(SlackService.SLACK_EVENT_SUBTYPE_VALUE_BOT_MESSAGE) == 0)
					{
						result = SenderType.USER;
					}
					else
					{
						// assume it is a system message like "message_change", "message_delete"...
						// TODO: handle more message sub-types like "message_change","message_delete"...
						result = SenderType.ROBOT;
					}
				}
			}
		}

		return result;
	}

	/**
	 * 
	 * @param jsonElement - a JsonObject
	 * @param keys        - list of keys representing the path in json tree from
	 *                    whole object to target key
	 * @return last found value as JsonElement or null otherwise.
	 */
	private JsonElement extractValueFromJsonElement(JsonElement jsonElement, String... keys)
	{
		JsonElement result = null;

		if (jsonElement != null && jsonElement.isJsonObject() && keys != null && keys.length > 0)
		{
			// search down the tree hierarchy according to input keys
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			for (String currentKey : keys)
			{
				JsonElement value = jsonObject.get(currentKey);
				if (value != null)
				{
					result = value;

					// ready for next iteration
					if (value.isJsonObject())
					{
						jsonObject = value.getAsJsonObject();
					}
					else
					{
						break;
					}
				}
				else
				{
					// end tree search
					result = null;
					break;
				}
			}
		}
		else
		{
			log.error("Incorrect input for call keys: {}, jsonElement: {}", keys, jsonElement);
		}

		return result;
	}

	/**
	 * delete messages from single/all channels
	 * 
	 * @param channelName - input channel name to delete messages from channelName
	 *                    only, or null to delete messages from all channels
	 *                    (available to owner account).
	 */
	public void deleteAllMessages(String channelName)
	{
		try
		{
			// get all channels for given taken
			ConversationsListResponse conversationsListResponse = this.methodsClient.conversationsList(r -> r.token(this.token));

			if (conversationsListResponse != null)
			{
				for (Conversation channel : conversationsListResponse.getChannels())
				{
					// select only input channel
					if (channelName == null || channelName.compareTo(channel.getName()) == 0)
					{
						String channelId = channel.getId();

						// get all conversations in channel
						ConversationsHistoryResponse conversationsHistoryResponse = this.methodsClient.conversationsHistory(r -> r.token(this.token).channel(channelId));

						if (conversationsHistoryResponse.isOk())
						{
							// get all messages in conversation
							// TODO: for some reason, conversation replies are not deleted - find out why 
							conversationsHistoryResponse.getMessages().stream().forEach(message ->
							{
								try
								{
									// slack has a rate limit on calls, so take it easy  
									Thread.sleep(1000);
								}
								catch (InterruptedException e)
								{
									log.error(e.getLocalizedMessage(),e);
								}
								
								this.deleteMessage(channelId, ((Message) message).getTs());
							});
						}
						else
						{
							log.error("Error: {}, for channel id: {}", conversationsHistoryResponse.getError(), channelId);
						}
					}
				}
			}
			else
			{
				log.debug("no conversations list found");
			}
		}
		catch (IOException | SlackApiException e)
		{
			log.error("error: {}", e.getMessage(), e);
		}
	}

	/**
	 * delete a single message in a channel
	 * 
	 * @param channelId - channel id in slack
	 * @param ts        - message id
	 */
	public void deleteMessage(String channelId, String ts)
	{
		try
		{
			ChatDeleteResponse response = this.methodsClient.chatDelete(r -> r.token(this.token).channel(channelId).ts(ts));
			if (response.isOk())
			{
				log.info("Deleted message: {} in channel: {}", response.getTs(), response.getChannel());
			}
			else
			{
				log.info("Error from deleted message: {}", response.getError());
			}

		}
		catch (IOException | SlackApiException e)
		{
			log.error(e.getMessage(), e);
		}
	}

	/**
	 * parse slack userId into user friendly names
	 * 
	 * @param slackUser
	 * @param slackUserName
	 * @return
	 */
	private String parseUserName(String slackUser, String slackUserName)
	{
		String result = slackUserName;
		if (slackUser != null && !slackUser.isEmpty())
		{
			// check if message is written by channel owner
			switch (slackUser)
			{
				case SlackService.SLACK_USER_OWNER:
				{
					result = this.SLACK_CHANNEL_OWNER_NAME;
					break;
				}
				case SlackService.SLACK_USER_ROBOT:
				{
					result = this.SLACK_CHANNEL_ROBOT_NAME;
					break;
				}
				default:
				{
					log.error("unknown slack user {}", slackUser);
					break;
				}
			}
		}

		return result;
	}

	/**
	 * figure where slack message came from, user chat or slack service
	 * 
	 * @param sender  - slack message.user
	 * @param subtype - slack message.subtype
	 * @return chosen SenderType according to input, or SenderType.USER on error
	 */
	private SenderType getMessageSender(String sender, String subtype)
	{
		SenderType result = SenderType.USER;

		if (sender != null)
		{
			switch (sender)
			{
				case SlackService.SLACK_USER_OWNER:
				{
					result = SenderType.OWNER;
					break;
				}
				case SlackService.SLACK_USER_ROBOT:
				{
					result = SenderType.USER;
					break;
				}
				default:
				{
					log.error("unrecognized sender: {}", sender);
					break;
				}
			}
		}
		else
		{
			if (subtype != null && subtype.compareTo(SlackService.SLACK_EVENT_SUBTYPE_VALUE_BOT_MESSAGE) == 0)
			{
				result = SenderType.USER;
			}
			else
			{
				log.error("unrecognized subtype: {}", subtype);
			}
		}

		return result;
	}

	/**
	 * check if incoming message came from currently active chat with user
	 * @param payload - slack event envelope.payload
	 * @return chatId if message came from currently active slack conversation, null
	 *         if message is not in cache
	 */
	private String getActiveChatId(JsonElement payload)
	{
		String result = null;

		if (payload != null)
		{
			// extract slack threadTs, which is equal to chatId
			// manually extract message threadTs, because regular Json deserializing removes
			// important details
			JsonElement threadTsJson = SlackService.this.extractValueFromJsonElement(payload, SlackService.SLACK_EVENT_PAYLOAD_KEY_EVENT, SlackService.SLACK_EVENT_PAYLOAD_KEY_THREAD_TS);
			if (threadTsJson != null)
			{
				String threadTs = SlackGson.fromJson(threadTsJson, String.class);
				if (this.cache.isChatExists(threadTs))
				{
					result = threadTs;
				}
			}
			else
			{
				log.error("Message threadTs not found in message: {}", payload);
			}
		}

		return result;
	}
}
