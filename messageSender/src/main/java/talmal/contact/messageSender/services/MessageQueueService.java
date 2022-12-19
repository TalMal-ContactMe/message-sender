package talmal.contact.messageSender.services;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import talmal.contact.messageSender.config.SlackGson;

@Service
public class MessageQueueService
{
	@Autowired
	private RabbitTemplate rabbitTemplate;
	
	/**
	 * send message to queue named routingKey 
	 * @param routingKey - the queue to send message to 
	 * @param message - object message to send (will be converted to json)
	 */
	public void convertAndSend(String routingKey, Object message)
	{
		this.rabbitTemplate.convertAndSend(routingKey, SlackGson.toJson(message));
	}
	
	/**
	 * send message to queue named routingKey, and wait for response as json string 
	 * @param routingKey - the queue to send message to 
	 * @param message - object message to send (will be converted to json)
	 * @return Object of json String or null
	 */
	public Object convertAndSendMessageAndReceiveMessage(String routingKey, Object message)
	{
		return this.rabbitTemplate.convertSendAndReceive(routingKey, SlackGson.toJson(message));
	}
}
