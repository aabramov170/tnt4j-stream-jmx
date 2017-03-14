# Stream-JMX
A Lightweight framework to stream and monitor JMX metrics.
(Formerly known as PingJMX)

Stream JMX metrics to: 
* Central monitoring server
* File, socket, log4j
* User defined destination

These metrics can be used to monitor health, performance and availability of your JVMs and applications.
Use Stream-JMX to embed a monitoring agent within your application and monitor memory, GC activity, CPU as
well as user defined MBeans.

Here is what you can do with Stream-JMX:
* Periodic JVM heartbeat
* Monitor memory utilization, GC activity, memory leaks
* High/Low, normal vs. abnormal CPU usage
* Monitor threading, runtime and other JVM performance metrics
* Monitor standard and custom MBean attributes
* Conditional actions based on MBean attribute values
* Conditional streaming based on custom filters
* Application state dumps on VM shut-down for diagnostics

# Why Stream-JMX
Stream-JMX provides and easy, lightweight and secure way to stream and monitor JMX metrics from within
java runtime containers.

* Stream JMX metrics out of the JVM container (vs. polling from outside/remote)
* Makes it easy to monitor farms of JMVs, application servers
* Reduce cyber security risk: No need to enable remote JMX, SSL, security, ports, firewalls
* Integration with monitoring tools for alerting, pro-active monitoring (AutoPilot M6)
* Integration with cloud analytics tools (https://www.jkoolcloud.com via JESL)
* Integration with log4j, slf4j, jkoolcloud (via TNT4J event sinks)
* Embedded application state dump framework for diagnostics
* Easily build your own extensions, monitors

<b>NOTE:</b> JESL provides a way to stream events generated by Stream-JMX to jKool Cloud. 
For more information on JESL visit: http://nastel.github.io/JESL/

# Using Stream-JMX
It is simple, do one of the following: 
 * run Stream-JMX as a `-javaagent` 
 * attach Stream-JMX as agent to running JVM 
 * connect Streams-JMX over JMXConnector to locally running JVM or remote JMX service
 * embed Stream-JMX code into your application
 
**NOTE:** Running Stream-JMX as `-javaagent`, attaching agent to running JVM or connecting over JMXConnector to locally running JVM or 
remote JMX service over RMI connection can be invoked without changing your application code.
 
## Running Stream-JMX as `-javaagent`

### Command line to run
```cmd
java -javaagent:tnt4j-stream-jmx.jar="*:*!30000" -Dtnt4j.config=tnt4j.properties -classpath "tnt4j-stream-jmx.jar;lib/*" your.class.name your-args
```
The options are `-javaagent:tnt4j-stream-jmx.jar="mbean-filter!sample-time-ms"`, classpath must include tnt4j-stream-jmx jar files as well as locations of log4j and tnt4j configuration files.
 
## Attaching Stream-JMX to running JVM

### Command line to run
```cmd
java -Dtnt4j.config=.\config\tnt4j.properties -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.validate.types=false -classpath "tnt4j-stream-jmx.jar;lib/*" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -attach -vm:activemq -ap:tnt4j-stream-jmx-0.4.5.jar -ao:*:*!10000
```
System properties `-Dxxxxx` defines Stream-JMX configuration. For details see [Stream-JMX configuration ](#stream-jmx-configuration).
 
StreamAgent arguments `-attach -vm:activemq -ap:tnt4j-stream-jmx-0.4.5.jar -ao:*:*!10000` states:
* `-attach` - defines that StreamsAgent shall be attached to running JVM process
* `-vm:activemq` - is JVM descriptor. In this case it is running JVM name fragment `activemq`. But it also may be JVM process identifier - 
PID. Mandatory argument.
* `-ap:tnt4j-stream-jmx-0.4.5.jar` - is agent library name. If it is class path - then only name should be sufficient. In any other case 
define full or relative path i.e. `..\build\tnt4j-stream-jmx\tnt4j-stream-jmx-0.4.5\lib\tnt4j-stream-jmx-0.4.5.jar`. Mandatory argument.
* `-ao:*:*!10000` - is JMX sampler options stating to include all MBeans and schedule sampling every 30 seconds. Sampler options are 
optional - default value is `*:*!30000`.   

**NOTE:** arguments and properties defined running `StreamAgent.main` is forwarded to `StreamsAgent` agent attached to JVM process. 

## Connecting Stream-JMX to local or remote JMX service

### Command line to run

#### To connect to local JVM process
 
```cmd
java -Dtnt4j.config=.\config\tnt4j.properties -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.validate.types=false -classpath "tnt4j-stream-jmx.jar;lib/*" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:activemq -ao:*:*!*:dummy!10000
```

System properties `-Dxxxxx` defines Stream-JMX configuration. For details see [Stream-JMX configuration ](#stream-jmx-configuration).
 
StreamAgent arguments `-connect -vm:activemq -ao:*:*!*:dummy!10000` states:
* `-connect` - defines that StreamsAgent shall connect to running JVM process over JMXConnector (RMI) connection.
* `-vm:activemq` - is JVM descriptor. In this case it is running JVM name fragment `activemq`. But it also may be JVM process identifier - 
PID. Mandatory argument.
* `-ao:*:*!*:dummy!10000` - is JMX sampler options stating to include all MBeans, exclude all `dummy` MBeans and schedule sampling every 30 
seconds. Sampler options are optional - default value is `*:*!30000`.  

#### To connect to JMX service over URL
 
```cmd
java -Dtnt4j.config=.\config\tnt4j.properties -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.validate.types=false -classpath "tnt4j-stream-jmx.jar;lib/*" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -connect -vm:service:jmx:<JMX_URL> -ao:*:*!!10000
```
System properties `-Dxxxxx` defines Stream-JMX configuration. For details see [Stream-JMX configuration ](#stream-jmx-configuration).
 
StreamAgent arguments `-connect -vm:service:jmx:<JMX_URL> -ao:*:*!!10000` states:
* `-connect` - defines that StreamsAgent shall connect to running JMX service over JMXConnector (RMI) connection.
* `-vm:service:jmx:<JMX_URL>` - is JMX service URL to use for connection. Mandatory argument. Full URL may be like 
`service:jmx:rmi://127.0.0.1/stub/rO0ABXN9AAAAAQAlamF2YXgubWFuYWdlbWVudC5yZW1vdGUucm1pLlJNSVNlcnZlcnhyABdqYXZhLmxhbmcucmVmbGVjdC5Qcm94eeEn2iDMEEPLAgABTAABaHQAJUxqYXZhL2xhbmcvcmVmbGVjdC9JbnZvY2F0aW9uSGFuZGxlcjt4cHNyAC1qYXZhLnJtaS5zZXJ2ZXIuUmVtb3RlT2JqZWN0SW52b2NhdGlvbkhhbmRsZXIAAAAAAAAAAgIAAHhyABxqYXZhLnJtaS5zZXJ2ZXIuUmVtb3RlT2JqZWN002G0kQxhMx4DAAB4cHc2AAtVbmljYXN0UmVmMgAACzE3Mi4xNi42Ljg2AADPWKO5DJD/bZIhG9aBuwAAAVo8DdAkgAEAeA==`.
* `-ao:*:*!!10000` - is JMX sampler options stating to include all MBeans and schedule sampling every 10 seconds. Sampler options are 
optional - default value is `*:*!30000`.  

## Embed Stream-JMX code into your application

### Coding

```java
    // obtain SamplerFactory instance
    SamplerFactory factory = DefaultSamplerFactory.getInstance();
    // create an instance of the sampler that will sample mbeans
    Sampler sampler = factory.newInstance();
    // schedule collection (ping) for given MBean filter and 30000 ms sampling period
    sampler.setSchedule(Sampler.JMX_FILTER_ALL, 30000).run();
```
<b>NOTE:</b> `setSchedule(..).run()` sequence must be called to run the schedule. `setSchedule(..)` just sets the
scheduling parameters, `run()` executes the schedule.

To schedule metric collection for a specific MBean server:
```java
    // obtain SamplerFactory instance
    SamplerFactory factory = DefaultSamplerFactory.getInstance();
    // create an instance of the sampler that will sample mbeans
    Sampler sampler = factory.newInstance(ManagementFactory.getPlatformMBeanServer());
    // schedule collection (ping) for given MBean filter and 30000 ms sampling period
    sampler.setSchedule(Sampler.JMX_FILTER_ALL, 30000).run();
```
Stream-JMX supports inclusion and exclusion filters.
To schedule metric collection for a specific MBean server and exclude certain MBeans:
(Exclusion filters are applied after inclusion filters)
```java
    // obtain SamplerFactory instance
    SamplerFactory factory = DefaultSamplerFactory.getInstance();
    // create an instance of the sampler that will sample mbeans
    Sampler sampler = factory.newInstance(ManagementFactory.getPlatformMBeanServer());
    String excludeMBeanFilter = "mydomain:*";
    // schedule collection (ping) for given MBean filter and 30000 ms sampling period
    sampler.setSchedule(Sampler.JMX_FILTER_ALL, excludeMBeanFilter, 30000).run();
```
Below is an example of how to sample all registered mbean servers:
```java
    // obtain SamplerFactory instance
    SamplerFactory factory = DefaultSamplerFactory.getInstance();
    // find other registered mbean servers
    ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
    for (MBeanServer server: mlist) {
        Sampler jmxp = factory.newInstance(server);
        jmxp.setSchedule(Sampler.JMX_FILTER_ALL, 30000).run();
    }
```
Alternatively, Stream-JMX provides a helper class `SamplingAgent` that lets you schedule sampling for all registered `MBeanServer` instances.
```java
    SamplingAgent.sample(Sampler.JMX_FILTER_ALL, Sampler.JMX_FILTER_NONE, 60000, TimeUnit.MILLISECONDS);
```
<b>NOTE:</b> Sampled MBean attributes and associated values are stored in a collection of `Snapshot` objects stored within `Activity` instance. Current `Activity` instance can be obtained via `AttributeSample` passed when calling listeners such as `AttributeCondition`, `SampleListener`. Snapshots can be accessed using `Activity.getSnapshots()` method call.

<b>NOTE:</b> Sampled output is written to underlying tnt4j event sink configured in `tnt4j.properties` file. Sink destinations could be a file, socket, log4j, user defined event sink implementations. 

For more information on TNT4J and `tnt4j.properties` see (https://github.com/Nastel/TNT4J/wiki/Getting-Started).

### Command line to run
Example below runs `SamplingAgent` helper class as a standalone java application with a given MBean filter `"*:*"`, sampling period in milliseconds (`10000`), and time to run in milliseconds (`60000`):
```cmd
java -Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true -classpath "tnt4j-stream-jmx*.jar;lib/*" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent "*:*" "" 10000 60000
```

## Stream-JMX configuration 

### System properties used
* `tnt4j.config` - defines TNT4J properties file path. Example: `-Dtnt4j.config=".\config\tnt4j.properties"`
* `com.jkoolcloud.tnt4j.stream.jmx.agent.trace` - defines whether to dump trace data to application console output. 
Example: `-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true`
* `com.jkoolcloud.tnt4j.stream.jmx.agent.validate.types` - defines if MBean attribute value types validation should be applied. If `true` - 
only non array primitives, Strings, Numbers and Booleans are allowed to be processed. 
Example: `-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.validate.types=false`

## Stream-JMX event data formatters

* FactNameValueFormatter - This class provides key/value formatting for tnt4j activities, events and snapshots. The output format follows 
the following format:
```
"OBJ:name-value-prefix1\name1=value1,name-value-prefix1\name2=value2,....,name-value-prefixN\nameN=valueN"
```
Sample output:
```
OBJ:Streams\0,
0\HQDC\samtis\com.jkoolcloud.tnt4j.stream.jmx.impl.PlatformJmxSampler\Activities,
Self\location=0,
0,
Self\level=INFO,
Self\id.count=0,
Self\pid=7660,
Self\tid=63,
Self\snap.count=34,
Self\elapsed.usec=9989365,
JMImplementation:type\MBeanServerDelegate\MBeanServerId=samtis_1486645461070,
JMImplementation:type\MBeanServerDelegate\SpecificationName=Java Management Extensions,
JMImplementation:type\MBeanServerDelegate\SpecificationVersion=1.4,
JMImplementation:type\MBeanServerDelegate\SpecificationVendor=Oracle Corporation,
JMImplementation:type\MBeanServerDelegate\ImplementationName=JMX,
JMImplementation:type\MBeanServerDelegate\ImplementationVersion=1.8.0_121-b13,
JMImplementation:type\MBeanServerDelegate\ImplementationVendor=Oracle Corporation,
org.apache.activemq:brokerName\localhost!type\Broker\Uptime=1 minute,
org.apache.activemq:brokerName\localhost!type\Broker\MemoryPercentUsage=0,
org.apache.activemq:brokerName\localhost!type\Broker\Persistent=true,
org.apache.activemq:brokerName\localhost!type\Broker\DataDirectory=D:\tmp\apache-activemq-5.14.3\data,
org.apache.activemq:brokerName\localhost!type\Broker\VMURL=vm://localhost,
org.apache.activemq:brokerName\localhost!type\Broker\TempLimit=42844577792,
org.apache.activemq:brokerName\localhost!type\Broker\MemoryLimit=668309914,
org.apache.activemq:brokerName\localhost!type\Broker\StoreLimit=42846849471,
org.apache.activemq:brokerName\localhost!type\Broker\MaxMessageSize=1024,
org.apache.activemq:brokerName\localhost!type\Broker\AverageMessageSize=1024,
org.apache.activemq:brokerName\localhost!type\Broker\TotalDequeueCount=0,
org.apache.activemq:brokerName\localhost!type\Broker\TotalConsumerCount=0,
org.apache.activemq:brokerName\localhost!type\Broker\StatisticsEnabled=true,
org.apache.activemq:brokerName\localhost!type\Broker\JobSchedulerStoreLimit=0,
org.apache.activemq:brokerName\localhost!type\Broker\MinMessageSize=1024,
org.apache.activemq:brokerName\localhost!type\Broker\JobSchedulerStorePercentUsage=0,
org.apache.activemq:brokerName\localhost!type\Broker\StorePercentUsage=0,
org.apache.activemq:brokerName\localhost!type\Broker\TempPercentUsage=0,
org.apache.activemq:brokerName\localhost!type\Broker\CurrentConnectionsCount=0,
org.apache.activemq:brokerName\localhost!type\Broker\TotalMessageCount=0,
org.apache.activemq:brokerName\localhost!type\Broker\TotalConnectionsCount=0,
org.apache.activemq:brokerName\localhost!type\Broker\TotalEnqueueCount=1,
org.apache.activemq:brokerName\localhost!type\Broker\TotalProducerCount=0,
org.apache.activemq:brokerName\localhost!type\Broker\TransportConnectors=[openwire=tcp://samtis:61616?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 amqp=amqp://samtis:5672?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 mqtt=mqtt://samtis:1883?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 stomp=stomp://samtis:61613?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 ws=ws://samtis:61614?maximumConnections=1000&wireFormat.maxFrameSize=104857600],
org.apache.activemq:brokerName\localhost!type\Broker\BrokerVersion=5.14.3,
org.apache.activemq:brokerName\localhost!type\Broker\UptimeMillis=62787,
org.apache.activemq:brokerName\localhost!type\Broker\BrokerName=localhost,
org.apache.activemq:brokerName\localhost!type\Broker\Slave=false,
org.apache.activemq:brokerName\localhost!type\Broker\BrokerId=ID:samtis-52769-1486645462150-0:1,
SampleContext\noop.count=0,
SampleContext\sample.count=6,
SampleContext\total.error.count=6,
SampleContext\total.exclude.count=204,
SampleContext\mbean.count=37,
SampleContext\condition.count=0,
SampleContext\listener.count=1,
SampleContext\total.action.count=0,
SampleContext\total.metric.count=3582,
SampleContext\last.metric.count=595,
SampleContext\sample.time.usec=10054,
SampleContext\listener.exclude.set.count=6,
SampleContext\listener.trace.mode=false,
...
```
**NOTE:** Entries are not sorted, sequence is same as returned by activity contained snapshots map entries and snapshot properties iterators.  

* FactPathValueFormatter - This class provides key/value formatting for tnt4j activities, events and snapshots. The output format follows 
the following format:
```
"OBJ:object-path1\name1=value1,object-path1\name2=value2,....,object-pathN\nameN=valueN""
```
Sample output:
```
OBJ:Streams\0,
0\HQDC\samtis\com.jkoolcloud.tnt4j.stream.jmx.impl.PlatformJmxSampler\Activities,
Self\location=0,
0,
Self\level=INFO,
Self\id.count=0,
Self\pid=3136,
Self\tid=24,
Self\snap.count=34,
Self\elapsed.usec=9885208,
JMImplementation\MBeanServerDelegate\ImplementationName=JMX,
JMImplementation\MBeanServerDelegate\ImplementationVendor=Oracle Corporation,
JMImplementation\MBeanServerDelegate\ImplementationVersion=1.8.0_121-b13,
JMImplementation\MBeanServerDelegate\MBeanServerId=samtis_1486998422874,
JMImplementation\MBeanServerDelegate\SpecificationName=Java Management Extensions,
JMImplementation\MBeanServerDelegate\SpecificationVendor=Oracle Corporation,
JMImplementation\MBeanServerDelegate\SpecificationVersion=1.4,
SampleContext\condition.count=0,
SampleContext\last.metric.count=633,
SampleContext\listener.count=1,
SampleContext\listener.exclude.set.count=6,
SampleContext\listener.trace.mode=true,
SampleContext\mbean.count=37,
SampleContext\noop.count=0,
SampleContext\sample.count=2,
SampleContext\sample.time.usec=72856,
SampleContext\total.action.count=0,
SampleContext\total.error.count=6,
SampleContext\total.exclude.count=66,
SampleContext\total.metric.count=1272,
org.apache.activemq\Broker\localhost\AverageMessageSize=1024,
org.apache.activemq\Broker\localhost\BrokerId=ID:samtis-63754-1486998424125-0:1,
org.apache.activemq\Broker\localhost\BrokerName=localhost,
org.apache.activemq\Broker\localhost\BrokerVersion=5.14.3,
org.apache.activemq\Broker\localhost\CurrentConnectionsCount=0,
org.apache.activemq\Broker\localhost\DataDirectory=D:\tmp\apache-activemq-5.14.3\data,
org.apache.activemq\Broker\localhost\DurableTopicSubscribers=[],
org.apache.activemq\Broker\localhost\DynamicDestinationProducers=[],
org.apache.activemq\Broker\localhost\InactiveDurableTopicSubscribers=[],
org.apache.activemq\Broker\localhost\JMSJobScheduler=null,
org.apache.activemq\Broker\localhost\JobSchedulerStoreLimit=0,
org.apache.activemq\Broker\localhost\JobSchedulerStorePercentUsage=0,
org.apache.activemq\Broker\localhost\MaxMessageSize=1024,
org.apache.activemq\Broker\localhost\MemoryLimit=668309914,
org.apache.activemq\Broker\localhost\MemoryPercentUsage=0,
org.apache.activemq\Broker\localhost\MinMessageSize=1024,
org.apache.activemq\Broker\localhost\Persistent=true,
org.apache.activemq\Broker\localhost\QueueProducers=[],
org.apache.activemq\Broker\localhost\QueueSubscribers=[],
org.apache.activemq\Broker\localhost\Queues=[],
org.apache.activemq\Broker\localhost\Slave=false,
org.apache.activemq\Broker\localhost\StatisticsEnabled=true,
org.apache.activemq\Broker\localhost\StoreLimit=42845775469,
org.apache.activemq\Broker\localhost\StorePercentUsage=0,
org.apache.activemq\Broker\localhost\TempLimit=42842071040,
org.apache.activemq\Broker\localhost\TempPercentUsage=0,
org.apache.activemq\Broker\localhost\TemporaryQueueProducers=[],
org.apache.activemq\Broker\localhost\TemporaryQueueSubscribers=[],
org.apache.activemq\Broker\localhost\TemporaryQueues=[],
org.apache.activemq\Broker\localhost\TemporaryTopicProducers=[],
org.apache.activemq\Broker\localhost\TemporaryTopicSubscribers=[],
org.apache.activemq\Broker\localhost\TemporaryTopics=[],
org.apache.activemq\Broker\localhost\TopicProducers=[],
org.apache.activemq\Broker\localhost\TopicSubscribers=[],
org.apache.activemq\Broker\localhost\Topics=[org.apache.activemq:type=Broker,
brokerName=localhost,
destinationType=Topic,
destinationName=ActiveMQ.Advisory.MasterBroker],
org.apache.activemq\Broker\localhost\TotalConnectionsCount=0,
org.apache.activemq\Broker\localhost\TotalConsumerCount=0,
org.apache.activemq\Broker\localhost\TotalDequeueCount=0,
org.apache.activemq\Broker\localhost\TotalEnqueueCount=1,
org.apache.activemq\Broker\localhost\TotalMessageCount=0,
org.apache.activemq\Broker\localhost\TotalProducerCount=0,
org.apache.activemq\Broker\localhost\TransportConnectors=[amqp=amqp://samtis:5672?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 mqtt=mqtt://samtis:1883?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 ws=ws://samtis:61614?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 openwire=tcp://samtis:61616?maximumConnections=1000&wireFormat.maxFrameSize=104857600,
 stomp=stomp://samtis:61613?maximumConnections=1000&wireFormat.maxFrameSize=104857600],
org.apache.activemq\Broker\localhost\Uptime=13 minutes,
org.apache.activemq\Broker\localhost\UptimeMillis=822599,
org.apache.activemq\Broker\localhost\VMURL=vm://localhost,
...
```
**NOTE:** Entries are sorted by key alphanumeric ordering and key representation is more common to be used for i.e. tree model construction 
to represent JMX structure more like `JConsole` does.  

## Where do the streams go?
Stream-JMX streams all collected metrics based on a scheduled interval via TNT4J event streaming framework.
All streams are written into TNT4J event sinks defined in `tnt4j.properties` file which is defined by `-Dtnt4j.config=tnt4j.properties` property. 

To stream Stream-JMX to jkool cloud (https://www.jkoolcloud.com): (Requires JESL libraries (see https://github.com/Nastel/JESL))
```properties
;Stanza used for Stream-JMX sources
{
	source: com.jkoolcloud.tnt4j.stream.jmx
	source.factory: com.jkoolcloud.tnt4j.source.SourceFactoryImpl
	source.factory.GEOADDR: New York
	source.factory.DATACENTER: YourDC
	source.factory.RootFQN: SERVER=?#DATACENTER=?#GEOADDR=?	
	source.factory.RootSSN: tnt4j-stream-jmx	
	
	tracker.factory: com.com.jkoolcloud.jkool.tnt4j.tracker.DefaultTrackerFactory
	dump.sink.factory: com.jkoolcloud.tnt4j.dump.DefaultDumpSinkFactory

	; Event sink definition where all streams are recorded
	event.sink.factory: com.jkoolcloud.tnt4j.sink.impl.BufferedEventSinkFactory
	event.sink.factory.PooledLoggerFactory: com.jkoolcloud.tnt4j.sink.impl.PooledLoggerFactoryImpl

	; Event Sink configuration for streaming to jKool Cloud
	; event.sink.factory.EventSinkFactory.Filename: jkoolcloud.json
	event.sink.factory.EventSinkFactory.Url: https://data.jkoolcloud.com
	event.sink.factory.EventSinkFactory.Token: ACCESS-TOKEN
	event.formatter: com.jkoolcloud.tnt4j.format.JSONFormatter

	; Configure default sink filter 
	event.sink.factory.Filter: com.jkoolcloud.tnt4j.filters.EventLevelTimeFilter
	event.sink.factory.Filter.Level: TRACE
	
	tracking.selector: com.jkoolcloud.tnt4j.selector.DefaultTrackingSelector
	tracking.selector.Repository: com.jkoolcloud.tnt4j.repository.FileTokenRepository
}
```
Below is an example of TNT4J stream definition where all Stream-JMX streams are written into a socket event sink
`com.jkoolcloud.tnt4j.sink.impl.SocketEventSinkFactory`, formatted by `com.jkoolcloud.tnt4j.stream.jmx.format.FactNameValueFormatter` :
```properties
;Stanza used for Stream-JMX sources
{
	source: com.jkoolcloud.tnt4j.stream.jmx
	source.factory: com.jkoolcloud.tnt4j.source.SourceFactoryImpl
	source.factory.GEOADDR: New York
	source.factory.DATACENTER: YourDC
	source.factory.RootFQN: SERVER=?#DATACENTER=?#GEOADDR=?	
	source.factory.RootSSN: tnt4j-stream-jmx	
	
	tracker.factory: com.jkoolcloud.tnt4j.tracker.DefaultTrackerFactory
	dump.sink.factory: com.jkoolcloud.tnt4j.dump.DefaultDumpSinkFactory

	; Event sink definition where all streams are recorded

	event.sink.factory: com.jkoolcloud.tnt4j.sink.impl.BufferedEventSinkFactory
	event.sink.factory.PooledLoggerFactory: com.jkoolcloud.tnt4j.sink.impl.PooledLoggerFactoryImpl
	
	event.sink.factory.EventSinkFactory: com.jkoolcloud.tnt4j.sink.impl.SocketEventSinkFactory
	event.sink.factory.EventSinkFactory.eventSinkFactory: com.jkoolcloud.tnt4j.sink.impl.NullEventSinkFactory
	event.sink.factory.EventSinkFactory.Host: localhost
	event.sink.factory.EventSinkFactory.Port: 6060

	; Configure default sink filter 
	event.sink.factory.Filter: com.jkoolcloud.tnt4j.filters.EventLevelTimeFilter
	event.sink.factory.Filter.Level: TRACE
	
	; If JMX attributes should be formatted as JMX object names 
	event.formatter: com.jkoolcloud.tnt4j.stream.jmx.format.FactNameValueFormatter
	; If JMX attributes should be formatted as JMX object paths
	;event.formatter: com.jkoolcloud.tnt4j.stream.jmx.format.FactPathValueFormatter
	; If JMX attribute string type values should surounded by double quote symbol
	;event.formatter.QuoteStringValues: true

	tracking.selector: com.jkoolcloud.tnt4j.selector.DefaultTrackingSelector
	tracking.selector.Repository: com.jkoolcloud.tnt4j.repository.FileTokenRepository
}
```
To stream Stream-JMX into a log file `MyStream.log`:
```properties
;Stanza used for Stream-JMX sources
{
	source: com.jkoolcloud.tnt4j.stream.jmx
	source.factory: com.jkoolcloud.tnt4j.source.SourceFactoryImpl
	source.factory.GEOADDR: New York
	source.factory.DATACENTER: YourDC
	source.factory.RootFQN: SERVER=?#DATACENTER=?#GEOADDR=?	
	source.factory.RootSSN: tnt4j-stream-jmx	
	
	tracker.factory: com.jkoolcloud.tnt4j.tracker.DefaultTrackerFactory
	dump.sink.factory: com.jkoolcloud.tnt4j.dump.DefaultDumpSinkFactory

	; Event sink definition where all streams are recorded
	event.sink.factory: com.jkoolcloud.tnt4j.sink.impl.BufferedEventSinkFactory
	event.sink.factory.PooledLoggerFactory: com.jkoolcloud.tnt4j.sink.impl.PooledLoggerFactoryImpl
	event.sink.factory.EventSinkFactory: com.jkoolcloud.tnt4j.sink.impl.FileEventSinkFactory
	event.sink.factory.EventSinkFactory.FileName: MyStream.log

	; Configure default sink filter 
	event.sink.factory.Filter: com.jkoolcloud.tnt4j.filters.EventLevelTimeFilter
	event.sink.factory.Filter.Level: TRACE
	
	; If JMX attributes should be formatted as JMX object names 
	event.formatter: com.jkoolcloud.tnt4j.stream.jmx.format.FactNameValueFormatter
	; If JMX attributes should be formatted as JMX object paths
	;event.formatter: com.jkoolcloud.tnt4j.stream.jmx.format.FactPathValueFormatter
	; If JMX attribute string type values should surounded by double quote symbol
	;event.formatter.QuoteStringValues: true

	tracking.selector: com.jkoolcloud.tnt4j.selector.DefaultTrackingSelector
	tracking.selector.Repository: com.jkoolcloud.tnt4j.repository.FileTokenRepository
}
```
You can write your own custom event sinks (HTTPS, HTTP, etc) and your own stream formatters without having to change Stream-JMX code or your application. TNT4J comes with a set of built-in event sink implementations such as: 

* `com.jkoolcloud.tnt4j.logger.Log4JEventSinkFactory` -- log4j
* `com.jkoolcloud.tnt4j.sink.impl.BufferedEventSinkFactory` -- buffered sink
* `com.jkoolcloud.tnt4j.sink.impl.FileEventSinkFactory` - standard log file
* `com.jkoolcloud.tnt4j.sink.impl.SocketEventSinkFactory` -- socket (tcp/ip)
* `com.jkoolcloud.tnt4j.sink.impl.NullEventSinkFactory` -- null (empty)

## Auto-generating application state dump
Stream-JMX is utilizing TNT4J state dump capability to generate application state dumps

(1) Dump on VM shut-down:
```cmd
java -Dtnt4j.dump.on.vm.shutdown=true -Dtnt4j.dump.provider.default=true -Dtnt4j.dump.folder=./ ...
```
(2) Dump on uncaught thread exceptions:
```cmd
java -Dtnt4j.dump.on.exceptionn=true -Dtnt4j.dump.provider.default=true -Dtnt4j.dump.folder=./ ...
```
`-Dtnt4j.dump.folder=./` specifies the destination folder where dump (.dump) files will be created (default is current working directory).

By default Stream-JMX will generate dumps with the following info:

* Java Properties Dump -- `PropertiesDumpProvider`
* Java Runtime Dump -- `MXBeanDumpProvider`
* Thread Stack Dump -- `ThreadDumpProvider`
* Thread Deadlock Dump -- `ThreadDumpProvider`
* Logging Statistics Dump -- `LoggerDumpProvider`

You may create your own dump providers and handlers (https://github.com/Nastel/TNT4J/wiki/Getting-Started#application-state-dumps)
## Overriding default `SamplerFactory`
`SamplerFactory` instances are used to generate `Sampler` implementation for a specific runtime environment. Stream-JMX supplies sampler and ping factories for standard JVMs, JBoss,
WebSphere Application Server. You may want to override default `SamplerFactory` with your own or an alternative by specifying:
```cmd
java -Dcom.jkoolcloud.tnt4j.stream.jmx.factory=com.jkoolcloud.tnt4j.stream.jmx.PlatformSamplerFactory ...
```
`SamplerFactory` is used to generate instances of the underlying sampler implementations (objects that provide sampling of underlying mbeans).
```java
    // return default or user defined SamplerFactory implementation
    SamplerFactory factory = DefaultSamplerFactory.getInstance();
    ...
```
## Managing Sample Behavior
Stream-JMX provides a way to intercept sampling events such as pre, during an post for each sample run and control sample behavior. See `SampleListener` interface for more details. Applications may register more than one listener per `Sampler`. Each listener is called in registration order.

In addition to intercepting sample events, applications may want to control how one ore more attributes are sampled and whether each sample is reported/logged. See example below:
```java
    // return default or user defined SamplerFactory implementation
    SamplerFactory factory = DefaultSamplerFactory.getInstance();
    // create an instance of the sampler that will sample mbeans
    Sampler sampler = factory.newInstance();
    sampler.setSchedule(Sampler.JMX_FILTER_ALL, 30000).addListener(new MySampleListener())).run();
```
Below is a sample of what `MySampleListener` may look like:
```java
class MySampleListener implements SampleListener {
	@Override
    public void getStats(SampleContext context, Map<String, Object> stats) {
		// add your own stats to the map
	}
	
	@Override
    public void register(SampleContext context, ObjectName oname) {
		System.out.println("Register mbean: " + oname + ", mbean.server=" + context.getMBeanServer());
	}

	@Override
    public void unregister(SampleContext context, ObjectName oname) {
		System.out.println("Unregister mbean: " + oname + ", mbean.server=" + context.getMBeanServer());
    }

	@Override
	public void pre(SampleContext context, Activity activity) {
		// called once per sample, beginning of each sample
		// set activity to NOOP to disable further sampling
		// no other attribute will be sampled during current sample
		if (some-condition) {
			activity.setType(OpType.NOOP);
		}
	}

	@Override
	public void pre(SampleContext context, AttributeSample sample) {
		// called once before attribute is sampled
		// set exclude to true to skip sampling this attribute
		sample.excludeNext(sample.getAttributeInfo().isReadable());
	}

	@Override
	public void post(SampleContext context, AttributeSample sample) {
		// called once after attribute is sampled
		Object value = sample.get();
	}

	@Override
	public void post(SampleContext context, Activity activity) {
		// called once per sample, end of each sample
		// set activity to NOOP to disable sampling reporting
		if (some-condition) {
			activity.setType(OpType.NOOP);
		}
	}
	
	@Override
	public void error(SampleContext context, Throwable ex) {
		// called once for every exception that occurs not associated with a sample
		ex.printStackTrace();
	}
	
	@Override
	public void error(SampleContext context, AttributeSample sample) {
		// called once for every exception that occurs during each sample
		Throwable ex = sample.getError();
		ex.printStackTrace();
	}	
}
```
## Conditions and Actions
Stream-JMX allows you to associate conditions with user defined actions based on values of MBean attributes on each sampling
interval. For example, what if you wanted to setup an action when a specific MBean attribute exceeds a certain threshold?

Stream-JMX `AttributeCondition` and `AttributeAction` interfaces allow you to call your action at runtime every time a condition is evaluated to true. See example below:
```java
    // return default or user defined SamplerFactory implementation
    SamplerFactory factory = DefaultSamplerFactory.getInstance();
    // create an instance of the sampler that will sample mbeans
    Sampler sampler = factory.newInstance();
    // create a condition when ThreadCount > 100
    AttributeCondition myCondition = new SimpleCondition("java.lang:type=Threading", "ThreadCount", 100, ">");
    // schedule collection (ping) for given MBean filter and 30000 ms sampling period
    sampler.setSchedule(Sampler.JMX_FILTER_ALL, 30000).register(myCondition, new MyAttributeAction()).run();
```
Below is a sample of what `MyAttributeAction` may look like:
```java
public class MyAttributeAction implements AttributeAction {
	@Override
	public Object action(SampleContext context, AttributeCondition cond, AttributeSample sample) {
		Activity activity = sample.getActivity();
		// obtain a collection of all sampled metrics
		Collection<Snapshot> metrics = activity.getSnapshots();
		System.out.println("Myaction called with value=" + sample.get()
			+ ", age.usec=" + sample.ageUsec()
			+ ", count=" + metrics.size());
		return null;
	}
}
```

# Project Dependencies
Stream-JMX requires the following:
* JDK 1.6+
    * JDK instrumentation library `tools.jar` is referenced:
        * from Maven POM script by
          ```xml
          <dependency>
              <groupId>com.sun</groupId>
              <artifactId>tools</artifactId>
              <version>1.7.0</version>
              <scope>system</scope>
              <systemPath>${java.home}/../lib/tools.jar</systemPath>
          </dependency>
          ```
        * from system executables `bin/stream-jmx.bat` or `bin/stream-jmx.sh` by environment variable `TOOLS_PATH`
          ```cmd
          set TOOLS_PATH="%JAVA_HOME%\lib\tools.jar"
          ```
          ```bash
          TOOLS_PATH="$JAVA_HOME/lib/tools.jar"
          ```          
     **NOTE:** you may need to change paths if these do not match your enviroment.     
* TNT4J (https://github.com/Nastel/TNT4J)

Please use JCenter or Maven and dependencies will be downloaded automatically. 

# Related Projects
* TrackingFilter (http://nastel.github.io/TrackingFilter/)
* JESL (http://nastel.github.io/JESL/)

# Available Integrations
* TNT4J (https://github.com/Nastel/TNT4J)
* jKoolCloud.com (https://www.jkoolcloud.com)
* AutoPilot M6 (http://www.nastel.com/products/autopilot-m6.html)
