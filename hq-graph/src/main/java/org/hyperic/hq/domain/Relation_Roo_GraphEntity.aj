// WARNING: DO NOT EDIT THIS FILE. THIS FILE IS MANAGED BY SPRING ROO.
// You may push code into the target .java compilation unit if you wish to edit any member(s).

package org.hyperic.hq.domain;

import org.neo4j.graphdb.Relationship;

privileged aspect Relation_Roo_GraphEntity {
    
    public Relation.new(Relationship r) {
        setUnderlyingState(r);
    }

}