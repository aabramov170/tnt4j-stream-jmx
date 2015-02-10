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

import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.AttributeCondition;
import org.tnt4j.pingjmx.conditions.SampleHandler;
import org.tnt4j.pingjmx.core.Pinger;

import com.nastel.jkool.tnt4j.ActivityScheduler;

/**
 * <p>
 * This class provides scheduled ping/heart-beat for a given JMX
 * <code>MBeanServer</code>.
 * </p>
 * 
 * @version $Revision: 1 $
 */
public class PingSchedulerImpl extends ActivityScheduler implements PingScheduler {
	protected SampleHandler listener;
	protected long period;
	protected TimeUnit timeUnit;
	protected String filter;

	/**
	 * Create new instance of <code>PingSchedulerImpl</code> with a given name, MBean
	 * server, sampling period. Filter is set to all MBeans.
	 *
	 * @param name
	 *            name of assigned to the pinger
	 * @param server
	 *            MBean server instance
	 * @param period
	 *            sampling period in milliseconds
	 * 
	 */
	public PingSchedulerImpl(String name, MBeanServer server, long period) {
		this(name, server, Pinger.JMX_FILTER_ALL, period);
	}

	/**
	 * Create new instance of <code>PingSchedulerImpl</code> with a given name, MBean
	 * server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the pinger
	 * @param server
	 *            MBean server instance
	 * @param filterList
	 *            MBean filters semicolon separated
	 * @param period
	 *            sampling period in milliseconds
	 * 
	 */
	public PingSchedulerImpl(String name, MBeanServer server, String filterList,
			long period) {
		this(name, server, filterList, period, TimeUnit.MILLISECONDS);
	}

	/**
	 * Create new instance of <code>PingSchedulerImpl</code> with a given name, MBean
	 * server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the pinger
	 * @param server
	 *            MBean server instance
	 * @param filterList
	 *            MBean filters semicolon separated
	 * @param period
	 *            sampling period
	 * @param tunit
	 *            time unit for the sampling period
	 * 
	 */
	public PingSchedulerImpl(String name, MBeanServer server, String filterList,
			long period, TimeUnit tunit) {
		super(name, newSampleHandlerImpl(server, filterList));
		this.listener = (SampleHandler) this.getListener();
		this.period = period;
		this.timeUnit = tunit;
		this.filter = filterList;
	}

	/**
	 * Create new instance of <code>SampleHandler</code> Override this call
	 * to return your instance of the sample handler implementation.
	 *
	 * @param server
	 *            MBean server instance
	 * @param filterList
	 *            MBean filters semicolon separated
	 * 
	 * @see SampleHandler
	 * @return new sample handler implementation instance
	 */
	protected static SampleHandler newSampleHandlerImpl(MBeanServer server,
			String filterList) {
		return new PingSampleHandlerImpl(server, filterList);
	}

	@Override
	public void register(AttributeCondition cond, AttributeAction action) {
		listener.register(cond, action);
	}

	@Override
	public SampleHandler getSampleHandler() {
		return listener;
	}

	@Override
	public String getFilter() {
		return filter;
	}
	
	@Override
	public long getPeriod() {
		return TimeUnit.MILLISECONDS.convert(period, timeUnit);
	}
	
	@Override
	public void run() {
		this.schedule(this.getName(), period, timeUnit);
	}	
}