package org.hyperic.hq.api.transfer.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hyperic.hq.api.model.Notification;
import org.hyperic.hq.api.model.NotificationType;
import org.hyperic.hq.api.model.NotificationsGroup;
import org.hyperic.hq.api.model.NotificationsReport;
import org.hyperic.hq.api.model.ResourceDetailsType;
import org.hyperic.hq.api.transfer.ResourceTransfer;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.notifications.InternalNotificationReport;
import org.hyperic.hq.notifications.model.BaseNotification;
import org.hyperic.hq.notifications.model.CreatedResourceNotification;
import org.hyperic.hq.notifications.model.InternalResourceDetailsType;
import org.hyperic.hq.notifications.model.MetricNotification;
import org.hyperic.hq.notifications.model.RemovedResourceNotification;
import org.hyperic.hq.notifications.model.ResourceChangedContentNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationsMapper {
    @Autowired
    protected ResourceMapper rscMapper;
    @Autowired
    protected MeasurementMapper mtmtMapper;
    @Autowired
    protected ExceptionToErrorCodeMapper errorHandler ;

    public NotificationsReport toNotificationsReport(final AuthzSubject subject, ResourceTransfer resourceTransfer, InternalNotificationReport inr) {
        NotificationsReport res = new NotificationsReport(this.errorHandler);
        Map<Integer,List<? extends BaseNotification>> regToNS = inr.getNotifications();
        Map<Integer,InternalResourceDetailsType> internalResourceDetailsType = inr.getResourceDetailsType();
        if (regToNS==null || regToNS.isEmpty()) {
            return new NotificationsReport(null);
        }
        List<Notification> creationNotifications = null;
        List<Notification> updateNotifications = null;
        List<Notification> removalNotifications = null;
        
        for(Map.Entry<Integer,List<? extends BaseNotification>> regToNSE:regToNS.entrySet()) {
            Integer regID = regToNSE.getKey();
            List<? extends BaseNotification> ns = regToNSE.getValue();
            ResourceDetailsType resourceDetailsType = ResourceDetailsType.valueOf(internalResourceDetailsType.get(regID));

            for(BaseNotification bn:ns) {
                try {
                    // expensive for many notifications, the 'instance of' should be used only in the polling mechanism
                    if (bn instanceof MetricNotification) {
                        if (creationNotifications==null) {
                            creationNotifications = new ArrayList<Notification>();
                        }
                        creationNotifications.add(this.mtmtMapper.toMetricWithId((MetricNotification)bn,regID));
                    } else if (bn instanceof CreatedResourceNotification) {
                        if (creationNotifications==null) {
                            creationNotifications = new ArrayList<Notification>();
                        }
                        creationNotifications.add(this.rscMapper.toResource(subject, resourceTransfer, resourceDetailsType,(CreatedResourceNotification )bn,regID));
                    } else if (bn instanceof RemovedResourceNotification) {
                        if (removalNotifications==null) {
                            removalNotifications = new ArrayList<Notification>();
                        }
                        removalNotifications.add(this.rscMapper.toResource((RemovedResourceNotification) bn,regID));
                    } else if (bn instanceof ResourceChangedContentNotification) {
                        if (updateNotifications==null) {
                            updateNotifications = new ArrayList<Notification>();
                        }
                        updateNotifications.add(this.rscMapper.toChangedResourceContent(resourceDetailsType, (ResourceChangedContentNotification) bn,regID));
                    }
                } catch (Throwable t) {
                    //TODO~ put errors in failed resource/failed metrics
                }
            }
        }
        List<NotificationsGroup> ngList = res.getNotificationsGroupList();
        if (creationNotifications!=null) {
            NotificationsGroup ng = new NotificationsGroup(NotificationType.Create);
            ng.setNotifications(creationNotifications);
            ngList.add(ng);
        }
        if (removalNotifications!=null) {
            NotificationsGroup ng = new NotificationsGroup(NotificationType.Delete);
            ng.setNotifications(removalNotifications);
            ngList.add(ng);
        }
        if (updateNotifications!=null) {
            NotificationsGroup ng = new NotificationsGroup(NotificationType.Update);
            ng.setNotifications(updateNotifications);
            ngList.add(ng);
        }

        return res;
    }
}