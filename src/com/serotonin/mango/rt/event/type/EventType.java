/*
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.rt.event.type;

import java.util.Map;

import com.serotonin.json.JsonException;
import com.serotonin.json.JsonObject;
import com.serotonin.json.JsonReader;
import com.serotonin.json.JsonRemoteEntity;
import com.serotonin.json.JsonSerializable;
import com.serotonin.mango.db.dao.DataSourceDao;
import com.serotonin.mango.db.dao.MaintenanceEventDao;
import com.serotonin.mango.db.dao.PublisherDao;
import com.serotonin.mango.db.dao.ScheduledEventDao;
import com.serotonin.mango.util.ExportCodes;
import com.serotonin.mango.util.LocalizableJsonException;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.vo.dataSource.DataSourceVO;
import com.serotonin.mango.vo.event.CompoundEventDetectorVO;
import com.serotonin.mango.vo.event.MaintenanceEventVO;
import com.serotonin.mango.vo.event.ScheduledEventVO;
import com.serotonin.mango.vo.publish.PublisherVO;
import org.scada_lts.mango.service.CompoundEventDetectorService;
import org.scada_lts.mango.service.DataPointService;
import org.scada_lts.mango.service.DataSourceService;
import org.scada_lts.mango.service.ScheduledEventService;

/**
 * An event class specifies the type of event that was raised.
 * 
 * @author Matthew Lohbihler
 */
@JsonRemoteEntity(typeFactory = EventTypeFactory.class)
abstract public class EventType implements JsonSerializable {
    public interface EventSources {
        /**
         * Data points raise events with point event detectors. All point event detectors are stored in a single table,
         * so that the id of the detector is a unique identifier for the type. Thus, the detector's id can be (and is)
         * used as the event type id.
         */
        int DATA_POINT = 1;

        /**
         * Data sources raise events internally for their own reasons (for example no response from the external system)
         * or if a point locator failed. Data source error types are enumerated in the data sources themselves. So, the
         * unique identifier of a data source event type is the combination of the the data source id and the data
         * source error type.
         */
        int DATA_SOURCE = 3;

        /**
         * The system itself is also, of course, a producer of events (for example low disk space). The types of system
         * events are enumerated in the SystemEvents class. The system event type is the unique identifier for system
         * events.
         */
        int SYSTEM = 4;

        /**
         * Compound detector event types have their unique identifiers generated by the database. These detectors listen
         * to point event detectors and scheduled events and raise events according to their configured logical
         * statement.
         */
        int COMPOUND = 5;

        /**
         * Scheduled event types have their unique identifiers generated by the database. Scheduled events are raised by
         * the scheduler at instants defined by the user.
         */
        int SCHEDULED = 6;

        /**
         * Publishers raise events internally for their own reasons, including general publishing failures or failures
         * in individual points. Error types are enumerated in the publishers themselves. So, the unique identifier of a
         * publisher event type is the combination of the publisher id and the publisher error type.
         */
        int PUBLISHER = 7;

        /**
         * Audit events are created when a user makes a change that needs to be acknowledged by other users. Such
         * changes include modifications to point event detectors, scheduled events, compound events, data sources, data
         * points, and point links.
         */
        int AUDIT = 8;

        /**
         * Maintenance events are created when maintenance mode becomes active. See MaintenanceVO for more information.
         */
        int MAINTENANCE = 9;
    }

    public static final ExportCodes SOURCE_CODES = new ExportCodes();
    static {
        SOURCE_CODES.addElement(EventSources.DATA_POINT, "DATA_POINT");
        SOURCE_CODES.addElement(EventSources.DATA_SOURCE, "DATA_SOURCE");
        SOURCE_CODES.addElement(EventSources.SYSTEM, "SYSTEM");
        SOURCE_CODES.addElement(EventSources.COMPOUND, "COMPOUND");
        SOURCE_CODES.addElement(EventSources.SCHEDULED, "SCHEDULED");
        SOURCE_CODES.addElement(EventSources.PUBLISHER, "PUBLISHER");
        SOURCE_CODES.addElement(EventSources.AUDIT, "AUDIT");
        SOURCE_CODES.addElement(EventSources.MAINTENANCE, "MAINTENANCE");
    }

    /**
     * This interface defines all of the possible actions that can occur if an event is raised for which type there
     * already exists an active event.
     * 
     * @author Matthew Lohbihler
     */
    public interface DuplicateHandling {
        /**
         * Duplicates are not allowed. This should be the case for all event types where there is an automatic return to
         * normal.
         */
        int DO_NOT_ALLOW = 1;

        /**
         * Duplicates are ignored. This should be the case where the initial occurrence of an event is really the only
         * thing of interest to a user. For example, the initial error in a data source is usually what is most useful
         * in diagnosing a problem.
         */
        int IGNORE = 2;

        /**
         * Duplicates are ignored only if their message is the same as the existing.
         */
        int IGNORE_SAME_MESSAGE = 3;

        /**
         * Duplicates are allowed. The change detector uses this so that user's can acknowledge every change the point
         * experiences.
         */
        int ALLOW = 4;
    }

    abstract public int getEventSourceId();

    /**
     * Convenience method that keeps us from having to cast.
     * 
     * @return false here, but the system message implementation will return true.
     */
    public boolean isSystemMessage() {
        return false;
    }

    /**
     * Convenience method that keeps us from having to cast.
     * 
     * @return -1 here, but the data source implementation will return the data source id.
     */
    public int getDataSourceId() {
        return -1;
    }

    /**
     * Convenience method that keeps us from having to cast.
     * 
     * @return -1 here, but the data point implementation will return the data point id.
     */
    public int getDataPointId() {
        return -1;
    }

    /**
     * Convenience method that keeps us from having to cast.
     * 
     * @return -1 here, but the schedule implementation will return the schedule id.
     */
    public int getScheduleId() {
        return -1;
    }

    /**
     * Convenience method that keeps us from having to cast.
     * 
     * @return -1 here, but the compound detector event type will return the compound detector id.
     */
    public int getCompoundEventDetectorId() {
        return -1;
    }

    /**
     * Convenience method that keeps us from having to cast.
     * 
     * @return -1 here, but the publisher implementation will return the publisher id.
     */
    public int getPublisherId() {
        return -1;
    }

    /**
     * Determines whether an event type that, once raised, will always first be deactivated or whether overriding events
     * can be raised. Overrides can occur in data sources and point locators where a retry of a failed action causes the
     * same event type to be raised without the previous having returned to normal.
     * 
     * @return whether this event type can be overridden with newer event instances.
     */
    abstract public int getDuplicateHandling();

    abstract public int getReferenceId1();

    abstract public int getReferenceId2();

    /**
     * Determines if the notification of this event to the given user should be suppressed. Useful if the action of the
     * user resulted in the event being raised.
     * 
     * @return
     */
    public boolean excludeUser(@SuppressWarnings("unused") User user) {
        return false;
    }

    //
    // /
    // / Serialization
    // /
    //
    @Override
    public void jsonSerialize(Map<String, Object> map) {
        map.put("sourceType", SOURCE_CODES.getCode(getEventSourceId()));
    }

    /**
     * @throws JsonException
     */
    @Override
    public void jsonDeserialize(JsonReader reader, JsonObject json) throws JsonException {
        // no op. See the factory
    }

    protected int getInt(JsonObject json, String name, ExportCodes codes) throws JsonException {
        String text = json.getString(name);
        if (text == null)
            throw new LocalizableJsonException("emport.error.eventType.missing", name, codes.getCodeList());

        int i = codes.getId(text);
        if (i == -1)
            throw new LocalizableJsonException("emport.error.eventType.invalid", name, text, codes.getCodeList());

        return i;
    }

    // protected int getUserId(JsonObject json, String name) throws JsonException {
    // String username = json.getString(name);
    // if (username == null)
    // throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
    // User user = new UserDao().getUser(username);
    // if (user == null)
    // throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, username);
    // return user.getId();
    // }
    //
    protected int getCompoundEventDetectorId(JsonObject json, String name) throws JsonException {
        String xid = json.getString(name);
        if (xid == null)
            throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
        CompoundEventDetectorVO ced = new CompoundEventDetectorService().getCompoundEventDetector(xid);
        if (ced == null)
            throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
        return ced.getId();
    }

    // protected int getEventHandlerId(JsonObject json, String name) throws JsonException {
    // String xid = json.getString(name);
    // if (xid == null)
    // throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
    // EventHandlerVO eh = new EventDao().getEventHandler(xid);
    // if (eh == null)
    // throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
    // return eh.getId();
    // }
    //
    protected int getScheduledEventId(JsonObject json, String name) throws JsonException {
        String xid = json.getString(name);
        if (xid == null)
            throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
        ScheduledEventVO se = new ScheduledEventService().getScheduledEvent(xid);
        if (se == null)
            throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
        return se.getId();
    }

    protected int getDataPointId(JsonObject json, String name) throws JsonException {
        String xid = json.getString(name);
        if (xid == null)
            throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
        DataPointVO dp = new DataPointService().getDataPoint(xid);
        if (dp == null)
            throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
        return dp.getId();
    }

    // protected int getPointLinkId(JsonObject json, String name) throws JsonException {
    // String xid = json.getString(name);
    // if (xid == null)
    // throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
    // PointLinkVO pl = new PointLinkDao().getPointLink(xid);
    // if (pl == null)
    // throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
    // return pl.getId();
    // }

    protected int getPointEventDetectorId(JsonObject json, String dpName, String pedName) throws JsonException {
        return getPointEventDetectorId(json, getDataPointId(json, dpName), pedName);
    }

    protected int getPointEventDetectorId(JsonObject json, int dpId, String pedName) throws JsonException {
        String pedXid = json.getString(pedName);
        if (pedXid == null)
            throw new LocalizableJsonException("emport.error.eventType.missing.reference", pedName);
        int id = new DataPointService().getDetectorId(pedXid, dpId);
        if (id == -1)
            throw new LocalizableJsonException("emport.error.eventType.invalid.reference", pedName, pedXid);

        return id;
    }

    protected DataSourceVO<?> getDataSource(JsonObject json, String name) throws JsonException {
        String xid = json.getString(name);
        if (xid == null)
            throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
        DataSourceVO<?> ds = new DataSourceService().getDataSource(xid);
        if (ds == null)
            throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
        return ds;
    }

    protected PublisherVO<?> getPublisher(JsonObject json, String name) throws JsonException {
        String xid = json.getString(name);
        if (xid == null)
            throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
        PublisherVO<?> pb = new PublisherDao().getPublisher(xid);
        if (pb == null)
            throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
        return pb;
    }

    protected int getMaintenanceEventId(JsonObject json, String name) throws JsonException {
        String xid = json.getString(name);
        if (xid == null)
            throw new LocalizableJsonException("emport.error.eventType.missing.reference", name);
        MaintenanceEventVO me = new MaintenanceEventDao().getMaintenanceEvent(xid);
        if (me == null)
            throw new LocalizableJsonException("emport.error.eventType.invalid.reference", name, xid);
        return me.getId();
    }
}
