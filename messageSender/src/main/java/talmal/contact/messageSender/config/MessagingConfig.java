package talmal.contact.messageSender.config;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig
{
	// these definitions need to be final so they can be used in annotations 
	public static final String CHAT_EXCHANGE = "chatExchange";
	public static final String TO_SLACK_NEW_MESSAGE_QUEUE = "to.slack.new.message";
	public static final String TO_SLACK_START_CHAT_QUEUE = "to.slack.start.chat";
	public static final String TO_SLACK_LOAD_CHAT_QUEUE = "to.slack.load.messages";
	public static final String FROM_SLACK_NEW_MESSAGE_QUEUE = "from.slack.new.message";
	
	@Bean
	public Declarables topicBindings()
	{
		TopicExchange topicExchange = new TopicExchange(MessagingConfig.CHAT_EXCHANGE);
		Queue fromSlackNewMessageQueue = new Queue(MessagingConfig.FROM_SLACK_NEW_MESSAGE_QUEUE, false);

		return new Declarables(topicExchange, 
				fromSlackNewMessageQueue, BindingBuilder.bind(fromSlackNewMessageQueue).to(topicExchange).with(fromSlackNewMessageQueue.getName())
				);
	}
	
	@Bean
	public MessageConverter messageConverter()
	{
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory)
	{
		final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setExchange(MessagingConfig.CHAT_EXCHANGE);
		rabbitTemplate.setMessageConverter(this.messageConverter());
//		rabbitTemplate.setReplyTimeout(30000); // defined with environment variables
		return rabbitTemplate;
	}
}
