/* **********************************************************************
/*
 * NOTE: This copyright does *not* cover user programs that use Hyperic
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 *
 * Copyright (C) [2004-2012], VMware, Inc.
 * This file is part of Hyperic.
 *
 * Hyperic is free software; you can redistribute it and/or modify
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
package org.hyperic.tools.dbmigrate;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.DataType;
import org.hyperic.tools.ant.utils.DatabaseType;
import org.hyperic.tools.dbmigrate.Forker.ForkContext;
import org.hyperic.tools.dbmigrate.Forker.ForkWorker;
import org.hyperic.tools.dbmigrate.Forker.WorkerFactory;
import org.hyperic.util.StringUtil;
import org.springframework.scheduling.annotation.AsyncResult;

/**
 * Base class for table processing entities. 
 * Factorizes common logic to fork and join 
 * @param <T> The unit of work parser 
 */
@SuppressWarnings("rawtypes")
public abstract class TableProcessor<T extends Callable<TableProcessor.Table[]>> extends Task implements Forker.WorkerFactory<TableProcessor.Table,T> {
  
  private static final String RECS_PER_TABLE_STATS_ENV_VAR_SUFFIX = ".table.stats" ;
  private static final String DEFAULT_OUTPUT_DIR = "./hq-migrate/export-data";
  protected static final String DATA_RELATIVE_DIR = "/data";
  protected static final int DEFAULT_QUERY_TIMEOUT_SECS = 2000 ; //seconds 
  protected static final int PROTECTIVE_BATCH_SIZE = 1000;
  private static final int MAX_WORKERS = 5;
  
  protected int maxRecordsPerTable = -1; //defaults to -1 (all) 
  protected boolean isDisabled; // defaults to false 
  protected int batchSize = PROTECTIVE_BATCH_SIZE; //defaults to 1000 
  protected int queryTimeoutSecs = DEFAULT_QUERY_TIMEOUT_SECS ;  //defults to 2000 seconds 
  private int noOfWorkers = MAX_WORKERS ; //defaults to 5 
  protected TablesContainer tablesContainer ; //tables metadata definitions container 
  private String tableContainerRefs  ;  
  protected DatabaseType enumDatabaseType ; //build database product strategy  
  
  /**
   * @param tableContainerRefs a comma delimited list of the names of the 'tables' stand alone elements to look up 
   */
  public final void setTablesRefs(final String tableContainerRefs) { 
      this.tableContainerRefs = tableContainerRefs ; 
  }//EOM 
  
  /**
   * Supports multiple 'tables' container elements 
   * @param tablesContainer either the actual element or a reference proxy (similar to simlink) through which to<br/> 
   * locate the actual element.
   */
  public final void addConfiguredTables(TablesContainer tablesContainer) { 
      if(tablesContainer.isReference()) {
          Project project = null ;
          String refId = null ; 
          
          do{ 
              project = tablesContainer.getProject() ; 
              if(project == null) project = this.getProject() ;
              
              refId = tablesContainer.getRefid().getRefId() ; 
              
              tablesContainer = (TablesContainer) project.getReference(refId)  ; 
              
              if(tablesContainer == null) {
                  this.log("Tables Container reference " + refId + " did not exist, skippping.", Project.MSG_VERBOSE) ;
                  return ; 
              }//EO if tablesContainer was null
          }while(tablesContainer.isReference()) ;
      }//EO if instance was in fact a reference 
      
      if(this.tablesContainer  == null) this.tablesContainer = tablesContainer ;
      else this.tablesContainer.tables.putAll(tablesContainer.tables) ; 
  }//EOM
  
  private final void initTableContainer() { 
      final Project project = this.getProject() ; 
      
      TablesContainer tempTablesContainer = null ; 
      for(String tableContainerRef : this.tableContainerRefs.split(",")) { 
          
          tempTablesContainer = (TablesContainer) project.getReference(tableContainerRef)  ;
          if(tempTablesContainer == null) {
              this.log("Tables Container reference " + tableContainerRef + " did not exist, skippping.", Project.MSG_VERBOSE) ;
              return ; 
          }//EO if tablesContainer was null
          else { 
              if(this.tablesContainer == null) this.tablesContainer = tempTablesContainer ; 
              else this.tablesContainer.tables.putAll(tempTablesContainer.tables) ;
          }//EO else if ref exists 
          
      }//EO while there are more references 
  }//EOM 

  /**
   * @param isDisabled id true the task is skipped (mainly for debugging purposes) 
   */
  public final void setDisabled(final boolean isDisabled) {
    this.isDisabled = isDisabled;
  }//EOM 
  
  /**
   * @param noOfWorkers number of threads used for parallel processing  
   */
  public final void setNoOfWorkers(final int noOfWorkers) { 
      this.noOfWorkers = noOfWorkers ; 
  }//EOM 

  /**
   * @param maxRecordsPerTable limits the number of records per table to process (can be used for evaluation or debugging purposes) 
   */
  public final void setMaxRecordsPerTable(final int maxRecordsPerTable) {
    this.maxRecordsPerTable = maxRecordsPerTable;
  }//EOM 

  /**
   * @param batchSize the number of records which constitute one cohesive unit. 
   */
  public final void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }//EOM 
  
  /**
   * @param iQueryTimeoutSecs timeout after which to fail to statement execution
   */
  public final void setQueryTimeoutSecs(final int iQueryTimeoutSecs) { 
      this.queryTimeoutSecs = iQueryTimeoutSecs ; 
  }//EOM 

  public void execute() throws BuildException  {
    if (this.isDisabled) return;
    
    try {
        final long before = System.currentTimeMillis() ; 
        
        this.initTableContainer() ;         
        if(this.tablesContainer == null || this.tablesContainer.tables.isEmpty()) throw new BuildException("tables attribute was not set or was empty, aborting") ; 
        
        final Hashtable env = getProject().getProperties();

        final String outputDir = Utils.getEnvProperty(Utils.STAGING_DIR, DEFAULT_OUTPUT_DIR, env) + DATA_RELATIVE_DIR;
        final File outputDirFile = new File(outputDir);

        //invoke the template method which might purge some directories and other resources 
        this.clearResources(outputDirFile.getParentFile());

        if(!outputDirFile.exists()) outputDirFile.mkdirs();

        int noOfTables = this.tablesContainer.tables.size() ; 

        final LinkedBlockingDeque<Table> sink = new LinkedBlockingDeque<Table>();
        
        //iterate over all tables and add to the blockingQueue sink via a template method 
        for(Table table: this.tablesContainer.tables.values()) { 
            this.addTableToSink(sink, table) ;
        }//EO while there are more tables to add
        
        noOfTables = sink.size() ; 

        //Note that the WorkerFactory is the 'this' instance
        final ForkContext<Table,T> context = new ForkContext<Table,T>(sink, this, env);
        context.put(Utils.STAGING_DIR, outputDirFile);

        //invoke the lifecycle method for pre processing logic (see TableImporter and TableExporter for examples) 
        this.beforeFork(context, sink);
        
        List<Future<Table[]>> workersResponses = null;
        T worker = null;

        //No need to fork if there is a single table to process 
        if (noOfTables == 1) {
            workersResponses = new ArrayList<Future<Table[]>>(1);
            worker = this.newWorker(context);
            final Table[] response = worker.call();
            workersResponses.add(new AsyncResult<Table[]>(response));
        } else {
            //fork and wait for all workers to finish
            workersResponses = Forker.fork(noOfTables, this.noOfWorkers, context);
        }//EO else if there were more than 1 table

        //perform validations and exception checking on the workerResponses 
        this.afterFork(context, workersResponses, sink);
        //create a processing summary report and store in an environment variable for future reference 
        this.generateSummaryReport() ; 
        
        this.log("Overall Processing took: " + StringUtil.formatDuration(System.currentTimeMillis()-before) ) ; 
    
    }catch (Throwable t) {
      throw new BuildException(t);
    }//EO catch block 
  }//EOM 
  
  /**
   * Template method which simply adds the table instance as the tail element.
   * @param sink into which to add the table 
   * @param table instance to add to the sink 
   */
  protected void addTableToSink(final LinkedBlockingDeque<Table> sink, final Table table) { 
      sink.add(table) ;
  }//EOM 

  /**
   * Template lifecycle method for pre processing logic (see {@link TableImporter} and {@link TableExporter} for examples)
   * @param context {@link ForkContext} instance used to pass customized parameters to the {@link WorkerFactory} for the 
   * {@link ForkWorker}'s creation
   * @param sink
   * @throws Throwable
   */
  protected void beforeFork(final ForkContext<Table, T> context, final LinkedBlockingDeque<Table> sink) throws Throwable { /*NOOP*/ }//EOM 
 
  /**
   * Ensures that no exception was returned from one of the workers and that there are no entities left in the processing sink
   * <b>Note</b>  the method is parameterized locally so as to allow invocation of workers processing entities different than {@link Table} 
   * types.  
   * @param context {@link ForkContext} instance which may contain customize parameters.<br/>
   * @param workersResponses 
   * @param sink
   * @throws Throwable
   */
  protected <Y ,Z extends Callable<Y[]>> void afterFork(final ForkContext<Y,Z> context, final List<Future<Y[]>> workersResponses, final LinkedBlockingDeque<Y> sink) throws Throwable {
    for (Future<Y[]> workerResponse : workersResponses) {
        try{
            workerResponse.get();
        } catch (Throwable t) {
            log(t, Project.MSG_ERR);
        }//EO catch block 
    }//EO while there are more responses 

    for(Y entity : sink) { 
      log(getClass().getName() + ": Some failure had occured as the following entity was not processed: " + entity, Project.MSG_ERR); 
    }//EO while there are more unprocessed entities 
  }//EOM 
  
  /**
   * Stores the table : no of processed records tuples as a string against the '<<taskName>>.table.stats' key for future reference
   */
  private final void generateSummaryReport() { 
    //store the records per table stats in an env variable using the following format <task name>_RECS_PER_TABLE_STATS_ENV_VAR_SUFFIX
      final StringBuilder summaryBuilder = new StringBuilder() ;
      
      int paddingThreshold = 32;
      
      for(Table table : this.tablesContainer.tables.values()) { 
          
          summaryBuilder.append(" - ").append(table.name).append(":") ; 
          for(int i=table.name.length(); i < paddingThreshold; i++) { 
              summaryBuilder.append(" ") ; 
          }//EO while there are more padding to add 
          
          summaryBuilder.append("\t").append(table.noOfProcessedRecords).append("\n") ; 
      }//EO while there are more tables                           
      
      this.getProject().setProperty(this.getTaskName() + RECS_PER_TABLE_STATS_ENV_VAR_SUFFIX, summaryBuilder.toString()) ;
  }//EOM 
  
  /**
   * Template method used for purging the staging directory prior to processing 
   * @param stagingDir
   */
  protected void clearResources(final File stagingDir) { /*NOOP*/ }//EOM 
  
  protected abstract Connection getConnection(Hashtable env) throws Throwable;

  protected Connection getConnection(final Hashtable env, final boolean autoCommit) throws Throwable {
    final Connection conn = this.getConnection(env);
    conn.setAutoCommit(autoCommit);
    return conn;
  }//EOM 

  /**
   * Implementation of the {@link WorkerFactory#newWorker(ForkContext)} delegaing the actual creation to subclasses 
   * whilst initalizing common resources such as the database type, connection and staging directory. 
   * @param context {@link ForkContext} instance which contains the staging directory, the sink and other customized parameter 
   * used to for the {@link ForkWorker} initialization
   */
  public T newWorker(Forker.ForkContext<Table, T> context) throws Throwable {
    final Connection conn = this.getConnection(context.getEnv());
    conn.setAutoCommit(false);
    
    if(this.enumDatabaseType == null) this.enumDatabaseType  = DatabaseType.valueOf(conn.getMetaData().getDatabaseProductName()) ;
    
    final File outputDir = (File)context.get(Utils.STAGING_DIR);
    return newWorkerInner(context, conn, outputDir);
  }//EOM 
 
  protected abstract T newWorkerInner(final Forker.ForkContext<Table,T> forkContext, final Connection conn, File parentDir);
  
  
  public static final class TablesContainer extends DataType { 
      protected final Map<String,Table> tables = new HashMap<String,Table>() ;
      
      public final void addConfiguredTable(final Table table) {
          this.tables.put(table.name, table) ; 
      }//EOM 
      
      public final void addConfiguredBigTable(final BigTable bigTable) {
          this.tables.put(bigTable.name, bigTable) ; 
      }//EOM
      
  }//EO inner class BigTablesContainer
  
  public static class Table{ 
      
      protected String name ; 
      protected AtomicInteger noOfProcessedRecords ; 
      protected boolean shouldTruncate ;
      
      public Table(){
          this.noOfProcessedRecords = new AtomicInteger()  ;
          this.shouldTruncate = true ; 
      }//EOM 

      Table(final String name) {
          this() ;
          this.name = name;  
      }//EOM 
      
      public final void setTruncate(final boolean shouldTruncate) { 
          this.shouldTruncate = shouldTruncate ; 
      }//EOM 
      
      public final void setName(final String tableName) { 
          this.name = tableName.toUpperCase().trim() ; 
      }//EOM

    @Override
    public String toString() {
        return "Table [name=" + name + ", noOfProcessedRecords=" + noOfProcessedRecords + "]";
    }//EOM 
      
  }//EO inner class Table
  
  public static final class BigTable extends Table{ 
      
      protected int noOfPartitions ; 
      protected String partitionColumn ; 
      protected int partitionNumber = -1 ;  
      
      public BigTable(){}//EOM 
      
      BigTable(final String name, final String partitionColumn, final int noOfPartitions, final int partitionNumber, final AtomicInteger noOfProcessedRecords) { 
          super(name) ; 
          this.noOfPartitions = noOfPartitions ; 
          this.partitionColumn = partitionColumn ; 
          this.partitionNumber = partitionNumber ; 
          this.noOfProcessedRecords = noOfProcessedRecords ;
      }//EOM 
      
      public final void setNoOfPartitions(final int noOfPartitions) { 
          this.noOfPartitions = noOfPartitions ; 
      }//EOM 
      
      public final void setPartitionColumn(final String partitionColumn) { 
          this.partitionColumn = partitionColumn ; 
      }//EOM 
      
      public final BigTable clone(final int partitionNumber) { 
          return new BigTable(this.name, this.partitionColumn, this.noOfPartitions, partitionNumber, this.noOfProcessedRecords) ; 
      }//EOM 

      @Override
      public String toString() {
          return new StringBuilder("BigTable [tableName=").append(name).append(", noOfProcessedRecords=").
                  append(this.noOfProcessedRecords).append(", noOfPartitions=").append(
                  noOfPartitions).append(", partitionColumn=").append(partitionColumn).append(", partitionNo=").
                  append(this.partitionNumber).append("]").toString() ; 
      }//EOM
      
  }//EO inner class BigTable
  
}//EOC