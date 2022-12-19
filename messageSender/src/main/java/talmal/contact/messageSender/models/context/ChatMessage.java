package talmal.contact.messageSender.models.context;

import java.time.Instant;
import java.util.Map;

import com.slack.api.app_backend.events.payload.MessagePayload;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import talmal.contact.messageSender.models.MessageToSlack;
import talmal.contact.messageSender.services.Tools;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ChatMessage implements MessageToSlack
{
	private static final String INPUT_KEY_CHAT_ID = "chatId";
	private static final String INPUT_KEY_MESSAGE_ID = "messageId";
	private static final String INPUT_KEY_NAME = "name";
	private static final String INPUT_KEY_MESSAGE = "message";
	private static final String INPUT_KEY_DATE = "date";
	private static final String INPUT_KEY_SENDER_TYPE = "senderType";

	private String chatId;
	private String messageId;
	private String name;
	private String message;
	private Instant date;
	private SenderType senderType;

	public ChatMessage(Map<String, String> input)
	{
		this.setChatId(input.get(ChatMessage.INPUT_KEY_CHAT_ID));
		this.setMessageId(input.get(ChatMessage.INPUT_KEY_MESSAGE_ID));
		this.setName(input.get(ChatMessage.INPUT_KEY_NAME));
		this.setMessage(input.get(ChatMessage.INPUT_KEY_MESSAGE));
		this.setDate(Instant.parse(input.get(ChatMessage.INPUT_KEY_DATE)));
		this.setSenderType(SenderType.valueOf(input.get(ChatMessage.INPUT_KEY_SENDER_TYPE)));
	}
	
	/**
	 * parse a slack response to chat message back into a chat message 
	 * @param response - slack response to chat message
	 */
	public ChatMessage(ChatPostMessageResponse response)
	{
		this.setChatId((response.getMessage().getThreadTs() == null) ? response.getMessage().getTs() : response.getMessage().getThreadTs());
		this.setMessageId(response.getMessage().getTs());
		this.setName(response.getMessage().getUsername());
		this.setMessage(response.getMessage().getText());
		this.setDate(Tools.slackTsToInstant(response.getMessage().getTs()));
		this.setSenderType(SenderType.USER);
	}

	public ChatMessage(String threadTs, MessagePayload messagePayload, String userName)
	{
		this.setChatId(threadTs);
		this.setMessageId(messagePayload.getEvent().getTs());
		this.setName(userName);
		this.setMessage(messagePayload.getEvent().getText());
		this.setDate(Tools.slackTsToInstant(messagePayload.getEvent().getTs()));
		this.setSenderType(SenderType.OWNER);
	}
	
	@Override
	public String getText()
	{
		return this.getMessage();
	}
}
