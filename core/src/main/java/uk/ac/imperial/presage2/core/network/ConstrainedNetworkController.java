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

import com.google.inject.Inject;

import uk.ac.imperial.presage2.core.Time;
import uk.ac.imperial.presage2.core.environment.EnvironmentSharedStateAccess;

/**
 * @author Sam Macbeth
 *
 */
public class ConstrainedNetworkController extends NetworkController {

	protected List<NetworkConstraint> constraints;
	
	/**
	 * @param time
	 * @param environment
	 */
	@Inject
	public ConstrainedNetworkController(Time time,
			EnvironmentSharedStateAccess environment) {
		super(time, environment);
		constraints = new ArrayList<NetworkConstraint>();
	}
	
	public void addConstraint(NetworkConstraint c) {
		constraints.add(c);
	}

	@Override
	protected void handleMessage(Message m) {
		// apply NetworkConstraints.
		for(NetworkConstraint c : this.constraints) {
			m = c.constrainMessage(m);
		}
		super.handleMessage(m);
	}

	

}