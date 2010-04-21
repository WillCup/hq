/*                                                                 
 * NOTE: This copyright does *not* cover user programs that use HQ 
 * program services by normal system calls through the application 
 * program interfaces provided as part of the Hyperic Plug-in Development 
 * Kit or the Hyperic Client Development Kit - this is merely considered 
 * normal use of the program, and does *not* fall under the heading of 
 * "derived work". 
 *  
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc. 
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

package org.hyperic.hq.events.server.session;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The {@link AlertRegulator} has the responsibility of limiting the 
 * generation of alerts.
 */
public class AlertRegulator {
    private static final AlertRegulator INSTANCE = new AlertRegulator();
    private static final Log _log = LogFactory.getLog(AlertRegulator.class);
    
    private final Object LOCK = new Object();
    private boolean _alertsAllowed = true;
    
    public boolean alertsAllowed() {
        synchronized (LOCK) {
            return _alertsAllowed;
        }
    }

    public void setAlertsAllowed(boolean allowed) {
        synchronized (LOCK) {
            if (!allowed)
                _log.warn("Alerts have been globally disabled until the " +
                          "next server re-start");
            else
                _log.warn("Alerts have been globally re-enabled");
            _alertsAllowed = allowed;
        }
    }
    
    public static AlertRegulator getInstance() {
        return INSTANCE;
    }
}