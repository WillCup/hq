/**
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 *  "derived work".
 *
 *  Copyright (C) [2004-2011], VMware, Inc.
 *  This file is part of HQ.
 *
 *  HQ is free software; you can redistribute it and/or modify
 *  it under the terms version 2 of the GNU General Public License as
 *  published by the Free Software Foundation. This program is distributed
 *  in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more
 *  details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 *  USA.
 */

package org.hyperic.hq.product.shared;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hyperic.hq.appdef.server.session.AgentPluginStatus;
import org.hyperic.hq.appdef.server.session.AgentPluginStatusEnum;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.product.Plugin;

public interface PluginManager {

    Plugin getByJarName(String jarName);

    /**
     * Deploys the specified plugin to the hq-plugins dir.  Checks if the jar file is corrupt
     * and if the hq-plugin.xml is well-formed.  If these checks fail a {@link PluginDeployException}
     * will be thrown.
     * @param pluginInfo {@link Map} of {@link String} = filename to {@link Collection} of bytes
     * that represent the file contents
     * @throws {@link PluginDeployException} if the plugin file is corrupt and/or the hq-plugin.xml
     * is not well-formed.
     */
    void deployPluginIfValid(AuthzSubject subj, Map<String, byte[]> pluginInfo)
    throws PluginDeployException;

    /**
     * @return {@link Map} of {@link Integer} = pluginId to {@link Map} of
     * {@link AgentPluginStatusEnum} to {@link Integer} = count of number of agents that
     * match the status
     */
    Map<Integer, Map<AgentPluginStatusEnum, Integer>> getPluginRollupStatus();

    /**
     * @param pluginId - id associated with the {@link Plugin} object
     * @param {@link AgentPluginStatusEnum} array of status enums to match
     * @return {@link Collection} of {@link AgentPluginStatus} objects which match the pluginId and 
     * statuses params
     */
    Collection<AgentPluginStatus> getStatusesByPluginId(int pluginId,
                                                        AgentPluginStatusEnum ... statuses);

    /**
     * @return true if the server.pluginsync.enabled property is set to false or
     * setDisabledForPluginDeploymentMechanism(true) was called
     * @see #setDisabledForPluginDeploymentMechanism(boolean)
     */
    boolean isPluginDeploymentOff();

    /**
     * turns the Server -> Agent Plugin Sync mechanism on or off represented by the disabled flag
     */
    void setDisabledForPluginDeploymentMechanism(boolean disabled);

    Plugin getPluginById(Integer id);

    /**
     * @return {@link Map} of {@link Plugin} to {@link Collection} of {@link AgentPluginStatus}
     * where the plugin md5 checksum does not match the plugin's md5 checksum in the
     * EAM_AGENT_PLUGIN_STATUS table
     */
    Map<Plugin, Collection<AgentPluginStatus>> getOutOfSyncAgentsByPlugin();

    /**
     * @return {@link List} of {@link Plugin}s
     */
    List<Plugin> getAllPlugins();

    /**
     * @param agentId - id associated with the {@link Agent} object
     * @return {@link Collection} of {@link String} representing the pluginName from {@link Plugin}
     */
    Collection<String> getOutOfSyncPluginNamesByAgentId(Integer agentId);
    
    /**
     * Updates all the {@link AgentPluginStatus} objects associated with the agentId to the
     * specified status
     * @param status - {@link AgentPluginStatusEnum}
     * @param agentId - id associated with the {@link Agent} object
     * @param plugins - {@link Collection} of {@link Plugin}
     */
    void updateAgentPluginSyncStatusInNewTran(AgentPluginStatusEnum status, Integer agentId,
                                              Collection<Plugin> plugins);

    /**
     * Updates all the {@link AgentPluginStatus} objects associated with the agentId to the
     * specified status
     * @param status - {@link AgentPluginStatusEnum}
     * @param agentId - id associated with the {@link Agent} object
     * @param pluginFileNames - {@link Collection} of {@link String} = pluginFileName
     */
    void updateAgentPluginStatusByFileNameInNewTran(AgentPluginStatusEnum status, Integer agentId,
                                                    Collection<String> pluginFileNames);

    /**
     * Removes all plugins specified by the pluginFilenames collection
     * @param subj
     * @param pluginFileNames
     * @throws PermissionException
     */
    void removePlugins(AuthzSubject subj, Collection<String> pluginFileNames)
    throws PermissionException;

    /**
     * Removes all {@link AgentPluginStatus} objects associated with the agentId and pluginFileNames
     * @param agentId - id associated with the {@link Agent} object
     * @param pluginFileNames
     */
    void removeAgentPluginStatuses(Integer agentId, Collection<String> pluginFileNames);

    /**
     * @return {@link Set} of {@link Integer} which represents an agentId
     */
    Set<Integer> getAgentIdsInQueue();

    /**
     * Gets the agentIds being restarted by the SAPS mechanism
     * @return {@link Map} of {@link Integer} = agentId to {@link Long} = timestamp ms which
     * represents the time the agent restart command was issued
     */
    Map<Integer, Long> getAgentIdsInRestartState();

    /**
     * @return {@link Map} of {@link String} = pluginName to {@link Integer} = pluginId
     */
    Map<String, Integer> getAllPluginIdsByName();

    /**
     * Sets the disabled flag to true of the {@link Plugin}s represented by pluginIds
     * @param pluginIds - ids associated with {@link Plugin} objects
     */
    void markDisabled(Collection<Integer> pluginIds);

    /**
     * Sets the disabled flag to true of the {@link Plugin} object represented by pluginFileName
     * @param pluginFileName - pluginNames associated with a {@link Plugin}
     */
    void markDisabled(String pluginFileName);

    /**
     * Sets the disabled flag to false of the {@link Plugin} object represented by pluginName
     * @param pluginFileName - pluginNames associated with a {@link Plugin}
     */
    void markEnabled(String pluginName);

    /**
     * @return /path/to/WEB-INF/hq-plugins/
     */
    File getServerPluginDir();

    /**
     * @return parent dir of user.dir/hq-plugins/ basically, /cwd/../hq-plugins/
     */
    File getCustomPluginDir();

}
