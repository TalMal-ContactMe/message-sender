package talmal.contact.messageSender.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ContactDetails implements MessageToSlack
{
	private String name;
	private String email;
	private String message;
	
	@Override
	public String getText()
	{
		return String.format("%s (%s)", this.getMessage(), this.getEmail()).toString();
	}
	
	@Override
	public String getChatId()
	{
		return null;
	}
}
