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

package org.hyperic.hq.grouping.server.session;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.ejb.SessionContext;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.authz.shared.ResourceGroupManagerLocal;
import org.hyperic.hq.authz.shared.ResourceGroupManagerUtil;
import org.hyperic.hq.authz.shared.ResourceGroupValue;
import org.hyperic.hq.authz.shared.ResourceManagerLocal;
import org.hyperic.hq.authz.shared.ResourceManagerUtil;
import org.hyperic.hq.authz.shared.ResourceTypeValue;
import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.authz.shared.RoleValue;
import org.hyperic.hq.common.SystemException;
import org.hyperic.hq.grouping.shared.GroupCreationException;
import org.hyperic.hq.grouping.shared.GroupDuplicateNameException;
import org.hyperic.hq.grouping.shared.GroupEntry;
import org.hyperic.hq.grouping.shared.GroupModificationException;
import org.hyperic.hq.grouping.shared.GroupNotFoundException;
import org.hyperic.hq.grouping.shared.GroupValue;
import org.hyperic.hq.grouping.shared.GroupVisitorException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;
import org.hyperic.util.pager.Pager;
import org.hyperic.util.pager.SortAttribute;

/** *********************************************************
  * GroupManager provides "management level" support for group
  * operations.
  *

  * @ejb:bean name="GroupManager"
  *           jndi-name="ejb/grouping/GroupManager"
  *           local-jndi-name="LocalGroupManager"
  *           view-type="both"
  *           type="Stateless"
  */
public class GroupManagerEJBImpl implements javax.ejb.SessionBean {
    private javax.ejb.SessionContext ctx;
    private final Log log = LogFactory.getLog(
        "org.hyperic.hq.grouping.server.session.GroupManagerEJBImpl");
    public final String authzResourceGroupName = "covalentAuthzResourceGroup";


    /**
     *  Create a persistent group according to options specified.
     * 
     * @param spider subject
     * @param group value object ref to populate. Visitors can be pre-registered
     * @return GroupValue object.
     * @throws GroupCreationException during finding of resource types and
     *         creation of dependent session EJBs.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public GroupValue createGroup (AuthzSubjectValue subject,GroupValue retVal)
        throws GroupCreationException, GroupDuplicateNameException {

        try {
            ResourceGroupValue  rgVo  = null;
            RoleValue[]         roArr = null;
            ResourceValue[]     reArr = null;

            // To avoid groups with duplicate names, we're now performing a
            // a case-insensitive name based find before creation.
            // PR:5059
            if (isGroupNameExists(subject,retVal)) {
                throw new GroupDuplicateNameException ("A ResourceGroup "+
                                                       "with same name "+
                                                       "exists.");
            }

            /* Create the resource group. */
            rgVo = new ResourceGroupValue();
            rgVo.setName            ( retVal.getName() );
            rgVo.setDescription     ( retVal.getDescription() );
            rgVo.setLocation        ( retVal.getLocation() );
            rgVo.setGroupType       (retVal.getGroupType());
            rgVo.setGroupEntType    ( retVal.getGroupEntType() );
            rgVo.setGroupEntResType ( retVal.getGroupEntResType() );
            rgVo.setClusterId       ( retVal.getClusterId() );

            ResourceGroupManagerLocal rgmLoc = getResourceGroupManager();
            rgVo = rgmLoc.createResourceGroup(subject, rgVo, roArr, reArr);

            /* Create our return group vo */
            retVal.setId            ( rgVo.getId() );
            retVal.setName          ( rgVo.getName() );
            retVal.setDescription   ( rgVo.getDescription() );
            retVal.setLocation      ( rgVo.getLocation() );
            retVal.setGroupType     ( rgVo.getGroupType() );
            retVal.setSubject       ( subject );
            retVal.setCTime         ( rgVo.getCTime() );
            retVal.setMTime         ( rgVo.getMTime() );
            retVal.setModifiedBy    ( rgVo.getModifiedBy() );
            retVal.setOwner         ( subject.getName() );

           // Here's where we add our own group resource to our group.
            ResourceManagerLocal rmLoc = getResourceManager();
            ResourceTypeValue typeVal = rmLoc.findResourceTypeByName(
                authzResourceGroupName);
            ResourceValue resVal =
                rmLoc.findResourceByInstanceId(typeVal,rgVo.getId());
            rgmLoc.addResource(subject,rgVo,rgVo.getId(),typeVal);

        }
        catch (CreateException e) {
            log.error("Caught entity CreateException creating ResourceGroup",e);
            throw new GroupCreationException ("Caught entity CreateException "+
                                              "creating ResourceGroup", e);
        }
        catch (PermissionException pe) {
            // This should NOT occur. Anyone can create groups.
            log.error("Caught PermissionException during "+
                      "self-assignment of group resource to group",pe);
            throw new GroupCreationException ("Caught PermissionException "+
                "during self-assignment of resource to group");
        }
        catch (FinderException fe) {
            log.error("GroupManager caught underlying finder exc "+
                          "with findResourceTypeByName(): "+fe.getMessage());
            throw new GroupCreationException (fe.getMessage());
        }
        return retVal;
    }

    private String fetchGroupOwner(Integer gid) {
        String retVal = "";
        try {
            ResourceGroupManagerLocal rgmLoc =
                getResourceGroupManager();
            retVal = rgmLoc.getResourceGroupOwner(gid).getName();
        }catch (Exception e){
            log.debug("Unable to set subject during create");
            log.debug(e);
        }
        return retVal;
    }

    /**
     *  Lookup and return a group specified by the id contained within the
     *  value object provided.
     * 
     * @param spider subject
     * @param the group value - can have registered visitors.
     * @param PageControl for paging.
     * @return GroupValue
     * @throws PermissionException - you must be owner or authorized to access
     *         a particular group.
     * @throws GroupNotFoundException when group is non-existent.
     * @ejb:interface-method
     * @ejb:transaction type="SUPPORTS"
     */
    public GroupValue findGroup (AuthzSubjectValue subject, GroupValue retVal,
                                 PageControl pc)
        throws PermissionException, GroupNotFoundException {
        try {
            ResourceGroupManagerLocal rgmLoc = getResourceGroupManager();

            ResourceGroupValue rgVo;
            if (retVal.getName () != null) {
                rgVo = rgmLoc.findResourceGroupByName(subject,
                                                      retVal.getName());
            } 
            else {
                rgVo = rgmLoc.findResourceGroupById(subject,retVal.getId());
            }

            /* Create our return group vo */
            retVal.setId                ( rgVo.getId() );
            retVal.setName              ( rgVo.getName() );
            retVal.setDescription       ( rgVo.getDescription() );
            retVal.setLocation          ( rgVo.getLocation() );
            retVal.setGroupType         ( rgVo.getGroupType() );
            retVal.setSubject           ( subject );
            retVal.setGroupEntType      ( rgVo.getGroupEntType() );
            retVal.setGroupEntResType   ( rgVo.getGroupEntResType() );
            retVal.setClusterId         ( rgVo.getClusterId() );
            retVal.setMTime             ( rgVo.getMTime() );
            retVal.setCTime             ( rgVo.getCTime() );
            retVal.setModifiedBy        ( rgVo.getModifiedBy() );
            retVal.setOwner             ( fetchGroupOwner(rgVo.getId()) );

            /* Add the group members*/
            PageList resList = rgmLoc.getResources(subject, rgVo, pc);

            for (Iterator i = resList.iterator(); i.hasNext();) {
                ResourceValue resVo = (ResourceValue) i.next();
                GroupEntry ge =
                    new GroupEntry(resVo.getInstanceId(),
                                   resVo.getResourceTypeValue().getName());
                retVal.addEntry(ge);
            }
            retVal.setTotalSize( resList.getTotalSize() );
        }
        catch (FinderException fe) {
            log.debug("GroupManager caught underlying finder exc "+
                      "attempting to findResourceGroupById(): "+
                          fe.getMessage());
            throw new GroupNotFoundException("The specified group "+
                          "does not exist.",fe);
        }
        catch (NamingException ne) {
            log.error("Caught NamingException in resource group manager",ne);
            throw new SystemException ("Caught NamingException "+
                "in resource group manager");
        }
        return retVal;
    }

    /**
     *  Lookup and return all groups that include a resource.
     * 
     * @param spider subject
     * @param GroupValue for filtering
     * @param PageControl for paging
     * @return PageList
     * @throws FinderException
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList findAllGroups (AuthzSubjectValue subject,
                                   GroupValue gv, PageControl pc)
        throws  GroupNotFoundException, PermissionException,
                CloneNotSupportedException {
        return findAllGroups(subject,gv,null,pc);
    }

    /**
     *  Lookup and return a group from the specified identifier.
     * 
     * @param spider subject
     * @param GroupValue for filtering
     * @param ResourceValue for filtering
     * @param PageControl for paging
     * @return PageList
     * @throws FinderException
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public PageList findAllGroups (AuthzSubjectValue subject, GroupValue gv, 
                                   ResourceValue rv, PageControl pc) 
        throws  GroupNotFoundException, PermissionException,
                CloneNotSupportedException {
        PageList retVal = null;

        try {
            ResourceGroupManagerLocal rgmLoc =
                getResourceGroupManager();

            List rgList;
            if (rv != null) {
                rgList = rgmLoc.getAllResourceGroupsResourceInclusive(subject,
                                                                      pc,rv);
            } else {
                rgList = rgmLoc.getAllResourceGroups(subject,pc,true);
            }

            List toBePaged = new ArrayList();

            for (Iterator i=rgList.iterator();i.hasNext();) {
                ResourceGroupValue rgVo =
                    (ResourceGroupValue) i.next();

                //  Filter out the system group entries.
                if (rgVo.getSystem())
                    continue;

                // Create a new group value to populate
                GroupValue gVal = (GroupValue)gv.clone();

                /* Create our return group vo */
                gVal.setId          (rgVo.getId());
                gVal.setName        (rgVo.getName());
                gVal.setDescription (rgVo.getDescription());
                gVal.setLocation    (rgVo.getLocation());
                gVal.setGroupType   (rgVo.getGroupType());
                gVal.setSubject     (subject);
                gVal.setGroupEntType(rgVo.getGroupEntType());
                gVal.setGroupEntResType(rgVo.getGroupEntResType());
                gVal.setClusterId   ( rgVo.getClusterId());
                gVal.setMTime       ( rgVo.getMTime() );
                gVal.setCTime       ( rgVo.getCTime() );
                gVal.setModifiedBy  ( rgVo.getModifiedBy() );
                gVal.setOwner       ( fetchGroupOwner(rgVo.getId()) );

                /* Add the group members */
                PageList resList = rgmLoc.getResources(subject, rgVo, 
                                                       PageControl.PAGE_ALL);
    
                for (Iterator iter=resList.iterator();iter.hasNext();) {
                    ResourceValue resVo = (ResourceValue) iter.next();
                    GroupEntry ge = 
                        new GroupEntry(resVo.getInstanceId(),
                                       resVo.getResourceTypeValue().getName());
                    gVal.addEntry(ge);
                }
                toBePaged.add(gVal);
            }
            retVal = getPageList(toBePaged, pc);

        }
        catch (FinderException fe) {
            log.debug("Finder caught, no groups for this subject: "+
                      fe.getMessage());
            throw new GroupNotFoundException("No groups exist for specified "+
                                            "subject.",fe);
        }
        catch (PermissionException pe) {
            log.error("GroupManager caught PermissionException: "+
                      pe.getMessage());
            throw pe;
        }
        catch (NamingException ne) {
            log.error("Caught NamingException in resource group manager",ne);
            throw new SystemException ("Caught NamingException "+
                "in resource group manager");
        }
        return retVal;
    }

    // For group paging only. Not members!
    private PageList getPageList (Collection coll, PageControl pc)
    {
        Pager defaultPager = Pager.getDefaultPager();
        PageControl.initDefaults(pc, SortAttribute.RESGROUP_NAME);
        return  defaultPager.seek(coll,pc.getPagenum(),pc.getPagesize());
    }

    /**
     *  Saves the contents of a persistent group back the persistent
     *  storage.  Any registered visitors are executed prior to
     *  initiating save operation. Contents of group overwrite any
     *  previous group data.
     * 
     * @param spider subject
     * @param group value object
     * @throws GroupModificationException when group save fails.
     * @throws PermissionException when consumer is not owner or priv'd.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void saveGroup (AuthzSubjectValue subject, GroupValue groupVo) 
        throws  GroupModificationException,
                GroupDuplicateNameException,
                PermissionException,
                GroupVisitorException
    {
        try {

            ResourceGroupManagerLocal rgmLoc = getResourceGroupManager();
            
            // First, lookup the group to preserve non-updatable fields
            ResourceGroupValue rgVo = 
                rgmLoc.findResourceGroupById(subject, groupVo.getId() );

            // If they've changed the group name then we need to ensure
            // they haven't chosen one that already exists. Perform a 
            // case-insensitive name based find before saving. PR:5208
            if (!groupVo.getName().toLowerCase().equals( 
                    rgVo.getName().toLowerCase()) &&
                isGroupNameExists(subject,groupVo)) {
                throw new GroupDuplicateNameException ("A ResourceGroup "+
                                                       "with same name "+
                                                       "exists.");
            }

            // Apply any last minute operations.
            groupVo.visit();

            rgVo.setName        (groupVo.getName());
            rgVo.setDescription (groupVo.getDescription());
            rgVo.setLocation    (groupVo.getLocation());
            rgVo.setMTime       ( new Long(System.currentTimeMillis()) );
            rgVo.setModifiedBy  (subject.getName());
            rgVo.setClusterId   (groupVo.getClusterId());

            rgmLoc.saveResourceGroup(subject,rgVo);

            // Fetch existing entries
            PageList resList = rgmLoc.getResources(subject,rgVo, 
                PageControl.PAGE_ALL);


            // Now apply deletions
            Iterator iter = resList.iterator();
            List resToDel = new ArrayList();
            while (iter.hasNext()) {
                ResourceValue resVo = (ResourceValue) iter.next();
                GroupEntry ent =
                    new GroupEntry(resVo.getInstanceId(),
                                   resVo.getResourceTypeValue().getName());

                if (!groupVo.existsEntry(ent)) {
                    resToDel.add(resVo);
                }
            }
            
            // only call remove if there's stuff to delete
            if(!resToDel.isEmpty()) {
                ResourceValue[] resToDelArr = (ResourceValue[])
                    resToDel.toArray(new ResourceValue[]{});
                rgmLoc.removeResources(subject,rgVo,resToDelArr);
            }
            // now apply additions
            iter = groupVo.getGroupEntries().iterator();
            List resToAdd = new ArrayList();
            while (iter.hasNext()) {
                GroupEntry groupEntry = (GroupEntry)iter.next();
                if (! resourceListContainsGroupEntry(resList,groupEntry) ) {
                    ResourceValue trv = getResourceByInstanceId(
                                            groupEntry.getType(),
                                            groupEntry.getId());
                    resToAdd.add(trv);
                }
            }
            ResourceValue[] resToAddArr = (ResourceValue[])
                resToAdd.toArray(new ResourceValue[]{});
            rgmLoc.addResources(subject,rgVo,resToAddArr);
        }
        catch (FinderException fe) {
            log.error("GroupManager.saveGroup caught underlying finder exc: "+
                      "this may happen in authz during permission checks if "+
                      "person or resource isn't found"+ fe.getMessage());
            throw new GroupModificationException (fe);
        }
        catch (PermissionException pe) {
            log.error("GroupManager caught PermissionException: "+
                      pe.getMessage(),pe);
            throw pe;
        }
        catch (NamingException ne) {
            log.error("Caught NamingException in resource group manager",ne);
            throw new SystemException ("Caught NamingException "+
                "in resource group manager");
        }
    }

    private boolean resourceListContainsGroupEntry (List list, GroupEntry ge) {
        try {
            Iterator i=list.iterator();
            while (i.hasNext()) {
                ResourceValue rv = (ResourceValue) i.next();
                if (rv.getInstanceId().intValue() == ge.getId().intValue() &&
                    rv.getResourceTypeValue().getName().equals(ge.getType()))
                    return true;
            }
        } catch (Exception e) {
            log.debug("ResourceListContainsGroupEntry caught exception:"+
                e.getMessage());
        }
        return false;
    }
    /**
     *  Removes a group specified by id.
     * 
     * @param spider subject
     * @param group identifier
     * @throws PermissionException when consumer is not owner or priv'd.
     * @ejb:interface-method
     * @ejb:transaction type="Required"
     */
    public void deleteGroup (AuthzSubjectValue subject, Integer groupId)
        throws GroupNotFoundException, PermissionException {
        try {

            ResourceGroupManagerLocal rgmLoc =
                getResourceGroupManager();

            ResourceGroupValue rgVo = new ResourceGroupValue();
            rgVo.setId(groupId);

            // First, remove existing resources...
            rgmLoc.removeResourceGroup(subject,rgVo);
        }
        catch (FinderException e) {
            log.error("Caught FinderException. Group doesn't exist");
            throw new GroupNotFoundException ("Couldn't delete non-"+
                                              "existent group.");
        }
        catch (RemoveException e) {
            log.error("Caught RemoveException on EJB remove.");
            throw new SystemException ("Caught exception during "+
                                          "removal of group.");
        }
        catch (PermissionException pe) {
            log.error("GroupManager caught PermissionException: "+
                      pe.getMessage());
            throw pe;
        }
        catch (NamingException ne) {
            log.error("Caught NamingException in resource group manager",ne);
            throw new SystemException ("Caught NamingException "+
                "in resource group manager");
        }
    }

    /**
     * Change owner of a group.
     * @param current owner subject
     * @param group value
     * @param newOwner subject
     * @ejb:interface-method
     * @ejb:transaction type="REQUIRED"
     */
    public GroupValue changeGroupOwner(AuthzSubjectValue subject,
                                       GroupValue groupVo,
                                       AuthzSubjectValue newOwner)
        throws GroupNotFoundException, PermissionException {
        try {
            // now get its authz resource
            ResourceValue authzRes = getResourceByInstanceId(
                  AuthzConstants.groupResourceTypeName, groupVo.getId() );

            // change the authz owner
            getResourceManager().setResourceOwner(subject, authzRes, newOwner);

            // update the modified by attr in the resource group table
            getResourceGroupManager().setGroupModifiedBy(subject,
                                                         groupVo.getId());

            // Set the GroupValue to be returned
            groupVo.setOwner(newOwner.getName());
            groupVo.setModifiedBy(subject.getName());
        }
        catch (FinderException e) {
            log.error("Unable to find resource group to change owner.");
            throw new GroupNotFoundException ("Unable to lookup ResourceGroup" +
                                              " for ownership change");
        }
        catch (NamingException e) {
            log.error("Unable to lookup GroupManagerLocalHome");
            throw new SystemException("Unable to lookup GroupManagerLocalHome");
        }
        return groupVo;
    }

    /* Get the authz resource type value  */
    protected ResourceTypeValue getResourceType(String resType)
        throws FinderException {
        return getResourceManager().findResourceTypeByName(resType);
    }

    private ResourceGroupManagerLocal getResourceGroupManager() {
        try {
            return ResourceGroupManagerUtil.getLocalHome().create();
        }
        catch (NamingException ne) {
            log.error("Unable to lookup ResourceGroupManagerLocalHome "+
                      "interface", ne);
            throw new SystemException ("Unable to lookup "+
                                          "ResourceGroupManagerLocal"+
                                          "Home interface",ne);
        }
        catch (CreateException ce) {
            log.error("Unable to create ResourceGroupManagerLocal", ce);
            throw new SystemException ("Unable to create Resource"+
                                          "GroupManagerLocal interface",ce);
        }
    }

    /* Get the local interace of the Resource Manager  */
    private ResourceManagerLocal getResourceManager() {
        try {
            return ResourceManagerUtil.getLocalHome().create();
        }
        catch (NamingException ne) {
            log.error("Unable to lookup ResourceManagerLocalHome "+
                      "interface", ne);
            throw new SystemException ("Unable to lookup "+
                                          "ResourceManagerLocal"+
                                          "Home interface",ne);
        }
        catch (CreateException ce) {
            log.error("Unable to create ResourceManagerLocal", ce);
            throw new SystemException ("Unable to create Resource"+
                                          "ManagerLocal interface",ce);
        }
    }

    private ResourceValue getResourceByInstanceId(String type, Integer id)
        throws FinderException {
        return getResourceManager()
            .findResourceByInstanceId(getResourceType(type),id);
    }

    // To avoid groups with duplicate names, we're now performing a
    // a case-insensitive name based find before creation.
    // PR:5059
    // Simply lookup group by name throwing appropriate exceptions.
    private boolean isGroupNameExists(AuthzSubjectValue subject, 
                                      GroupValue gVo) {
        boolean retVal;

        try {
            findGroup (subject,gVo,PageControl.PAGE_ALL);
            retVal = true;
        } catch (GroupNotFoundException e) {
            retVal = false;
        } catch (PermissionException pe) {
            // this happens when a group with same name exists and user
            // is unauthorized to view it.
            retVal = true;
        }
        return retVal;
    }

    /**
     * @see javax.ejb.SessionBean#ejbCreate()
     * @ejb:create-method
     * @throws CreateException
     */
    public void ejbCreate() throws CreateException {
    }

    /**
     * @see javax.ejb.SessionBean#ejbPostCreate()
     */
    public void ejbPostCreate() {}

    /**
     * @see javax.ejb.SessionBean#ejbActivate()
     */
    public void ejbActivate() {}

    /**
     * @see javax.ejb.SessionBean#ejbPassivate()
     */
    public void ejbPassivate() {}

    /**
     * @see javax.ejb.SessionBean#ejbRemove()
     */
    public void ejbRemove() {
        this.ctx = null;
    }

    /**
     * @see javax.ejb.SessionBean#setSessionContext(SessionContext)
     */
    public void setSessionContext(SessionContext ctx)
        throws EJBException, RemoteException {
        this.ctx = ctx;
    }
}
