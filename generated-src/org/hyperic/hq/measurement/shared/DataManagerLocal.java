/*
 * Generated by XDoclet - Do not edit!
 */
package org.hyperic.hq.measurement.shared;

/**
 * Local interface for DataManager.
 */
public interface DataManagerLocal
   extends javax.ejb.EJBLocalObject
{
   /**
    * Save the new MetricValue to the database
    * @param dp the new MetricValue
    * @throws NumberFormatException if the value from the DataPoint.getMetricValue() cannot instantiate a BigDecimal
    */
   public void addData( java.lang.Integer mid,org.hyperic.hq.product.MetricValue mv,boolean overwrite ) ;

   /**
    * Write metric data points to the DB with transaction
    * @param data a list of {@link DataPoint}s
    * @throws NumberFormatException if the value from the DataPoint.getMetricValue() cannot instantiate a BigDecimal
    */
   public boolean addData( java.util.List data ) ;

   /**
    * Write metric datapoints to the DB without transaction
    * @param data a list of {@link DataPoint}s
    * @param overwrite If true, attempt to over-write values when an insert of the data fails (i.e. it already exists). You may not want to over-write values when, for instance, the back filler is inserting data.
    * @throws NumberFormatException if the value from the DataPoint.getMetricValue() cannot instantiate a BigDecimal
    */
   public void addData( java.util.List data,boolean overwrite ) ;

   /**
    * Fetch the list of historical data points given a begin and end time range. Returns a PageList of DataPoints without begin rolled into time windows.
    * @param m The Measurement
    * @param begin the start of the time range
    * @param end the end of the time range
    * @param prependUnknowns determines whether to prepend AVAIL_UNKNOWN if the corresponding time window is not accounted for in the database. Since availability is contiguous this will not occur unless the time range precedes the first availability point.
    * @return the list of data points
    */
   public org.hyperic.util.pager.PageList getHistoricalData( org.hyperic.hq.measurement.server.session.Measurement m,long begin,long end,org.hyperic.util.pager.PageControl pc,boolean prependAvailUnknowns ) ;

   /**
    * Fetch the list of historical data points given a begin and end time range. Returns a PageList of DataPoints without begin rolled into time windows.
    * @param m The Measurement
    * @param begin the start of the time range
    * @param end the end of the time range
    * @return the list of data points
    */
   public org.hyperic.util.pager.PageList getHistoricalData( org.hyperic.hq.measurement.server.session.Measurement m,long begin,long end,org.hyperic.util.pager.PageControl pc ) ;

   /**
    * Aggregate data across the given metric IDs, returning max, min, avg, and count of number of unique metric IDs
    * @param measurements {@link List} of {@link Measurement}s
    * @param begin The start of the time range
    * @param end The end of the time range
    * @return the An array of aggregate values
    */
   public double[] getAggregateData( java.util.List measurements,long begin,long end ) ;

   /**
    * Fetch the list of historical data points, grouped by template, given a begin and end time range. Does not return an entry for templates with no associated data. PLEASE NOTE: The {@link MeasurementConstants.IND_LAST_TIME} index in the {@link double[]} part of the returned map does not contain the real last value. Instead it is an averaged value calculated from the last 1/60 of the specified time range. If this becomes an issue the best way I can think of to solve is to pass in a boolean "getRealLastTime" and issue another query to get the last value if this is set. It is much better than the alternative of always querying the last metric time because not all pages require this value.
    * @param measurements The List of {@link Measurement}s to query
    * @param begin The start of the time range
    * @param end The end of the time range
    * @see org.hyperic.hq.measurement.server.session.AvailabilityManagerImpl#getHistoricalData()
    * @return the {@link Map} of {@link Integer} to {@link double[]} which represents templateId to data points
    */
   public java.util.Map getAggregateDataByTemplate( java.util.List measurements,long begin,long end ) ;

   /**
    * Fetch the list of historical data points given a start and stop time range and interval
    * @param measurements The List of Measurements to query
    * @param begin The start of the time range
    * @param end The end of the time range
    * @param interval Interval for the time range
    * @param type Collection type for the metric
    * @param returnMetricNulls Specifies whether intervals with no data should be return as nulls
    * @see org.hyperic.hq.measurement.server.session.AvailabilityManagerImpl#getHistoricalData()
    * @return the list of data points
    */
   public org.hyperic.util.pager.PageList<HighLowMetricValue> getHistoricalData( java.util.List measurements,long begin,long end,long interval,int type,boolean returnMetricNulls,org.hyperic.util.pager.PageControl pc ) ;

   /**
    * Get the last MetricValue for the given Measurement.
    * @param m The Measurement
    * @return The MetricValue or null if one does not exist.
    */
   public org.hyperic.hq.product.MetricValue getLastHistoricalData( org.hyperic.hq.measurement.server.session.Measurement m ) ;

   /**
    * Fetch the most recent data point for particular Measurements.
    * @param measurements The List of Measurements to query. In the list of Measurements null values are allowed as placeholders.
    * @param timestamp Only use data points with collection times greater than the given timestamp.
    * @return A Map of measurement ids to MetricValues. TODO: We should change this method to now allow NULL values. This is legacy and only used by the Metric viewer and Availabilty Summary portlets.
    */
   public java.util.Map getLastDataPoints( java.util.List measurements,long timestamp ) ;

   /**
    * Get data points from cache only
    */
   public java.util.ArrayList getCachedDataPoints( java.lang.Integer[] ids,java.util.Map data,long timestamp ) ;

   /**
    * Get a Baseline data.
    */
   public double[] getBaselineData( org.hyperic.hq.measurement.server.session.Measurement meas,long begin,long end ) ;

   /**
    * Fetch a map of aggregate data values keyed by metrics given a start and stop time range
    * @param tids The template id's of the Measurement
    * @param iids The instance id's of the Measurement
    * @param begin The start of the time range
    * @param end The end of the time range
    * @param useAggressiveRollup uses a measurement rollup table to fetch the data if the time range spans more than one data table's max timerange
    * @return the Map of data points
    */
   public java.util.Map getAggregateDataByMetric( java.lang.Integer[] tids,java.lang.Integer[] iids,long begin,long end,boolean useAggressiveRollup ) ;

   /**
    * Fetch a map of aggregate data values keyed by metrics given a start and stop time range
    * @param measurements The id's of the Measurement
    * @param begin The start of the time range
    * @param end The end of the time range
    * @param useAggressiveRollup uses a measurement rollup table to fetch the data if the time range spans more than one data table's max timerange
    * @return the map of data points
    */
   public java.util.Map getAggregateDataByMetric( java.util.List measurements,long begin,long end,boolean useAggressiveRollup ) ;

}
