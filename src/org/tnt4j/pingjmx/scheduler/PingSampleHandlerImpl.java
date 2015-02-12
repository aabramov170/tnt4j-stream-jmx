/*
 * Copyright 2015 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx.scheduler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.AttributeCondition;
import org.tnt4j.pingjmx.conditions.AttributeSample;
import org.tnt4j.pingjmx.conditions.NoopAction;
import org.tnt4j.pingjmx.conditions.SampleHandler;
import org.tnt4j.pingjmx.core.SampleContext;
import org.tnt4j.pingjmx.core.SampleListener;
import org.tnt4j.pingjmx.core.UnsupportedAttributeException;

import com.nastel.jkool.tnt4j.core.Activity;
import com.nastel.jkool.tnt4j.core.PropertySnapshot;

/**
 * <p> 
 * This class provides implementation for handling ping/heart-beats
 * generated by <code>PingScheduler</code> class, which handles
 * implementation of all metric collection for each sample.
 * </p>
 * 
 * @see PingScheduler
 * @see AttributeSample
 * 
 * @version $Revision: 1 $
 */
public class PingSampleHandlerImpl implements SampleHandler {
	String mbeanFilter;
	long sampleCount = 0, totalMetricCount = 0;
	long lastMetricCount = 0, noopCount = 0;
	
	MBeanServer mbeanServer;
	SampleContext context;
	Throwable lastError;
	
	Vector<SampleListener> listeners = new Vector<SampleListener>(5, 5);
	Map<AttributeCondition, AttributeAction> conditions = new LinkedHashMap<AttributeCondition, AttributeAction>(89);
	HashMap<ObjectName, MBeanInfo> mbeans = new HashMap<ObjectName, MBeanInfo>(89);
	HashMap<MBeanAttributeInfo, MBeanAttributeInfo> excAttrs = new HashMap<MBeanAttributeInfo, MBeanAttributeInfo>(89);

	/**
	 * Create new instance of <code>PingSampleHandlerImpl</code> with a given
	 * MBean server and a set of filters.
	 *
	 * @param mserver MBean server instance
	 * @param filter MBean filters semicolon separated
	 *  
	 */
	public PingSampleHandlerImpl(MBeanServer mserver, String filter) {
		mbeanServer = mserver;
		mbeanFilter = filter;
		context = new PingSampleContextImpl(this);
	}

	/**
	 * Load JMX beans based on a configured MBean filter list.
	 * All loaded MBeans are stored in <code>HashMap</code>.
	 */
	private void loadMBeans() {
		try {
			StringTokenizer tk = new StringTokenizer(mbeanFilter, ";");
			Vector<ObjectName> nFilters = new Vector<ObjectName>(5);
			while (tk.hasMoreTokens()) {
				nFilters.add(new ObjectName(tk.nextToken()));
			}
			for (ObjectName nameFilter : nFilters) {
				Set<?> set = mbeanServer.queryNames(nameFilter, nameFilter);
				for (Iterator<?> it = set.iterator(); it.hasNext();) {
					ObjectName oname = (ObjectName) it.next();
					mbeans.put(oname, mbeanServer.getMBeanInfo(oname));
				}
			}
		} catch (Exception ex) {
			lastError = ex;
			ex.printStackTrace();
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
				if (jinfo.isReadable() && !attrExcluded(jinfo)) {
					AttributeSample sample = new AttributeSample(activity, mbeanServer, name, jinfo);
					try {
						sample.sample(); // obtain a sample
						if (doSample(sample)) {
							processJmxValue(snapshot, jinfo, jinfo.getName(), sample.get());
						}
					} catch (Throwable ex) {
						lastError = ex;
						exclude(jinfo);
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
	 * associated <code>MBeanAction</code> instances.
	 * 
	 * @param sample MBean sample instance
	 * @see AttributeSample
	 */
	protected void evalAttrConditions(AttributeSample sample) {
		for (Map.Entry<AttributeCondition, AttributeAction> entry: conditions.entrySet()) {
			if (entry.getKey().evaluate(sample)) {
				entry.getValue().action(context, entry.getKey(), sample);
			}
		}
	}

	/**
	 * Determine if a given attribute to be excluded from sampling.	
	 * 
	 * @param jinfo attribute info
	 * @return true when attribute should be excluded, false otherwise
	 */
	private boolean attrExcluded(MBeanAttributeInfo jinfo) {
	    return excAttrs.get(jinfo) != null;
    }

	/**
	 * Mark a given attribute to be excluded from sampling.	
	 * 
	 * @param jinfo attribute info
	 */
	private void exclude(MBeanAttributeInfo jinfo) {
	    excAttrs.put(jinfo, jinfo);
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
	 */
	private int finish(Activity activity) {
		PropertySnapshot snapshot = new PropertySnapshot(activity.getName(), "SampleContext");
		snapshot.add("sample.count", sampleCount);
		snapshot.add("noop.count", noopCount);
		snapshot.add("sample.metric.count", lastMetricCount);
		snapshot.add("total.metric.count", totalMetricCount);
		activity.addSnapshot(snapshot);		
		return snapshot.size();
	}
	
	@Override
	public void started(Activity activity) {
		lastError = null; // reset last sample error
		runPre(activity);
		if ((!activity.isNoop()) && (mbeans.size() == 0)) {
			loadMBeans();
		} else if (activity.isNoop()) {
			noopCount++;
		}
	}

	@Override
	public void stopped(Activity activity) {
		if (!activity.isNoop()) {
			sampleCount++;
			lastMetricCount = sampleMBeans(activity);		
			lastMetricCount += finish(activity);		
			totalMetricCount += lastMetricCount;
			runPost(activity);		
			if (activity.isNoop()) {
				noopCount++;
			}
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
	public SampleContext getStats() {
		return context;
	}
}

class PingSampleContextImpl implements SampleContext {
	PingSampleHandlerImpl handle;
	
	PingSampleContextImpl(PingSampleHandlerImpl lst) {
		handle = lst;
	}
	
	@Override
	public MBeanServer getMBeanServer() {
		return handle.mbeanServer;
	}

	@Override
	public long getSampleCount() {
		return handle.sampleCount;
	}

	@Override
	public long getMBeanCount() {
		return handle.mbeans.size();
	}

	@Override
	public long getExclAttrCount() {
		return handle.excAttrs.size();
	}

	@Override
	public long getTotalMetricCount() {
		return handle.totalMetricCount;
	}

	@Override
	public long getLastMetricCount() {
		return handle.lastMetricCount;
	}

	@Override
	public long getTotalNoopCount() {
		return handle.noopCount;
	}

	@Override
    public Throwable getLastError() {
		return handle.lastError;
    }
}