/*
 * Copyright 2015 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tnt4j.stream.jmx.scheduler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.relation.MBeanServerNotificationFilter;

import org.tnt4j.stream.jmx.conditions.AttributeAction;
import org.tnt4j.stream.jmx.conditions.AttributeCondition;
import org.tnt4j.stream.jmx.conditions.AttributeSample;
import org.tnt4j.stream.jmx.conditions.NoopAction;
import org.tnt4j.stream.jmx.conditions.SampleHandler;
import org.tnt4j.stream.jmx.core.SampleContext;
import org.tnt4j.stream.jmx.core.SampleListener;
import org.tnt4j.stream.jmx.core.UnsupportedAttributeException;

import com.nastel.jkool.tnt4j.core.Activity;
import com.nastel.jkool.tnt4j.core.PropertySnapshot;

/**
 * <p> 
 * This class provides implementation for handling sample/heart-beats
 * generated by {@link Scheduler} class, which handles
 * implementation of all metric collection for each sample.
 * </p>
 * 
 * @see Scheduler
 * @see SampleHandler
 * @see AttributeSample
 * @see AttributeCondition
 * 
 * @version $Revision: 1 $
 */
public class SampleHandlerImpl implements SampleHandler, NotificationListener {
	public static String STAT_NOOP_COUNT = "noop.count";
	public static String STAT_SAMPLE_COUNT = "sample.count";
	public static String STAT_TOTAL_ERROR_COUNT = "total.error.count";
	public static String STAT_TOTAL_EXCLUDE_COUNT = "total.exclude.count";
	public static String STAT_MBEAN_COUNT = "mbean.count";
	public static String STAT_CONDITION_COUNT = "condition.count";
	public static String STAT_LISTENER_COUNT = "listener.count";
	public static String STAT_TOTAL_ACTION_COUNT = "total.action.count";
	public static String STAT_TOTAL_METRIC_COUNT = "total.metric.count";
	public static String STAT_LAST_METRIC_COUNT = "last.metric.count";
	public static String STAT_SAMPLE_TIME_USEC = "sample.time.usec";
	
	private final ReentrantLock lock = new ReentrantLock();
	
	String mbeanIncFilter, mbeanExcFilter;
	long sampleCount = 0, totalMetricCount = 0, totalActionCount = 0;
	long lastMetricCount = 0, lastSampleTimeUsec = 0;
	long noopCount = 0, excCount = 0, errorCount = 0;
	
	MBeanServer mbeanServer;
	SampleContext context;
	Throwable lastError;
	
	MBeanServerNotificationFilter MBeanFilter; 
	Vector<ObjectName> iFilters = new Vector<ObjectName>(5, 5), eFilters = new Vector<ObjectName>(5, 5);
	Map<AttributeCondition, AttributeAction> conditions = new LinkedHashMap<AttributeCondition, AttributeAction>(89);
	ConcurrentHashMap<ObjectName, MBeanInfo> mbeans = new ConcurrentHashMap<ObjectName, MBeanInfo>(89);

	Vector<SampleListener> listeners = new Vector<SampleListener>(5, 5);

	/**
	 * Create new instance of {@code SampleHandlerImpl} with a given
	 * MBean server and a set of filters.
	 *
	 * @param mserver MBean server instance
	 * @param incFilter MBean include filters semicolon separated
	 * @param excFilter MBean exclude filters semicolon separated
	 *  
	 */
	public SampleHandlerImpl(MBeanServer mserver, String incFilter, String excFilter) {
		mbeanServer = mserver;
		mbeanIncFilter = incFilter;
		mbeanExcFilter = excFilter;
		context = new SampleContextImpl(this);
	}

	
	/**
	 * Tokenize a given set of filters into JMX object names
	 * 
	 * @param filter semicolon set of JMX filters
	 * @param tokenized list of object names
	 * @throws MalformedObjectNameException 
	 */
	private static void  tokenizeFilters(String filter, List<ObjectName> filters) throws MalformedObjectNameException {
		StringTokenizer itk = new StringTokenizer(filter, ";");
		while (itk.hasMoreTokens()) {
			filters.add(new ObjectName(itk.nextToken()));
		}		
	}
	
	/**
	 * Install MBean add/delete listener
	 * @throws InstanceNotFoundException 
	 */
	private void listenForChanges() throws InstanceNotFoundException  {
		if (MBeanFilter == null) {
			MBeanFilter = new MBeanServerNotificationFilter();
			MBeanFilter.enableAllObjectNames();
			mbeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this, MBeanFilter, null);
		}
	}
	

	/**
	 * Determine if a given object name matches include/exclusion filters
	 * 
	 * @param oname object name
	 * @return true if included, false otherwise
	 */
	public boolean isFilterIncluded(ObjectName oname) {
		for (ObjectName eFilter : eFilters) {
			if (eFilter.apply(oname)) {
				return false;
			}
		}
		for (ObjectName incFilter : iFilters) {
			if (incFilter.apply(oname)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Load JMX beans based on a configured MBean filter list.
	 * All loaded MBeans are stored in {@code HashMap}.
	 */
	private void loadMBeans() {
		try {
			tokenizeFilters(mbeanIncFilter, iFilters);
			if (mbeanExcFilter != null && mbeanExcFilter.trim().length() > 0) {
				tokenizeFilters(mbeanExcFilter, eFilters);
			}
			listenForChanges();			
			
			// run inclusion
			for (ObjectName nameFilter : iFilters) {
				Set<ObjectName> set = mbeanServer.queryNames(nameFilter, nameFilter);
				if (eFilters.size() > 0) {
					excludeFromSet(set, eFilters);
				}
				for (Iterator<ObjectName> it = set.iterator(); it.hasNext();) {
					ObjectName oname = it.next();
					mbeans.put(oname, mbeanServer.getMBeanInfo(oname));
					runRegister(oname);
				}
			}
		} catch (Exception ex) {
			lastError = ex;
			doError(ex);
		}
	}

	/**
	 * Exclude MBeans based on a list of exclude object name patterns
	 * 
	 * @param objSet JMX object name set
	 * @param eFilters list of MBean exclusions
	 * @return the set without objects excluded by eFilters list
	 */
	private void excludeFromSet(Set<ObjectName> objSet, List<ObjectName> eFilters) {
		for (ObjectName ename: eFilters) {
			Iterator<ObjectName> it = objSet.iterator();
			while (it.hasNext()) {
				ObjectName name = it.next();
				if (ename.apply(name)) {
					it.remove();
				}
			}
		}
	}

	/**
	 * Sample MBeans based on a configured MBean filter list and store within given activity as snapshots.
	 * 
	 * @param activity
	 *            instance where sampled MBean attributes are stored
	 * @return number of metrics loaded from all MBeans
	 */
	private int sampleMBeans(Activity activity) {
		int pCount = 0;
		for (Entry<ObjectName, MBeanInfo> entry : mbeans.entrySet()) {
			ObjectName name = entry.getKey();
			MBeanInfo info = entry.getValue();
			MBeanAttributeInfo[] attr = info.getAttributes();

			PropertySnapshot snapshot = new PropertySnapshot(name.getDomain(), name.getCanonicalName());
			for (int i = 0; i < attr.length; i++) {
				MBeanAttributeInfo jinfo = attr[i];
				AttributeSample sample = new AttributeSample(activity, mbeanServer, name, jinfo);
				try {
					if (doSample(sample)) {
						sample.sample(); // obtain a sample
						processJmxValue(snapshot, jinfo, jinfo.getName(), sample.get());
					}
				} catch (Throwable ex) {
					errorCount++;
					lastError = ex;
					doError(sample, ex);
				} finally {
					if (sample.excludeNext()) {
						excCount++;
					}
					evalAttrConditions(sample);
				}
			}
			if (snapshot.size() > 0) {
				activity.addSnapshot(snapshot);
				pCount += snapshot.size();
			}
		}
		return pCount;
	}

	/**
	 * Run and evaluate all registered conditions and invoke
	 * associated {@code MBeanAction} instances.
	 * 
	 * @param sample MBean sample instance
	 * @see AttributeSample
	 */
	protected void evalAttrConditions(AttributeSample sample) {
		for (Map.Entry<AttributeCondition, AttributeAction> entry: conditions.entrySet()) {
			if (entry.getKey().evaluate(sample)) {
				totalActionCount++;
				entry.getValue().action(context, entry.getKey(), sample);
			}
		}
	}

	/**
	 * Process/extract value from a given MBean attribute
	 * 
	 * @param snapshot instance where extracted attribute is stored
	 * @param jinfo attribute info
	 * @param property name to be assigned to given attribute value
	 * @param value associated with attribute
	 * @throws UnsupportedAttributeException if provided attribute not supported
	 * @return snapshot instance where all attributes are contained
	 */
	private PropertySnapshot processJmxValue(PropertySnapshot snapshot, MBeanAttributeInfo jinfo, String propName, Object value) throws UnsupportedAttributeException {
		if (value != null && !value.getClass().isArray()) {
			if (value instanceof CompositeData) {
				CompositeData cdata = (CompositeData) value;
				Set<String> keys = cdata.getCompositeType().keySet();
				for (String key: keys) {
					Object cval = cdata.get(key);
					processJmxValue(snapshot, jinfo, propName + "\\" + key, cval);
				}
			} else if (typeSupported(value)) {
				snapshot.add(propName, value);
			} else {
				throw new UnsupportedAttributeException("Unsupported type=" + value.getClass(), jinfo, value);
			}
		}
		return snapshot;
	}
	
	/**
	 * Determine if a given value and its type are supported
	 * 
	 * @param value value to test for support
	 * @return true if a given value and its type are supported, false otherwise
	 */
	protected boolean typeSupported(Object value) {
		 return (value.getClass().isPrimitive() || (value instanceof String) || (value instanceof Number) || (value instanceof Boolean));
	}
	
	/**
	 * Finish processing of the activity sampling
	 * 
	 * @param activity instance
	 * @return snapshot instance containing metrics at the end of each sample
	 */
	private PropertySnapshot finish(Activity activity) {
		PropertySnapshot snapshot = new PropertySnapshot(activity.getName(), "SampleContext");
		snapshot.add(STAT_NOOP_COUNT, noopCount);
		snapshot.add(STAT_SAMPLE_COUNT, sampleCount);
		snapshot.add(STAT_TOTAL_ERROR_COUNT, errorCount);
		snapshot.add(STAT_TOTAL_EXCLUDE_COUNT, excCount);
		snapshot.add(STAT_MBEAN_COUNT, mbeans.size());
		snapshot.add(STAT_CONDITION_COUNT, conditions.size());
		snapshot.add(STAT_LISTENER_COUNT, listeners.size());
		snapshot.add(STAT_TOTAL_ACTION_COUNT, totalActionCount);
		snapshot.add(STAT_TOTAL_METRIC_COUNT, totalMetricCount);
		snapshot.add(STAT_LAST_METRIC_COUNT, lastMetricCount);
		snapshot.add(STAT_SAMPLE_TIME_USEC, lastSampleTimeUsec);
		
		// get custom statistics
		Map<String, Object> stats = new HashMap<String, Object>();
		doStats(stats);
		snapshot.addAll(stats);
		
		activity.addSnapshot(snapshot);		
		return snapshot;
	}
	
	@Override
	public void started(Activity activity) {
		lock.lock();
		try {
			lastError = null; // reset last sample error
			runPre(activity);
			if ((!activity.isNoop()) && (mbeans.size() == 0)) {
				loadMBeans();
			} else if (activity.isNoop()) {
				noopCount++;
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void stopped(Activity activity) {
		if (!activity.isNoop()) {
			lock.lock();
			try {
				long started = System.nanoTime();
				sampleCount++;
				lastMetricCount = sampleMBeans(activity);		
				totalMetricCount += lastMetricCount;			
				lastSampleTimeUsec = (System.nanoTime() - started)/1000;
				
				// run post listeners
				runPost(activity);		
				if (activity.isNoop()) {
					noopCount++;
				}
				// compute sampling statistics
				finish(activity);
			} catch (Throwable ex) {
				doError(ex);
			} finally {
				lock.unlock();
			}
		}
	}

	/**
	 * Reset all counters maintained by sampling handler
	 * 
	 * @return instance to the sampling context
	 */
	public SampleContext resetCounters() {
		lock.lock();
		try {
			sampleCount = 0;
			totalMetricCount = 0;
			totalActionCount = 0;
			lastMetricCount = 0;
			lastSampleTimeUsec = 0;
			noopCount = 0;
			excCount = 0;
			errorCount = 0;
			lastError = null;
			return context;
		} finally {
			lock.unlock();
		}
    }

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#registerMBean(SampleContext, ObjectName)}
	 * for all registered listeners.
	 * 
	 * @param name MBean object name
	 */
	private void runRegister(ObjectName name) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.registerMBean(context, name);
			}
		} 
	}

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#unregisterMBean(SampleContext, ObjectName)}
	 * for all registered listeners.
	 * 
	 * @param name MBean object name
	 */
	private void runUnRegister(ObjectName name) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.unregisterMBean(context, name);
			}
		} 
	}

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#post(SampleContext, Activity)}
	 * for all registered listeners.
	 * 
	 * @param activity sampling activity instance
	 */
	private void runPost(Activity activity) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.post(context, activity);
			}
		} 
	}

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#pre(SampleContext, Activity)}
	 * for all registered listeners.
	 * 
	 * @param activity sampling activity instance
	 */
	private void runPre(Activity activity) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.pre(context, activity);
			}
		} 
	}

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#sample(SampleContext, AttributeSample)}
	 * for all registered listeners.
	 * 
	 * @param sample current attribute sample instance
	 */
	private boolean doSample(AttributeSample sample) {
		boolean result = true;
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				boolean last = lst.sample(context, sample);
				result = result && last;
			}
		} 
		return result;
	}

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#error(SampleContext, AttributeSample)}
	 * for all registered listeners.
	 * 
	 * @param sample current attribute sample instance
	 * @param ex exception associated with the error
	 */
	private void doError(AttributeSample sample, Throwable ex) {
		sample.setError(ex);
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.error(context, sample);
			}
		} 
	}

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#error(SampleContext, Throwable)}
	 * for all registered listeners.
	 * 
	 * @param ex exception associated with the error
	 */
	private void doError(Throwable ex) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.error(context, ex);
			}
		} 
	}

	/**
	 * Run {@link org.tnt4j.stream.jmx.core.SampleListener#getStats(SampleContext, Map)}
	 * for all registered listeners.
	 * 
	 * @param stats map of key/value statistics
	 */
	private void doStats(Map<String, Object> stats) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.getStats(context, stats);
			}
		} 
	}

	@Override
	public SampleHandler register(AttributeCondition cond, AttributeAction action) {
		conditions.put(cond, (action == null? NoopAction.NOOP: action));
		return this;
	}

	@Override
	public SampleHandler register(AttributeCondition cond) {
		register(cond, NoopAction.NOOP);
		return this;
	}


	@Override
	public SampleHandler addListener(SampleListener listener) {
		listeners.add(listener);
		return this;
	}

	@Override
	public SampleHandler removeListener(SampleListener listener) {
		listeners.remove(listener);
		return this;
	}

	@Override
	public SampleContext getContext() {
		return context;
	}


	@Override
    public void handleNotification(Notification notification, Object handback) {
		if (notification instanceof MBeanServerNotification) {
			MBeanServerNotification mbeanEvent = (MBeanServerNotification) notification;
			if (mbeanEvent.getType().equalsIgnoreCase(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
				try {
					if (isFilterIncluded(mbeanEvent.getMBeanName())) {
						mbeans.put(mbeanEvent.getMBeanName(), mbeanServer.getMBeanInfo(mbeanEvent.getMBeanName()));
						runRegister(mbeanEvent.getMBeanName());
					}
                } catch (Throwable ex) {
                	doError(ex);
                }
			} else if (mbeanEvent.getType().equalsIgnoreCase(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
				mbeans.remove(mbeanEvent.getMBeanName());
				runUnRegister(mbeanEvent.getMBeanName());
			}
		}
	}

}