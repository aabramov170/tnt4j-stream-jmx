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

import java.util.HashSet;
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
	public static String STAT_MBEAN_COUNT = "mbean.count";
	public static String STAT_CONDITION_COUNT = "condition.count";
	public static String STAT_LISTENER_COUNT = "listener.count";
	public static String STAT_EXCLUDE_COUNT = "exclude.metric.count";
	public static String STAT_TOTAL_ACTION_COUNT = "total.action.count";
	public static String STAT_TOTAL_METRIC_COUNT = "total.metric.count";
	public static String STAT_LAST_METRIC_COUNT = "last.metric.count";
	public static String STAT_SAMPLE_TIME_USEC = "sample.time.usec";
	
	private final ReentrantLock lock = new ReentrantLock();
	
	String mbeanIncFilter, mbeanExcFilter;
	long sampleCount = 0, totalMetricCount = 0, totalActionCount = 0;
	long lastMetricCount = 0, lastSampleTimeUsec = 0, noopCount = 0;
	
	MBeanServer mbeanServer;
	SampleContext context;
	Throwable lastError;
	
	MBeanServerNotificationFilter MBeanFilter = new MBeanServerNotificationFilter(); 
	Map<AttributeCondition, AttributeAction> conditions = new LinkedHashMap<AttributeCondition, AttributeAction>(89);
	ConcurrentHashMap<ObjectName, MBeanInfo> mbeans = new ConcurrentHashMap<ObjectName, MBeanInfo>(89);
	HashSet<ObjectName> excAttrs = new HashSet<ObjectName>(89);

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

	
	private static void  tokenizeFilters(String filter, List<ObjectName> filters) throws MalformedObjectNameException {
		StringTokenizer itk = new StringTokenizer(filter, ";");
		while (itk.hasMoreTokens()) {
			filters.add(new ObjectName(itk.nextToken()));
		}		
	}
	
	private void listenForChanges(List<ObjectName> filters) throws InstanceNotFoundException {
		MBeanFilter.enableAllObjectNames();
		mbeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this, MBeanFilter, null);
	}
	
	/**
	 * Load JMX beans based on a configured MBean filter list.
	 * All loaded MBeans are stored in {@code HashMap}.
	 */
	private void loadMBeans() {
		try {
			Vector<ObjectName> nFilters = new Vector<ObjectName>(5, 5);
			Vector<ObjectName> eFilters = new Vector<ObjectName>(5, 5);

			tokenizeFilters(mbeanIncFilter, nFilters);
			if (mbeanExcFilter != null && mbeanExcFilter.trim().length() > 0) {
				tokenizeFilters(mbeanExcFilter, eFilters);
				listenForChanges(nFilters);
			}
			
			// run inclusion
			for (ObjectName nameFilter : nFilters) {
				Set<ObjectName> set = mbeanServer.queryNames(nameFilter, nameFilter);
				if (eFilters.size() > 0) {
					excludeFromSet(set, eFilters);
				}
				for (Iterator<ObjectName> it = set.iterator(); it.hasNext();) {
					ObjectName oname = it.next();
					mbeans.put(oname, mbeanServer.getMBeanInfo(oname));
				}
			}
		} catch (Exception ex) {
			lastError = ex;
			ex.printStackTrace();
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
	 * Sample  MBeans based on a configured MBean filter list
	 * and store within given activity as snapshots.
	 * 
	 * @param activity instance where sampled MBean attributes are stored
	 * @return number of metrics loaded from all MBeans
	 */
	private int sampleMBeans(Activity activity) {
		int pCount = 0;
		for (Entry<ObjectName, MBeanInfo> entry: mbeans.entrySet()) {
			ObjectName name = entry.getKey();
			MBeanInfo info = entry.getValue();
			MBeanAttributeInfo[] attr = info.getAttributes();
			
			PropertySnapshot snapshot = new PropertySnapshot(name.getDomain(), name.getCanonicalName());
			for (int i = 0; i < attr.length; i++) {
				MBeanAttributeInfo jinfo = attr[i];
				if (jinfo.isReadable() && !isExcluded(name)) {
					AttributeSample sample = new AttributeSample(activity, mbeanServer, name, jinfo);
					try {
						sample.sample(); // obtain a sample
						if (doSample(sample)) {
							processJmxValue(snapshot, jinfo, jinfo.getName(), sample.get());
						}
					} catch (Throwable ex) {
						lastError = ex;
						exclude(name);
						doError(sample, ex);
					} finally {
						evalAttrConditions(sample);						
					}
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
	 * Determine if a given attribute to be excluded from sampling.	
	 * 
	 * @param oname attribute object name
	 * @return true when attribute should be excluded, false otherwise
	 */
	private boolean isExcluded(ObjectName oname) {
	    return excAttrs.contains(oname);
    }

	/**
	 * Mark a given attribute to be excluded from sampling.	
	 * 
	 * @param oname attribute object name
	 */
	private void exclude(ObjectName oname) {
	    excAttrs.add(oname);
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
	 * @return snapshot instance containing finish stats
	 */
	private PropertySnapshot finish(Activity activity) {
		PropertySnapshot snapshot = new PropertySnapshot(activity.getName(), "SampleContext");
		snapshot.add(STAT_NOOP_COUNT, noopCount);
		snapshot.add(STAT_SAMPLE_COUNT, sampleCount);
		snapshot.add(STAT_MBEAN_COUNT, mbeans.size());
		snapshot.add(STAT_CONDITION_COUNT, conditions.size());
		snapshot.add(STAT_LISTENER_COUNT, listeners.size());
		snapshot.add(STAT_EXCLUDE_COUNT, excAttrs.size());
		snapshot.add(STAT_TOTAL_ACTION_COUNT, totalActionCount);
		snapshot.add(STAT_TOTAL_METRIC_COUNT, totalMetricCount);
		snapshot.add(STAT_LAST_METRIC_COUNT, lastMetricCount);
		snapshot.add(STAT_SAMPLE_TIME_USEC, lastSampleTimeUsec);
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
			lastError = null;
			return context;
		} finally {
			lock.unlock();
		}
    }

	private void runPost(Activity activity) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.post(context, activity);
			}
		} 
	}

	private void runPre(Activity activity) {
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.pre(context, activity);
			}
		} 
	}

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

	private void doError(AttributeSample sample, Throwable ex) {
		sample.setError(ex);
		synchronized (this.listeners) {
			for (SampleListener lst: listeners) {
				lst.error(context, sample);
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
	                mbeans.put(mbeanEvent.getMBeanName(), mbeanServer.getMBeanInfo(mbeanEvent.getMBeanName()));
                } catch (Throwable e) {
                	e.printStackTrace();
                }
			} else if (mbeanEvent.getType().equalsIgnoreCase(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
				mbeans.remove(mbeanEvent.getMBeanName());
			}
		}
	}

}