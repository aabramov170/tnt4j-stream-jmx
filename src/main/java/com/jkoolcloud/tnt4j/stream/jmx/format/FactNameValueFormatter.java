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
package com.jkoolcloud.tnt4j.stream.jmx.format;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.core.Operation;
import com.jkoolcloud.tnt4j.core.Property;
import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.format.DefaultFormatter;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.SchedulerImpl;
import com.jkoolcloud.tnt4j.tracker.TrackingActivity;
import com.jkoolcloud.tnt4j.tracker.TrackingEvent;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * This class provides key/value formatting for tnt4j activities, events and snapshots. The output format follows the
 * following format:
 * <p>
 * {@code "OBJ:name-value-prefix,name1=value1,....,nameN=valueN"}.
 * </p>
 * Newline is added at the end of each line.
 *
 * @version $Revision: 1 $
 * 
 * @see SchedulerImpl
 */
public class FactNameValueFormatter extends DefaultFormatter {
	public static final String LF = "\n";
	public static final String CR = "\r";
	public static final String FIELD_SEP = ",";
	public static final String END_SEP = LF;
	public static final String PATH_DELIM = "\\";
	public static final String EQ = "=";

	protected static final Pattern REP_CFG_PATTERN = Pattern.compile("\"(\\s*[^\"]+\\s*)\"->\"(\\s*[^\"]+\\s*)\"");

	protected boolean serializeSimpleTypesOnly = false;

	protected Map<String, String> keyReplacements = new HashMap<>();
	protected Map<String, String> valueReplacements = new HashMap<>();

	public FactNameValueFormatter() {
		super("time.stamp={2},level={1},source={3},msg=\"{0}\"");
	}

	@Override
	public String format(TrackingEvent event) {
		StringBuilder nvString = new StringBuilder(1024);
		nvString.append("OBJ:Streams");
		toString(nvString, event.getSource()).append(event.getOperation().getName()).append("\\Events").append(FIELD_SEP);

		if (event.getCorrelator() != null) {
			Object[] cids = event.getCorrelator().toArray();
			if (cids.length > 0) {
				nvString.append("corrid=").append(getValueStr(cids[0])).append(FIELD_SEP);
			}
		}
		if (event.getTag() != null) {
			Object[] cids = event.getTag().toArray();
			if (cids.length > 0) {
				nvString.append("tag=").append(getValueStr(cids[0])).append(FIELD_SEP);
			}
		}
		if (event.getOperation().getUser() != null) nvString.append("user=").append(getValueStr(event.getOperation().getUser())).append(FIELD_SEP);
		if (event.getLocation() != null) nvString.append("Self\\location=").append(getValueStr(event.getLocation())).append(FIELD_SEP);
		nvString.append("Self\\level=").append(getValueStr(event.getOperation().getSeverity())).append(FIELD_SEP);
		nvString.append("Self\\pid=").append(getValueStr(event.getOperation().getPID())).append(FIELD_SEP);
		nvString.append("Self\\tid=").append(getValueStr(event.getOperation().getTID())).append(FIELD_SEP);
		nvString.append("Self\\tid=").append(getValueStr(event.getOperation().getTID())).append(FIELD_SEP);
		nvString.append("Self\\elapsed.usec=").append(getValueStr(event.getOperation().getElapsedTimeUsec())).append(FIELD_SEP);

		Collection<Snapshot> slist = getSnapshots(event.getOperation());
		for (Snapshot snap : slist) {
			toString(nvString, snap);
		}
		return nvString.append(END_SEP).toString();
	}

	/**
	 * Returns operation contained snpshots collcetion.
	 * 
	 * @param op operation isntance
	 * @return collection of operation snapshots
	 */
	protected Collection<Snapshot> getSnapshots(Operation op) {
		return op.getSnapshots();
	}

	@Override
	public String format(TrackingActivity event) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:Streams");
		toString(nvString, event.getSource()).append("\\Activities").append(FIELD_SEP);

		if (event.getCorrelator() != null) {
			Object[] cids = event.getCorrelator().toArray();
			if (cids.length > 0) {
				nvString.append("Self\\corrid=").append(getValueStr(cids[0])).append(FIELD_SEP);
			}
		}
		if (event.getUser() != null) nvString.append("user=").append(getValueStr(event.getUser())).append(FIELD_SEP);
		if (event.getLocation() != null) nvString.append("Self\\location=").append(getValueStr(event.getLocation())).append(FIELD_SEP);
		nvString.append("Self\\level=").append(getValueStr(event.getSeverity())).append(FIELD_SEP);
		nvString.append("Self\\id.count=").append(getValueStr(event.getIdCount())).append(FIELD_SEP);
		nvString.append("Self\\pid=").append(getValueStr(event.getPID())).append(FIELD_SEP);
		nvString.append("Self\\tid=").append(getValueStr(event.getTID())).append(FIELD_SEP);
		nvString.append("Self\\snap.count=").append(getValueStr(event.getSnapshotCount())).append(FIELD_SEP);
		nvString.append("Self\\elapsed.usec=").append(getValueStr(event.getElapsedTimeUsec())).append(FIELD_SEP);

		Collection<Snapshot> slist = getSnapshots(event);
		for (Snapshot snap : slist) {
			toString(nvString, snap);
		}

		return nvString.append(END_SEP).toString();
	}

	@Override
	public String format(Snapshot event) {
		StringBuilder nvString = new StringBuilder(1024);
		String prefix = "OBJ:Metrics\\" + event.getCategory();
		nvString.append(prefix).append(FIELD_SEP);
		toString(nvString, event).append(END_SEP);
		return nvString.toString();
	}

	@Override
	public String format(long ttl, Source source, OpLevel level, String msg, Object... args) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:Streams");
		toString(nvString, source).append("\\Message").append(FIELD_SEP);
		nvString.append("Self\\level=").append(getValueStr(level)).append(FIELD_SEP);
		nvString.append("Self\\msg-text=").append(Utils.quote(Utils.format(msg, args))).append(END_SEP);
		return nvString.toString();
	}

	/**
	 * Makes string representaion of source and appends it to provided string builder.
	 * 
	 * @param nvString string builder instance to append
	 * @param source source instance to represent as string
	 * @return appended string builder reference
	 */
	protected StringBuilder toString(StringBuilder nvString, Source source) {
		Source parent = source.getSource();
		if (parent != null) {
			toString(nvString, source.getSource());
		}
		nvString.append(PATH_DELIM).append(source.getName());
		return nvString;
	}

	/**
	 * Returns snapshot contained properties collcetion.
	 * 
	 * @param snap snpashot instance
	 * @return collection of snapshot properties
	 */
	protected Collection<Property> getProperties(Snapshot snap) {
		return snap.getSnapshot();
	}

	/**
	 * Makes string representaion of snapshot and appends it to provided string builder.
	 * 
	 * @param nvString string builder instance to append
	 * @param snap snapshot instance to represent as string
	 * @return appended string builder reference
	 */
	protected StringBuilder toString(StringBuilder nvString, Snapshot snap) {
		Collection<Property> list = getProperties(snap);
		String sName = getSnapNameStr(snap.getName());
		for (Property p : list) {
			Object value = p.getValue();

			nvString.append(getKeyStr(sName, p.getKey()));
			nvString.append(EQ).append(getValueStr(value)).append(FIELD_SEP);
		}
		return nvString;
	}

	/**
	 * Determine if a given value can be meaningfully serialized to string.
	 *
	 * @param value value to test for serialization
	 * @return {@code true} if a given value can be serialized to string meaningfully, {@code false} - otherwise
	 */
	protected static boolean isSerializable(Object value) {
		return value == null || value.getClass().isPrimitive() || value.getClass().isEnum() || value instanceof String
				|| value instanceof Number || value instanceof Boolean || value instanceof Character;

	}

	/**
	 * Makes decorated string representation of snpashot name.
	 * <p>
	 * Replaces "{@value #EQ}" to "{@value #PATH_DELIM}" and "{@value #FIELD_SEP}" to {@code "!"}.
	 * 
	 * @param snapName snapshot name
	 * @return decorated string representation of snpshot name
	 */
	protected String getSnapNameStr(String snapName) {
		return snapName.replace(EQ, PATH_DELIM).replace(FIELD_SEP, "!");
	}

	/**
	 * Makes decorated string representation of argument attribute key.
	 * <p>
	 * Key representation string gets symbols replaced using ones defined in {@link #keyReplacements} map.
	 * 
	 * @param sName snapshot name
	 * @param pKey property key
	 * @return decorated string representation of attribute key
	 *
	 * @see #initDefaultKeyReplacements()
	 */
	protected String getKeyStr(String sName, String pKey) {
		String keyStr = sName + PATH_DELIM + pKey;

		for (Map.Entry<String, String> kre : keyReplacements.entrySet()) {
			keyStr = keyStr.replace(kre.getKey(), kre.getValue());
		}

		return keyStr;
	}

	/**
	 * Makes decorated string representation of argument attribute value.
	 * <p>
	 * If property {@link #serializeSimpleTypesOnly} is set to {@code true} - validates if value can be represented as
	 * simple type. If no, then actual value is replaced by dummy string {@code "<unsupported value type>"}.
	 * <p>
	 * Value representation string containing {@code "\n"} or {@code "\r"} symbols gets those replaced by escaped
	 * representations {@code "\\n"} amd {@code "\\r"}.
	 * <p>
	 * Value representation string gets symbols replaced using ones defined in {@link #valueReplacements} map.
	 *
	 * @param value attribute value
	 * @return decorated string representation of attribute value
	 *
	 * @see #toString(Object)
	 * @see #initDefaultValueReplacements()
	 */
	protected String getValueStr(Object value) {
		String valStr;

		if (serializeSimpleTypesOnly && !isSerializable(value)) {
			valStr = value == null ? "<null>" : "<unsupported value type>";// : " + value.getClass() + ">";
			// System.out.println("Unsupported value type=" + (value == null ? null : value.getClass()) + " value="
			// + toString(value));
		} else {
			valStr = toString(value);
		}

		valStr = valStr.replace(LF, "\\n").replace(CR, "\\r");
		for (Map.Entry<String, String> vre : valueReplacements.entrySet()) {
			valStr = valStr.replace(vre.getKey(), vre.getValue());
		}

		return valStr;
	}

	/**
	 * Makes string representation of argument attribute value.
	 * 
	 * @param value attribute value
	 * @return string representation of attribute value
	 */
	protected String toString(Object value) {
		return String.valueOf(value);
	}

	@Override
	public void setConfiguration(Map<String, Object> settings) {
		super.setConfiguration(settings);

		serializeSimpleTypesOnly = Utils.getBoolean("SerializeSimplesOnly", settings, serializeSimpleTypesOnly);

		String kReplacements = Utils.getString("KeyReplacements", settings, "");

		if (StringUtils.isEmpty(kReplacements)) {
			initDefaultKeyReplacements();
		} else {
			Matcher m = REP_CFG_PATTERN.matcher(kReplacements);

			while (m.find()) {
				keyReplacements.put(m.group(1), m.group(2));
			}
		}

		String vReplacements = Utils.getString("ValueReplacements", settings, "");
		if (StringUtils.isEmpty(vReplacements)) {
			initDefaultValueReplacements();
		} else {
			Matcher m = REP_CFG_PATTERN.matcher(vReplacements);

			while (m.find()) {
				valueReplacements.put(m.group(1), m.group(2));
			}
		}

		System.currentTimeMillis();
	}

	/**
	 * Initializes default set symbol replacements for a attribute keys.
	 *
	 * <p>
	 * Default keys string replacements mapping is:
	 * <ul>
	 * <li>{@code " "} to {@code "_"}</li>
	 * </ul>
	 */
	protected void initDefaultKeyReplacements() {
		keyReplacements.put(" ", "_");
	}

	/**
	 * Initializes default set symbol replacements for a attribute values.
	 * <p>
	 * Default value string replacements mapping is:
	 * <ul>
	 * <li>{@code ";"} to {@code "|"}</li>
	 * <li>{@code ","} to {@code "|"}</li>
	 * <li>{@code "["} to {@code "{("}</li>
	 * <li>{@code "["} to {@code ")}"}</li>
	 * </ul>
	 */
	protected void initDefaultValueReplacements() {
		valueReplacements.put(";", "|");
		valueReplacements.put(",", "|");
		valueReplacements.put("[", "{(");
		valueReplacements.put("]", ")}");
	}
}
