/**
 * NOTE: This copyright does *not* cover user programs that use Hyperic
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 *  Copyright (C) [2010], VMware, Inc.
 *  This file is part of Hyperic.
 *
 *  Hyperic is free software; you can redistribute it and/or modify
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
 *
 */
package org.hyperic.hq.plugin.rabbitmq.core;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.product.PluginException;
import org.hyperic.util.config.ConfigResponse;

/**
 * A HypericRabbitAdmin is created for each node/virtualHost.
 * HypericRabbitAdmin
 * @author Helena Edelson
 */
public class HypericRabbitAdmin {

    private static final Log logger = LogFactory.getLog(HypericRabbitAdmin.class);
    private final HttpClient client;
    private String node;

    public HypericRabbitAdmin(Properties props) {
        client = new HttpClient();

        client.getState().setCredentials(
                new AuthScope("192.168.183.140", 55672, "Management: Web UI"),
                new UsernamePasswordCredentials("guest", "guest"));

        client.getHttpConnectionManager().getParams().setConnectionTimeout(5000);

        this.node = props.getProperty("node");
    }

    public HypericRabbitAdmin(ConfigResponse props) {
        this(props.toProperties());
    }

    public void destroy() {
        logger.debug("[HypericRabbitAdmin] destroy()");
    }

    public List<RabbitVirtualHost> getVirtualHosts() throws PluginException {
        return Arrays.asList(get("/api/vhosts", RabbitVirtualHost[].class));
    }

    public List<RabbitQueue> getQueues(RabbitVirtualHost vh) throws PluginException {
        try {
            return Arrays.asList(get("/api/queues/" + URLEncoder.encode(vh.getName(), "UTF-8"), RabbitQueue[].class));
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<RabbitExchange> getExchanges(RabbitVirtualHost vh) throws PluginException {
        try {
            return Arrays.asList(get("/api/exchanges/" + URLEncoder.encode(vh.getName(), "UTF-8"), RabbitExchange[].class));
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<RabbitConnection> getConnections() throws PluginException {
        return Arrays.asList(get("/api/connections", RabbitConnection[].class));
    }

    public List<RabbitChannel> getChannels() throws PluginException {
        return Arrays.asList(get("/api/channels", RabbitChannel[].class));
    }

    public boolean getStatus() throws PluginException {
        RabbitNode n = get("/api/nodes/" + node, RabbitNode.class);
        return (n != null) && n.isRunning();
    }

    public String getPeerNodeName() {
        return node;
    }

    public RabbitVirtualHost getVirtualHost(String vhName) throws PluginException {
        try {
            vhName = URLEncoder.encode(vhName, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }
        return get("/api/vhosts/" + vhName, RabbitVirtualHost.class);
    }

    public RabbitQueue getVirtualQueue(String vhName, String qName) throws PluginException {
        try {
            vhName = URLEncoder.encode(vhName, "UTF-8");
            qName = URLEncoder.encode(qName, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }

        return get("/api/queues/" + vhName + "/" + qName, RabbitQueue.class);
    }

    public RabbitExchange getExchange(String vhost, String exch) throws PluginException {
        try {
            vhost = URLEncoder.encode(vhost, "UTF-8");
            exch = URLEncoder.encode(exch, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
        }

        return get("/api/exchanges/" + vhost + "/" + exch, RabbitExchange.class);
    }

    private <T extends Object> T get(String api, Class<T> classOfT) throws PluginException {
        T res = null;
        try {
            GetMethod get = new GetMethod("http://192.168.183.140:55672" + api);
            get.setDoAuthentication(true);
            int r = client.executeMethod(get);
            if (r != 200) {
                throw new PluginException("[" + api + "] http error code: '" + r + "'");
            }
            String responseBody = get.getResponseBodyAsString();
            if (logger.isDebugEnabled()) {
                logger.debug("[" + api + "] -(" + r + ")-> " + responseBody);
            }
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            res = gson.fromJson(responseBody, classOfT);
            if (logger.isDebugEnabled()) {
                logger.debug("[" + api + "] -(" + r + ")-> " + res);
            }
        } catch (IOException ex) {
            logger.error(ex);
        }
        return res;
    }
}
