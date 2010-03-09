package org.hyperic.hq.events.server.session;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hibernate.Util;
import org.hyperic.hq.events.shared.AlertManagerLocal;
import org.hyperic.hq.measurement.server.session.AlertConditionsSatisfiedZEvent;
import org.hyperic.hq.zevents.ZeventListener;

/**
 * Receives AlertConditionSatisfiedZEvents and forwards them to the AlertManager
 * for processing
 * @author jhickey
 *
 */
public class AlertConditionsSatisfiedListener implements ZeventListener {
    
    private static final int MAX_RETRIES = 3;
    private final Log _log = LogFactory.getLog(AlertConditionsSatisfiedListener.class);

    public void processEvents(List events) {
        AlertManagerLocal am = AlertManagerEJBImpl.getOne();
        
        for (Iterator it=events.iterator(); it.hasNext(); ) {
            AlertConditionsSatisfiedZEvent z = (AlertConditionsSatisfiedZEvent)it.next();
            // HQ-1905 need to retry due to potential StaleStateExceptions
            for (int ii=0; ii<MAX_RETRIES; ii++) {
                try {
                    am.fireAlert(z);
                    break;
                } catch (Throwable e) {
                    if ((ii+1) < MAX_RETRIES && Util.tranRolledBack(e)) {
                        String times = (MAX_RETRIES - ii == 1) ? "time" : "times";
                        _log.warn("Warning, exception occurred while running fireAlert.  will retry "
                                  + (MAX_RETRIES - (ii+1)) + " more " + times + ".  errorMsg: " + e);
                        continue;
                    } else {
                        _log.error("fireAlert threw an Exception, will not be retried",e);
	                    break;
                    }
                }
            }
        }
    }
    
    public String toString() {
        return "AlertConditionsSatisfiedListener";
    }

}
