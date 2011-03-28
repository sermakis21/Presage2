/**
 * 
 */
package uk.ac.imperial.presage2.core.network;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.imperial.presage2.core.Time;
import uk.ac.imperial.presage2.core.TimeDriven;
import uk.ac.imperial.presage2.core.environment.EnvironmentSharedStateAccess;

/**
 * <p>This is a central controller through which all messages go.</p>
 * 
 * <p>Like the environment it ensures agents obey the rules of the system. In
 * this case these are to do with the network, such as transmission ranges etc.</p>
 * 
 * @author Sam Macbeth
 *
 */
@Singleton
public class NetworkController implements NetworkChannel, TimeDriven {

	private final Logger logger = Logger.getLogger(NetworkController.class);
	
	protected Time time;
	
	protected List<Message> toDeliver;
	
	/**
	 * Map of devices registered to this controller.
	 */
	protected Map<NetworkAddress, NetworkChannel> devices;
	
	/**
	 * Access to environment shared state.
	 */
	protected EnvironmentSharedStateAccess environment;
	
	/**
	 * @param time
	 */
	@Inject
	public NetworkController(Time time, EnvironmentSharedStateAccess environment) {
		super();
		this.time = time;
		this.environment = environment;
		this.devices = new HashMap<NetworkAddress, NetworkChannel>();
		this.toDeliver = new LinkedList<Message>();
	}
	
	/**
	 * @see uk.ac.imperial.presage2.core.TimeDriven#incrementTime()
	 */
	@Override
	public void incrementTime() {
		if(this.logger.isDebugEnabled()) {
			this.logger.debug("Delivering messages for time "+this.time.toString());
		}
		for(Message m : this.toDeliver) {
			try {
				this.handleMessage(m);
			} catch(NetworkException e) {
				// log exceptions we encounter (unchecked runtime exceptions)
				this.logger.warn(e.getMessage(), e);
			}
		}
		this.toDeliver = new LinkedList<Message>();
		this.time.increment();
	}

	/**
	 * <p>Invoked by a NetworkConnector when it wishes to send a message.</p>
	 * <p>In this implementation we deliver at the end of time cycle, therefore this function just adds
	 * the message to the delivery queue</p>
	 * @see uk.ac.imperial.presage2.core.network.NetworkChannel#deliverMessage(uk.ac.imperial.presage2.core.network.Message)
	 */
	@Override
	synchronized public void deliverMessage(Message m) {
		this.toDeliver.add(m);
	}
	
	protected void handleMessage(Message m) {
		// check message type
		if(m instanceof UnicastMessage) {
			doUnicast((UnicastMessage) m);
		} else if(m instanceof MulticastMessage) {
			doMulticast((MulticastMessage) m);
		} else if(m instanceof BroadcastMessage) {
			doBroadcast((BroadcastMessage) m);
		} else {
			throw new UnknownMessageTypeException(m);
		}
	}

	/**
	 * Send a unicast message
	 * @param m
	 * @throws NetworkException
	 */
	protected void doUnicast(UnicastMessage m) {
		try {
			this.devices.get(m.getTo()).deliverMessage(m);
			if(this.logger.isDebugEnabled()) {
				this.logger.debug("Dispatched unicast message: "+ m.toString());
			}
		} catch(NullPointerException e) {
			throw new UnreachableRecipientException(m, m.getTo(), e);
		}
	}
	
	/**
	 * Send a multicast message
	 * @param m
	 */
	protected void doMulticast(MulticastMessage m) {
		final List<NetworkAddress> recipients = m.getTo();
		final List<NetworkAddress> unreachable = new LinkedList<NetworkAddress>();
		for(NetworkAddress to : recipients) {
			try {		
				this.devices.get(to).deliverMessage(m);
			} catch(NullPointerException e) {
				unreachable.add(to);
			}
		}
		if(this.logger.isDebugEnabled()) {
			this.logger.debug("Sent multicast message: "+ m.toString());
		}
		if(unreachable.size() > 0) {
			throw new UnreachableRecipientException(m, unreachable);
		}
	}
	
	/**
	 * Send a broadcast message
	 * @param m
	 */
	protected void doBroadcast(BroadcastMessage m) {
		for(NetworkAddress to : this.devices.keySet()) {
			// deliver to all but sender
			if(m.getFrom() != to)
				this.devices.get(to).deliverMessage(m);
		}
		this.logger.debug("Sent broadcast message: "+ m.toString());
	}

	/**
	 * Register a network device with this NetworkController
	 * @param req
	 * @throws NetworkException
	 */
	synchronized public void registerConnector(NetworkRegistrationRequest req) {
		// defensive programming
		if(req == null || req.getAddress() == null || req.getLink() == null) {
				throw new NullPointerException("NetworkRegistrationRequest null or containing null parameters");
		}

		this.devices.put(req.getAddress(), req.getLink());
	}

}
