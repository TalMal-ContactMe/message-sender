package talmal.contact.messageSender.controllers;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import talmal.contact.messageSender.config.MessagingConfig;
import talmal.contact.messageSender.config.SlackGson;
import talmal.contact.messageSender.models.ContactDetails;
import talmal.contact.messageSender.models.context.ChatMessage;
import talmal.contact.messageSender.services.SlackService;

@RestController
@RequestMapping(path = "/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public class MessageSenderController
{
	@Autowired
	private SlackService slackService;

	/**
	 * accept calls to start a new slack conversation
	 * 
	 * @param contactDetails - details from contact form
	 * @return - echo input request in ChatMessage form
	 */
	@PostMapping(path = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<ChatMessage> sendStartChatRequest(@RequestBody(required = true) ContactDetails contactDetails)
	{
		return Mono.just(this.slackService.sendMessage(contactDetails));
	}

	/**
	 * accept calls to send a new message to existing slack conversation
	 * 
	 * @param chatMessage - details of message
	 * @return - echo input request in ChatMessage form
	 */
	@PostMapping(path = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<ChatMessage> sendMessageReply(@RequestBody(required = true) ChatMessage chatMessage)
	{
		return Mono.just(this.slackService.sendMessage(chatMessage));
	}

	/**
	 * accept calls to load an existing slack conversation
	 * 
	 * @param chatId - conversation id (stack timestemp)
	 * @return - all existing messages in target slack conversation
	 */
	@GetMapping(path = "/open", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ChatMessage> subscribeToChat(@RequestParam(name = "chatId", required = true) String chatId)
	{
		return Flux.fromIterable(this.slackService.getChatMessages(chatId));
	}

	/**
	 * erase conversations from slack channel
	 * 
	 * @param channelName - input channel name to delete messages from channelName
	 *                    only, or null to delete messages from all channels
	 *                    (available to owner account).
	 */
	@DeleteMapping(path = "/deleteAll")
	public void deleteAll(@RequestParam(name = "channelName") String channelName)
	{
		this.slackService.deleteAllMessages(channelName);
	}

	/**
	 * listen to message queue for start new chat messages
	 * 
	 * @param contactDetails - details from contact form
	 * @return - echo input request in ChatMessage json form - back to message queue
	 */
	@RabbitListener(queues = MessagingConfig.TO_SLACK_START_CHAT_QUEUE)
	public String consumeStartChatQueue(String contactDetailsJson)
	{
		return SlackGson.toJson(this.slackService.sendMessage(SlackGson.fromJson(contactDetailsJson, ContactDetails.class)), ChatMessage.class);
	}

	/**
	 * listen to message queue for new chat message to existing chat
	 * 
	 * @param chatMessageJson - details from chat message
	 * @return - echo input request in ChatMessage json form - back to message queue
	 */
	@RabbitListener(queues = MessagingConfig.TO_SLACK_NEW_MESSAGE_QUEUE)
	public String consumeMessageQueue(String chatMessageJson)
	{
		return SlackGson.toJson(this.slackService.sendMessage(SlackGson.fromJson(chatMessageJson, ChatMessage.class)), ChatMessage.class);
	}

	/**
	 * listen to message queue to load an existing slack conversation
	 * 
	 * @param chatId - conversation id (stack timestemp)
	 * @return - all existing messages in target slack conversation as a list of
	 *         ChatMessages json string - back to message queue
	 */
	@RabbitListener(queues = MessagingConfig.TO_SLACK_LOAD_CHAT_QUEUE)
	public String subscribeToChatQueue(String chatId)
	{
		return SlackGson.toJson(this.slackService.getChatMessages(SlackGson.fromJson(chatId, String.class)));
	}
}
