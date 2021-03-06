package org.hyperic.hq.plugin.exchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.plugin.mssql.PDH;
import org.hyperic.hq.product.Collector;
import org.hyperic.hq.product.CollectorResult;
import org.hyperic.hq.product.Metric;
import org.hyperic.hq.product.MetricValue;

public class ExchangeCollector extends Collector {

    private static Log log = LogFactory.getLog(ExchangeCollector.class);
    private List<String> counters = new ArrayList<String>();

    @Override
    public void collect() {
        log.debug("[collect] [" + getProperties() + "] counters.size() = " + counters.size());
        if (counters.size() > 0) {
            try {
                Map<String, Double> res = PDH.getFormattedValues(counters);
                for (Map.Entry<String, Double> entry : res.entrySet()) {
                    String obj = entry.getKey();
                    Double val = entry.getValue();
                    log.debug("[collect] " + obj + " = " + val);
                    setValue(obj, val);
                }
            } catch (Exception ex) {
                log.debug("[collect] " + ex, ex);
            }
        }
    }

    @Override
    public MetricValue getValue(Metric metric, CollectorResult result) {
        log.debug("[getValue] metirc = " + metric);
        String g = metric.getObjectProperty("g");

        String obj = "\\" + g + "\\" + metric.getAttributeName();

        MetricValue res = MetricValue.NONE;
        if (obj != null) {
            if (counters.contains(obj)) {
                res = result.getMetricValue(obj);
            } else {
                counters.add(obj);
            }
        }

        log.debug("[getValue] obj:'" + obj + "' res:'" + res.getValue() + "'");
        return res;
    }
}
