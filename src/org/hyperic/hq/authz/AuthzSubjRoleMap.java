package org.hyperic.hq.authz;
// Generated Oct 17, 2006 12:45:08 PM by Hibernate Tools 3.1.0.beta4



/**
 * AuthzSubjRoleMap generated by hbm2java
 */
public class AuthzSubjRoleMap  implements java.io.Serializable {

    // Fields    

     private AuthzSubjRoleMapId id;
     private Integer cid;

     // Constructors

    /** default constructor */
    public AuthzSubjRoleMap() {
    }

	/** minimal constructor */
    public AuthzSubjRoleMap(AuthzSubjRoleMapId id) {
        this.id = id;
    }
    /** full constructor */
    public AuthzSubjRoleMap(AuthzSubjRoleMapId id, Integer cid) {
        this.id = id;
        this.cid = cid;
    }
    
   
    // Property accessors
    public AuthzSubjRoleMapId getId() {
        return this.id;
    }
    
    public void setId(AuthzSubjRoleMapId id) {
        this.id = id;
    }
    public Integer getCid() {
        return this.cid;
    }
    
    public void setCid(Integer cid) {
        this.cid = cid;
    }




}


