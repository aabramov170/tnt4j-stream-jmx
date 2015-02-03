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

import java.io.IOException;

import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.Condition;
import org.tnt4j.pingjmx.conditions.ConditionalListener;

import com.nastel.jkool.tnt4j.TrackingLogger;

/**
 * <p>
 * This interface provides a way to implement classes that implement 
 * scheduled ping/heart-beat for a given JMX <code>MBeanServer</code>.
 * </p>
 * 
 * @version $Revision: 1 $
 */
public interface PingScheduler extends Runnable {
	/**
	 * Name associated with this object
	 * 
	 * @return object name
	 */
	String getName();

	/**
	 * Sampling period in milliseconds
	 * 
	 * @return Sampling period in milliseconds
	 */
	long getPeriod();

	/**
	 * Open current scheduled activity instance.
	 * @throws IOException 
	 * 
	 */
	void open() throws IOException;

	/**
	 * Close current scheduled activity instance.
	 * 
	 */
	void close();

	/**
	 * Register a condition/action pair which will be evaluated every sampling
	 * interval.
	 *
	 * @param cond
	 *            user defined condition
	 * @param action
	 *            user defined action
	 * 
	 */
	void register(Condition cond, AttributeAction action);

	/**
	 * Obtain conditional listener instance which is triggered on every sample.
	 *
	 * @return conditional listener instance
	 */
	ConditionalListener getConditionalListener();
	
	/**
	 * MBean filter associated with this pinger
	 * 
	 * @return filter list
	 */
	String getFilter();
	
	/**
	 * Obtain <code>TrackingLogger</code> instance for logging
	 * 
	 * @return tracking logger instance
	 */
	TrackingLogger getLogger();
}
