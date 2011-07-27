/**
 * 	Copyright (C) 2011 Sam Macbeth <sm1106 [at] imperial [dot] ac [dot] uk>
 *
 * 	This file is part of Presage2.
 *
 *     Presage2 is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Presage2 is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser Public License
 *     along with Presage2.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.imperial.presage2.core.network;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sam Macbeth
 * 
 */
public class UnreachableRecipientException extends NetworkException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4463569707723015287L;

	final protected Message message;

	final protected List<NetworkAddress> recipients;

	/**
	 * @param message
	 * @param recipient
	 * @param e
	 */
	public UnreachableRecipientException(Message message,
			NetworkAddress recipient, Throwable e) {
		super("Unable to send message " + message.toString() + " to recipient "
				+ recipient.toString(), e);
		this.message = message;
		this.recipients = new ArrayList<NetworkAddress>();
		this.recipients.add(recipient);
	}

	public UnreachableRecipientException(Message message,
			List<NetworkAddress> recipients) {
		super("Unable to send message " + message.toString() + " to "
				+ recipients.size() + " recipients");
		this.message = message;
		this.recipients = recipients;
	}

}
