package org.hyperic.hq.operation.rabbit.mapping;

import org.hyperic.hq.operation.OperationData;
import org.hyperic.hq.operation.OperationSupported;
import org.hyperic.hq.operation.rabbit.util.RoutingType;

/**
 * @author Helena Edelson
 */
public interface OperationRoutingSupported extends OperationSupported {

    /**
     * Tests whether the operation has been registered with this handler
     * @param operation The operation name
     * @return Returns true if the operation name is a key in the handler's mapping, false if not
     */
    boolean supports(OperationData operation, RoutingType type);

}