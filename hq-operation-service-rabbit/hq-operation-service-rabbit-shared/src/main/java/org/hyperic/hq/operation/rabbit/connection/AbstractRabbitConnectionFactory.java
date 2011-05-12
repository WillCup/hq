/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2009-2010], VMware, Inc.
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

package org.hyperic.hq.operation.rabbit.connection;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.PossibleAuthenticationFailureException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Helena Edelson
 */
abstract class AbstractRabbitConnectionFactory extends ConnectionFactory {

    protected final Log logger = LogFactory.getLog(getClass());

    private final Map<List<Address>, Connection> connections = new ConcurrentHashMap<List<Address>, Connection>();

    private final Object monitor = new Object();

    /**
     * Creates an instance of ConnectionFactory without credentials
     * for non-authenticated agent operations.
     */
    public AbstractRabbitConnectionFactory() {
        super();
    }

    /**
     * Creates an instance of ConnectionFactory with enforced credentials
     * for authenticated agent operations.
     * @param username
     * @param password
     */
    public AbstractRabbitConnectionFactory(String username, String password) {
        this();
        setUsername(username);
        setPassword(password);
    }
 
    @PreDestroy
    final void closeConnection() {
        synchronized (monitor) {
            for (Connection connection : connections.values()) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        }
    }

    @Override
    public final Connection newConnection() throws IOException {
        return (newConnection(new Address[]{new Address(getHost(), getPort())}));
    }

    @Override
    public final Connection newConnection(Address[] addrs) throws IOException {
        List<Address> addressList = Arrays.asList(addrs);

        synchronized (monitor) {
            Connection connection = connections.get(addressList);

            if (connection == null) {
                logger.debug("Creating a new connection.");
                connection = createConnection(addrs);
                connections.put(addressList, connection);
            }
            return connection;
        }
    }

    protected final Connection doNewConnection(Address[] addrs) throws IOException {
        return super.newConnection(addrs);
    }

    protected abstract Connection createConnection(Address[] addrs) throws IOException;

    /**
     * Essentially a ping to see if a broker can be reached.
     * @param addrs an address array of ip/port to connect to
     * @return ConnectionStatus with throwable reason if not
     * and a boolean active - true if we could connect.
     */
    public ConnectionStatus isActive(Address[] addrs) {
        Throwable reason = null;
        boolean active = false;

        try {
            Connection c = newConnection(addrs);
            active = c != null && c.isOpen();
        } catch (Throwable e) {
            reason = e;
            translate(e, addrs);
        }
        return new ConnectionStatus(reason, active);
    }

    private void translate(Throwable error, Address[] addrs) {
        if (error instanceof PossibleAuthenticationFailureException) {
            logger.error("can not connect with credentials: " + getUsername() + ":" + getPassword());
        } else if (error instanceof ConnectException) {
            logger.error("can not connect with at least one of the following: " + StringUtils.arrayToCommaDelimitedString(addrs));
        } else {
            logger.error(error.getMessage());
        }
    }
}
