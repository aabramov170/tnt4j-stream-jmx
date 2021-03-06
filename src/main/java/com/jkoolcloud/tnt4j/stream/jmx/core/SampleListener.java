/*
 * Copyright 2015-2017 JKOOL, LLC.
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
package com.jkoolcloud.tnt4j.stream.jmx.core;

import java.util.Map;

import javax.management.ObjectName;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.NestedHandler;

import com.jkoolcloud.tnt4j.core.Activity;

/**
 * <p> 
 * This interface provides a way to get call backs on every sample:pre, during, post
 * each sample.
 * </p>
 * 
 * @see Sampler
 * @see NestedHandler
 * @see SampleContext
 * 
 * @version $Revision: 1 $
 */
public interface SampleListener {
	/**
	 * This method is called on a new MBean is found/registered
	 * 
	 * @param context current sample context
	 * @param oname MBean object name
	 */
	void register(SampleContext context, ObjectName oname);
	
	/**
	 * This method is called on a new MBean is removed/unregistered
	 * 
	 * @param context current sample context
	 * @param oname MBean object name
	 */
	void unregister(SampleContext context, ObjectName oname);
	
	/**
	 * This method is called before each attribute is sampled.
	 * Throw a runtime exception if you want all further samples to halt.
	 * Set {@link AttributeSample#excludeNext(boolean)} to true to skip 
	 * sampling this attribute.
	 * 
	 * @param context current sample context
	 * @param sample current attribute sample
	 */
	void pre(SampleContext context, AttributeSample sample);

	/**
	 * This method is called for after attribute is sampled.
	 * Throw a runtime exception if you want samples to halt.
	 * 
	 * @param context current sample context
	 * @param sample current attribute sample
	 * @throws UnsupportedAttributeException 
	 */
	void post(SampleContext context, AttributeSample sample) throws UnsupportedAttributeException;

	/**
	 * This method is called if sample fails with exception.
	 * 
	 * @param context current sample context
	 * @param sample current attribute sample
	 */
	void error(SampleContext context, AttributeSample sample);

	/**
	 * This method is called when generic error occurs
	 * 
	 * @param context current sample context
	 * @param ex exception instance
	 */
	void error(SampleContext context, Throwable ex);

	/**
	 * This method is called before a sample begins
	 * Set activity {@code OpType} to NOOP to ignore
	 * activity reporting. Setting this to NOOP at pre-stage
	 * cancels current sample.
	 * 
	 * @param context current sample context
	 * @param activity instance
	 */
	void pre(SampleContext context, Activity activity);
	
	/**
	 * This method is called after current sample is completed
	 * Set activity {@code OpType} to NOOP to ignore
	 * activity reporting. Setting this to NOOP at post stage
	 * cancels reporting of the current sample.
	 * 
	 * @param context current sample context
	 * @param activity instance
	 */
	void post(SampleContext context, Activity activity);
	
	/**
	 * This method is called to collect accumulated metrics maintained by a listener
	 * 
	 * @param context current sample context
	 * @param stats collection where all metrics are added
	 */
	void getStats(SampleContext context, Map<String, Object> stats);
}
