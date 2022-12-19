package talmal.contact.messageSender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class MessageSenderApplication 
{
	public static void main(String[] args) 
	{
		SpringApplication.run(MessageSenderApplication.class, args);
	}
}
