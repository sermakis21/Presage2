/**
 * 	Copyright (C) 2011-2012 Sam Macbeth <sm1106 [at] imperial [dot] ac [dot] uk>
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
package uk.ac.imperial.presage2.core.simulator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import uk.ac.imperial.presage2.core.Time;
import uk.ac.imperial.presage2.core.db.DatabaseModule;
import uk.ac.imperial.presage2.core.db.DatabaseService;
import uk.ac.imperial.presage2.core.db.StorageService;
import uk.ac.imperial.presage2.core.db.persistent.PersistentSimulation;
import uk.ac.imperial.presage2.core.event.EventBus;
import uk.ac.imperial.presage2.core.event.EventBusModule;
import uk.ac.imperial.presage2.core.event.EventListener;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * <p>
 * A RunnableSimulation is a wrapper for a parameterised experiment we can run.
 * </p>
 * 
 * <p>
 * It gives method to monitor the progress and state of the simulation from a
 * controlling program.
 * </p>
 * 
 * @author Sam Macbeth
 * 
 */
public abstract class RunnableSimulation implements Runnable {

	protected final Logger logger = Logger.getLogger(RunnableSimulation.class);

	protected SimulationState state = SimulationState.LOADING;

	protected Scenario scenario;

	protected Simulator simulator;

	protected DatabaseService database;
	protected StorageService storage;
	protected PersistentSimulation simPersist;

	private Map<String, Field> fieldParameters = new HashMap<String, Field>();

	private Map<String, Method> methodParameters = new HashMap<String, Method>();

	@Parameter(name = "finishTime")
	public int finishTime;

	private long id = -1;

	public enum SimulationState {
		LOADING, READY, INITIALISING, RUNNING, PAUSED, STOPPED, FINISHING, COMPLETE
	}

	/**
	 * Inform the simulation that parameters have been initialised and that it
	 * should build itself ready to run.
	 */
	public abstract void load();

	/**
	 * Get the simulation's current state.
	 * 
	 * @return {@link SimulationState}
	 */
	public SimulationState getState() {
		return this.state;
	}

	/**
	 * Get the current time in the underlying running simulation.
	 * 
	 * @return {@link Time}
	 */
	public Time getCurrentSimulationTime() {
		if (simulator == null)
			return null;
		else
			return simulator.getCurrentSimulationTime();
	}

	/**
	 * Get the time the underlying simulation should finish.
	 * 
	 * @return {@link Time}
	 */
	public Time getSimulationFinishTime() {
		if (scenario == null)
			return null;
		else
			return scenario.getFinishTime();
	}

	/**
	 * Get the percentage completion of the simulation.
	 * 
	 * @return
	 */
	public int getSimluationPercentComplete() {
		if (getSimulationFinishTime() == null
				|| getCurrentSimulationTime() == null)
			return 0;
		else
			return 100 * getCurrentSimulationTime().intValue()
					/ getSimulationFinishTime().intValue();
	}

	/**
	 * Get the underlying scenario this simulation is running.
	 * 
	 * @return {@link Scenario}
	 */
	public Scenario getScenario() {
		return this.scenario;
	}

	/**
	 * Get the simulator running this simulation.
	 * 
	 * @return {@link Simulator}
	 */
	public Simulator getSimulator() {
		return this.simulator;
	}

	protected void initDatabase() {
		if (this.database != null && !this.database.isStarted()) {
			try {
				this.database.start();
			} catch (Exception e) {
				logger.warn("Failed to start DB", e);
				this.database = null;
				this.storage = null;
			}
		}
		if (this.storage != null) {
			if (simPersist == null) {
				if (this.id >= 0) {
					simPersist = storage.getSimulationById(this.id);
					storage.setSimulation(simPersist);
				} else {
					simPersist = storage.createSimulation(getClass()
							.getSimpleName(), getClass().getCanonicalName(),
							getState().name(), getSimulationFinishTime()
									.intValue());
					for (String s : getParameters().keySet()) {
						simPersist.addParameter(s, getParameter(s));
					}
				}
			}
			simPersist.setStartedAt(new Date().getTime());
		}
	}

	private void updateDatabase() {
		if (this.storage != null) {
			if (!getState().name().equals(simPersist.getState())) {
				simPersist.setState(getState().name());
				if (getState() == SimulationState.COMPLETE) {
					simPersist.setFinishedAt(new Date().getTime());
				}
			}
			simPersist.setCurrentTime(getCurrentSimulationTime().intValue());
		}
	}

	@EventListener
	public void onNewTimeCycle(EndOfTimeCycle e) {
		updateDatabase();
	}

	final public String getParameter(String name) {
		for (Field f : this.getClass().getFields()) {
			Parameter param = f.getAnnotation(Parameter.class);
			if (param != null) {
				if (param.name().equalsIgnoreCase(name)) {
					try {
						return f.get(this).toString();
					} catch (IllegalArgumentException e) {
						logger.debug("Couldn't get value of field " + name, e);
					} catch (IllegalAccessException e) {
						logger.debug("Couldn't get value of field " + name, e);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Get the {@link Parameter}s flagged in this Simulation.
	 * 
	 * @return
	 */
	final public Map<String, Class<?>> getParameters() {
		Map<String, Class<?>> parameters = new HashMap<String, Class<?>>();
		parameters.putAll(getParametersFromFields());
		parameters.putAll(getParametersFromMethods());
		if (logger.isDebugEnabled()) {
			logger.debug("Got " + parameters.size()
					+ " parameters in simulation "
					+ this.getClass().getSimpleName());
		}
		return parameters;
	}

	final private Map<String, Class<?>> getParametersFromFields() {
		Map<String, Class<?>> parameters = new HashMap<String, Class<?>>();
		for (Field f : this.getClass().getFields()) {
			Parameter param = f.getAnnotation(Parameter.class);

			if (param != null) {
				Class<?> paramType = f.getType();
				parameters.put(param.name(), paramType);
				fieldParameters.put(param.name(), f);
			}
		}
		return parameters;
	}

	final private Map<String, Class<?>> getParametersFromMethods() {
		Map<String, Class<?>> parameters = new HashMap<String, Class<?>>();
		for (Method m : this.getClass().getMethods()) {
			Parameter param = m.getAnnotation(Parameter.class);

			if (param != null && m.getParameterTypes().length == 1) {
				Class<?> paramType = m.getParameterTypes()[0];
				parameters.put(param.name(), paramType);
				methodParameters.put(param.name(), m);
			}
		}
		return parameters;
	}

	final protected void setParameters(Map<String, String> provided)
			throws UndefinedParameterException, IllegalArgumentException,
			IllegalAccessException, InvocationTargetException {
		for (Map.Entry<String, Class<?>> entry : this.getParameters()
				.entrySet()) {
			if (!provided.containsKey(entry.getKey())) {
				if (!optionalParameter(entry.getKey())) {
					logger.fatal("No value provided for " + entry.getKey()
							+ " parameter.");
					throw new UndefinedParameterException(entry.getKey());
				}
				continue;
			}
			this.setParameter(entry.getKey(), provided.get(entry.getKey()));
		}
	}

	private boolean optionalParameter(String key)
			throws UndefinedParameterException {
		if (fieldParameters.containsKey(key)) {
			return fieldParameters.get(key).getAnnotation(Parameter.class)
					.optional();
		} else if (methodParameters.containsKey(key)) {
			return methodParameters.get(key).getAnnotation(Parameter.class)
					.optional();
		}
		throw new UndefinedParameterException(key);
	}

	/**
	 * Set the value of the named parameter for this simulation.
	 * 
	 * @param name
	 *            name of the {@link Parameter}
	 * @param value
	 *            of the parameter as a string. We try and convert this to the
	 *            appropriate type to insert into the field.
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	final protected void setParameter(String name, String value)
			throws IllegalArgumentException, IllegalAccessException,
			InvocationTargetException {
		if (fieldParameters.containsKey(name)) {
			Class<?> type = fieldParameters.get(name).getType();
			if (type == String.class) {
				fieldParameters.get(name).set(this, value);
			} else if (type == Integer.class || type == Integer.TYPE) {
				fieldParameters.get(name).setInt(this, Integer.parseInt(value));
			} else if (type == Double.class || type == Double.TYPE) {
				fieldParameters.get(name).setDouble(this,
						Double.parseDouble(value));
			} else if (type == Boolean.class || type == Boolean.TYPE) {
				fieldParameters.get(name).setBoolean(this,
						Boolean.parseBoolean(value));
			} else if (type.isEnum()) {
				fieldParameters.get(name).set(this,
						Enum.valueOf((Class<Enum>) type, value));
			}
		} else if (methodParameters.containsKey(name)) {
			Class<?> type = methodParameters.get(name).getParameterTypes()[0];
			if (type == String.class) {
				methodParameters.get(name).invoke(this, value);
			} else if (type == Integer.class || type == Integer.TYPE) {
				methodParameters.get(name)
						.invoke(this, Integer.parseInt(value));
			} else if (type == Double.class || type == Double.TYPE) {
				methodParameters.get(name).invoke(this,
						Double.parseDouble(value));
			} else if (type == Boolean.class || type == Boolean.TYPE) {
				methodParameters.get(name).invoke(this,
						Boolean.parseBoolean(value));
			} else if (type.isEnum()) {
				methodParameters.get(name).invoke(this,
						Enum.valueOf((Class<Enum>) type, value));
			}
		}
	}

	protected void setDatabase(DatabaseService database) {
		this.database = database;
	}

	protected void setStorage(StorageService db) {
		this.storage = db;
	}

	protected void setEventBus(EventBus e) {
		e.subscribe(this);
	}

	/**
	 * <p>
	 * Create a new {@link RunnableSimulation} from a provided string
	 * representing it's fully qualified name and an array of parameters to it's
	 * constructor.
	 * </p>
	 * <p>
	 * The method will search for an appropriate constructor for the given
	 * parameters.
	 * </p>
	 * 
	 * @param className
	 *            string representing the fully qualified name of a
	 *            {@link RunnableSimulation}
	 * @param ctorParams
	 *            array of parameters to the constructor
	 * @return {@link RunnableSimulation}
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	final public static RunnableSimulation newFromClassName(String className,
			Object... ctorParams) throws ClassNotFoundException,
			NoSuchMethodException, InvocationTargetException,
			InstantiationException, IllegalAccessException {
		final Logger logger = Logger.getLogger(RunnableSimulation.class);

		// Find Class and assert it is a RunnableSimulation
		Class<? extends RunnableSimulation> clazz = null;
		try {
			clazz = Class.forName(className).asSubclass(
					RunnableSimulation.class);
		} catch (ClassNotFoundException e) {
			logger.fatal(className + " is not on the classpath!", e);
			throw e;
		} catch (ClassCastException e) {
			logger.fatal(className + " is not a RunnableSimulation!");
			throw e;
		}

		// find ctor which matches params given.
		Constructor<? extends RunnableSimulation> ctor = null;
		Class<?>[] paramTypes = new Class<?>[ctorParams.length];
		for (int i = 0; i < ctorParams.length; i++) {
			paramTypes[i] = ctorParams[i].getClass();
		}
		try {
			ctor = ObjectFactory.getConstructor(clazz, paramTypes);
		} catch (SecurityException e) {
			logger.fatal("Could not get constructor for " + clazz, e);
			throw (e);
		} catch (NoSuchMethodException e) {
			logger.fatal("Could not find constructor for " + clazz, e);
			throw (e);
		}

		// create RunnableSimulation object
		RunnableSimulation simObj = null;
		try {
			simObj = ctor.newInstance(ctorParams);
		} catch (IllegalArgumentException e) {
			logger.fatal("Failed to create the RunnableSimulation", e);
			throw e;
		} catch (InvocationTargetException e) {
			logger.fatal("Failed to create the RunnableSimulation", e);
			throw e;
		} catch (InstantiationException e) {
			logger.fatal("Failed to create the RunnableSimulation", e);
			throw e;
		} catch (IllegalAccessException e) {
			logger.fatal("Failed to create the RunnableSimulation", e);
			throw e;
		}

		return simObj;
	}

	/**
	 * Run this simulation.
	 */
	@Override
	public void run() {

		this.state = SimulationState.INITIALISING;
		updateDatabase();
		this.simulator.initialise();

		this.state = SimulationState.RUNNING;
		updateDatabase();
		this.simulator.run();

		this.state = SimulationState.FINISHING;
		updateDatabase();
		this.simulator.complete();
		this.state = SimulationState.COMPLETE;
		updateDatabase();

		if (this.database != null) {
			this.database.stop();
		}
		this.simulator.shutdown();
	}

	/**
	 * <p>
	 * Run a single simulation from commandline arguments. Takes the following
	 * parameters:
	 * </p>
	 * 
	 * <code>simulation_class_name simulation_parameter=parameter_value...</code>
	 * <p>
	 * Where <code>simulation_class_name</code> is the fully qualified name of a
	 * class which implements {@link RunnableSimulation} and is visible to this
	 * class (i.e. public), and
	 * <code>simulation_parameter=parameter_value</code> are key/value pairs for
	 * simulation parameters. This pairs should correspond to {@link Parameter}
	 * annotations on fields or methods within the {@link RunnableSimulation} we
	 * are running. The key is the name assigned to each {@link Parameter}
	 * inside the annotations. These fields and methods must be public in order
	 * for use to insert the provided values in.
	 * </p>
	 * 
	 * @param args
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws UndefinedParameterException
	 * @throws IllegalArgumentException
	 */
	final public static void main(String[] args) throws ClassNotFoundException,
			NoSuchMethodException, InvocationTargetException,
			InstantiationException, IllegalAccessException,
			IllegalArgumentException, UndefinedParameterException {

		if (args.length < 1) {
			System.err.println("No args provided, expected 1 or more.");
			return;
		}
		System.out.println("Starting presage2 simulation: " + args[0]);
		System.out.print("Parameters given: ");
		if (args.length == 1) {
			System.out.print("None.");
		}
		for (int i = 1; i < args.length; i++) {
			System.out.print(args[i] + ",");
		}
		System.out.println();

		// Additional modules we want for this simulation run
		Set<AbstractModule> additionalModules = new HashSet<AbstractModule>();
		additionalModules.add(new EventBusModule());
		// database module
		AbstractModule dbModule = DatabaseModule.load();
		if (dbModule != null)
			additionalModules.add(dbModule);

		// Create the runnable simulation assuming it's an InjectedSimulation
		RunnableSimulation sim = newFromClassName(args[0], additionalModules);

		// check for parameters in args
		Map<String, String> providedParams = new HashMap<String, String>();
		for (int i = 1; i < args.length; i++) {
			if (Pattern.matches("([a-zA-Z0-9_]+)=([a-zA-Z0-9_.,])+$", args[i])) {
				String[] pieces = args[i].split("=", 2);
				providedParams.put(pieces[0], pieces[1]);
			}
		}

		// set parameters
		sim.setParameters(providedParams);

		// go!
		sim.load();
		sim.run();

	}

	final public static void runSimulationID(long simID, int threads)
			throws Exception {
		// Additional modules we want for this simulation run
		Set<AbstractModule> additionalModules = new HashSet<AbstractModule>();
		additionalModules.add(new EventBusModule());

		DatabaseModule db = DatabaseModule.load();
		additionalModules.add(db);

		RunnableSimulation run;
		try {
			run = newFromStorage(simID, db, additionalModules);
		} catch (Exception e) {
			throw e;
		}

		// now run
		run.load();
		run.run();
	}

	public static RunnableSimulation newFromStorage(long simID,
			DatabaseModule db, Set<AbstractModule> additionalModules)
			throws Exception {
		// db connect and get info we need
		Injector injector = Guice.createInjector(db);
		DatabaseService database = injector.getInstance(DatabaseService.class);
		StorageService storage = injector.getInstance(StorageService.class);
		database.start();
		// get PersistentSimulation
		PersistentSimulation sim = storage.getSimulationById(simID);
		if (sim == null) {
			database.stop();
			throw new RuntimeException("Simulation with ID " + simID
					+ " not found in storage. Aborting.");
		}
		if (!sim.getState().equalsIgnoreCase("NOT STARTED")
				&& !sim.getState().equalsIgnoreCase("AUTO START")) {
			database.stop();
			throw new RuntimeException("Simulation " + simID
					+ " has already been started. Aborting.");
		}

		RunnableSimulation run = newFromClassName(sim.getClassName(),
				additionalModules);
		try {
			run.setParameters(sim.getParameters());
		} catch (IllegalArgumentException e) {
			sim.setState("FAILED");
			database.stop();
			throw new RuntimeException(e);
		} catch (UndefinedParameterException e) {
			sim.setState("FAILED");
			database.stop();
			throw new RuntimeException(e);
		}
		run.id = simID;
		database.stop();
		return run;
	}

	private static class ObjectFactory {

		@SuppressWarnings("unchecked")
		static <T> Constructor<? extends T> getConstructor(
				final Class<T> clazz, Class<?>... paramTypes)
				throws NoSuchMethodException {

			for (Constructor<?> ctor : clazz.getConstructors()) {
				Class<?>[] ctorParams = ctor.getParameterTypes();

				if (ctorParams.length == paramTypes.length) {
					boolean match = true;
					for (int i = 0; i < ctorParams.length; i++) {
						try {
							paramTypes[i].asSubclass(ctorParams[i]);
						} catch (ClassCastException e) {
							match = false;
							break;
						}
					}
					if (match)
						return (Constructor<? extends T>) ctor;
				}
			}
			throw new NoSuchMethodException(
					"Could not find constructor to match parameters for "
							+ clazz.getSimpleName());
		}

	}

}
