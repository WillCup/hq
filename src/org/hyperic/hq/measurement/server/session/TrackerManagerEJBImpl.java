/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004-2008], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.measurement.server.session;

import javax.ejb.CreateException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.agent.AgentConnectionException;
import org.hyperic.hq.agent.AgentRemoteException;
import org.hyperic.hq.appdef.server.session.PlatformManagerEJBImpl;
import org.hyperic.hq.appdef.shared.AgentNotFoundException;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefEntityNotFoundException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.measurement.agent.client.MeasurementCommandsClient;
import org.hyperic.hq.measurement.agent.client.MeasurementCommandsClientFactory;
import org.hyperic.hq.measurement.shared.TrackerManagerLocal;
import org.hyperic.hq.measurement.shared.TrackerManagerUtil;
import org.hyperic.hq.product.ConfigTrackPlugin;
import org.hyperic.hq.product.LogTrackPlugin;
import org.hyperic.hq.product.PluginException;
import org.hyperic.hq.product.ProductPlugin;
import org.hyperic.util.config.ConfigResponse;

/**
 * The tracker manager handles sending agents add and remove operations
 * for the log and config track plugins.
 *
 * @ejb:bean name="TrackerManager"
 *      jndi-name="ejb/measurement/TrackerManager"
 *      local-jndi-name="LocalTrackerManager"
 *      view-type="local"
 *      type="Stateless"
 */
public class TrackerManagerEJBImpl 
    extends SessionEJB 
    implements SessionBean 
{
    private final Log log = LogFactory.getLog(TrackerManagerEJBImpl.class);

    /**
     * @ejb:create-method
     */
    public void ejbCreate() throws CreateException {}

    public void ejbPostCreate() {}
    public void ejbActivate() {}
    public void ejbPassivate() {}
    public void ejbRemove() {}
    public void setSessionContext(SessionContext ctx) {}

    private MeasurementCommandsClient getClient(AppdefEntityID aid)
        throws PermissionException, AgentNotFoundException {
        
        return MeasurementCommandsClientFactory.getInstance().getClient(aid);
    }

    /** 
     * Enable log or config tracking for the given resource
     */
    private void trackPluginAdd(AuthzSubject subject, AppdefEntityID id,
                                String pluginType, ConfigResponse response)
        throws PermissionException, PluginException
    {
        try {
            MeasurementCommandsClient client = getClient(id);
            String resourceName = PlatformManagerEJBImpl.getOne()
                .getPlatformPluginName(id);

            client.addTrackPlugin(id.getAppdefKey(), pluginType, 
                                  resourceName, response);        
        } catch (AppdefEntityNotFoundException e) {
            throw new PluginException("Entity not found: " +
                                      e.getMessage());
        } catch (AgentNotFoundException e) {
            throw new PluginException("Agent error: " + e.getMessage());
        } catch (AgentConnectionException e) {
            throw new PluginException("Agent error: " + e.getMessage());
        } catch (AgentRemoteException e) {
            throw new PluginException("Agent error: " + e.getMessage());
        }
    }

    /**
     * Disable log or config tracking for the given resource
     */
    private void trackPluginRemove(AuthzSubject subject, AppdefEntityID id,
                                   String pluginType)
        throws PermissionException, PluginException
    {
        try {
            MeasurementCommandsClient client = getClient(id);
            client.removeTrackPlugin(id.getAppdefKey(), pluginType);
        } catch (AgentNotFoundException e) {
            throw new PluginException("Agent error: " + e.getMessage());
        } catch (AgentConnectionException e) {
            throw new PluginException("Agent error: " + e.getMessage());
        } catch (AgentRemoteException e) {
            throw new PluginException("Agent error: " + e.getMessage());
        }
    }

    /**
     * Enable log and config tracking for a resource if it has been enabled.
     *
     * @ejb:interface-method
     */
    public void enableTrackers(AuthzSubject subject, AppdefEntityID id,
                               ConfigResponse config)
        throws PermissionException, PluginException
    {
        if (LogTrackPlugin.isEnabled(config, id.getType())) {
            trackPluginAdd(subject, id, ProductPlugin.TYPE_LOG_TRACK, config);
        }

        if (ConfigTrackPlugin.isEnabled(config, id.getType())) {
            trackPluginAdd(subject, id, ProductPlugin.TYPE_CONFIG_TRACK, config);
        }
    }

    /**
     * Disable log and config tracking for a resource.
     *
     * @ejb:interface-method
     */
    public void disableTrackers(AuthzSubject subject, AppdefEntityID id,
                                ConfigResponse config)
        throws PermissionException, PluginException
    {
        if (LogTrackPlugin.isEnabled(config, id.getType())) {
            trackPluginRemove(subject, id, ProductPlugin.TYPE_LOG_TRACK);
        }

        if (ConfigTrackPlugin.isEnabled(config, id.getType())) {
            trackPluginRemove(subject, id, ProductPlugin.TYPE_CONFIG_TRACK);
        }
    }

    /**
     * Toggle log and config tracking for the resource.
     *
     * @ejb:interface-method
     */
    public void toggleTrackers(AuthzSubject subject, AppdefEntityID id,
                               ConfigResponse config)
        throws PermissionException, PluginException
    {
        if (LogTrackPlugin.isEnabled(config, id.getType())) {
            trackPluginAdd(subject, id, ProductPlugin.TYPE_LOG_TRACK, config);
        } else {
            trackPluginRemove(subject, id, ProductPlugin.TYPE_LOG_TRACK);
        }

        if (ConfigTrackPlugin.isEnabled(config, id.getType())) {
            trackPluginAdd(subject, id, ProductPlugin.TYPE_CONFIG_TRACK, config);
        } else {
            trackPluginRemove(subject, id, ProductPlugin.TYPE_CONFIG_TRACK);
        }
    }
    
    public static TrackerManagerLocal getOne() {
        try {
            return TrackerManagerUtil.getLocalHome().create();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }
}