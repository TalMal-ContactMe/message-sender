package talmal.contact.messageSender;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import talmal.contact.messageSender.models.context.ChatMessage;
import talmal.contact.messageSender.models.context.SenderType;
import talmal.contact.messageSender.services.CacheService;
import talmal.contact.messageSender.services.Tools;

//@Slf4j
@SpringBootTest(classes = MessageSenderApplicationTests.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MessageSenderApplicationTests {

	@Test
	void contextLoads() {
	}
	
	/**
	 * test my basic cache implementation  
	 */
	@Test
	public void cacheOperatorTest()
	{
		String chatId1 = "221668031192.056079";
		ChatMessage chatMessage1 = new ChatMessage(chatId1, "","","one", Tools.slackTsToInstant(chatId1), SenderType.USER);
		
		String chatId2 = "221668031193.056079";
		ChatMessage chatMessage2 = new ChatMessage(chatId1, "","","two", Tools.slackTsToInstant(chatId2), SenderType.USER);
		
		String chatId3 = "221668031194.056079";
		ChatMessage chatMessage3 = new ChatMessage(chatId1, "","","three", Tools.slackTsToInstant(chatId3), SenderType.USER);
		
		String chatId4 = "221668031195.056079";
		ChatMessage chatMessage4 = new ChatMessage(chatId4, "","","four", Tools.slackTsToInstant(chatId4), SenderType.USER);
		
		String chatId5 = "221668031196.056079";
		ChatMessage chatMessage5 = new ChatMessage(chatId5, "","","five", Tools.slackTsToInstant(chatId5), SenderType.USER);
		
		List<ChatMessage> messages = new ArrayList<ChatMessage>();
		messages.add(chatMessage2);
		messages.add(chatMessage5);
		messages.add(chatMessage4);
		messages.add(chatMessage1);
		messages.add(chatMessage3);
		
		CacheService cache = new CacheService(true,2);
		cache.addMessages(messages);

		//if(cache.isMemoryLimitReached())
		String removedChatId = cache.removeOldest();
		
		if(removedChatId != null)
		{
			assertEquals(removedChatId, chatId1);
		}
	}
}
