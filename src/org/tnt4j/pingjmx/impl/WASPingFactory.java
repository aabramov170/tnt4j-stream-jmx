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
package org.tnt4j.pingjmx.impl;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.core.Pinger;
import org.tnt4j.pingjmx.factory.PingFactory;

/**
 * <p> 
 * This class provides a <code>PingFactory</code> implementation
 * with <code>WASJmxPing</code> as underlying pinger implementation.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Pinger
 * @see WASJmxPing
 */
public class WASPingFactory implements PingFactory {
	
	@Override
	public Pinger newInstance() {
		return new WASJmxPing();
	}

	@Override
	public Pinger newInstance(MBeanServer mserver) {
		return new WASJmxPing(mserver);
	}
}
