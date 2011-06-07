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

/**
 * 
 */
package uk.ac.imperial.presage2.core.network;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.imperial.presage2.core.network.BroadcastMessage;
import uk.ac.imperial.presage2.core.network.Message;


/**
 * @author Sam Macbeth
 *
 */
public class BroadcastMessageTest extends MessageTest {

	/**
	 * @see uk.ac.imperial.presage2.core.network.MessageTest#getRandomMessage()
	 */
	@Override
	protected BroadcastMessage getRandomMessage() {
		this.lastTime = this.randomTime();
		this.lastFrom = this.randomAddress();
		this.lastPerf = this.randomPerformative();
		return new BroadcastMessage(lastPerf, lastFrom, lastTime);
	}
	
	@Override
	@Test
	public void testMessage() {
		Message m = this.getRandomMessage();
		
		assertNotNull(m);
		assertTrue(m instanceof BroadcastMessage);
	}

}