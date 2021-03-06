package com.frs.alto.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.frs.alto.core.DatabaseConnectionMetaData;
import com.frs.alto.core.TenantMetaData;
import com.frs.alto.util.TenantUtils;

public class MultiTenantDataSource implements DataSource {

    private int maxActive = 5;
    private int maxIdle = 1;
    private long maxWait = 10000;

    private String datasourceId = "default";
    private PrintWriter logWriter = new PrintWriter(System.out);
    private List<DataSourceInfo> dataSources = new ArrayList<DataSourceInfo>();
    private HashMap<String, DataSourceInfo> tenantMapping = new HashMap<String, DataSourceInfo>();

    /**
     * Constructor giving an identifying name to this DataSource.
     *
     * @param datasourceId This DataSource's ID
     */
    public MultiTenantDataSource(String datasourceId) {

        this.datasourceId = datasourceId;
    }

    /**
     * Gets the ID of this datasource.
     *
     * @return  The datasource ID.
     */
    public String getDataSourceId() {
        return ( datasourceId );
    }

    /**
     * Sets the ID of this datasource.
     *
     * @param datasourceId The value for this DataSource's ID.
     */
    public void setDataSourceId(String datasourceId) {
        this.datasourceId = datasourceId;
    }

    /**
     * This method gets the maximum number of active pooled Connections to be
     * in allowed.
     *
     * @return  The maximum active connections in the pool.
     */
    public int getMaxActive() {

        return maxActive;
    }

    /**
     * This method sets the maximum number of active pooled Connections to be
     * in allowed.
     *
     */
    public void setMaxActive(int maxActive) {

        this.maxActive = maxActive;
    }

    /**
     * This method returns the maximum number of idle Connections that will
     * be allowed in the pool.
     *
     * @return  The maximum number of idle connections.
     */
    public int getMaxIdle() {

        return maxIdle;
    }

    /**
     * This method sets a limit on the maximum number of idle Connections
     * allowed to remain in the pool.
     *
     * @param maxIdle   The maximum idle connections allowed.
     */
    public void setMaxIdle(int maxIdle) {

        this.maxIdle = maxIdle;
    }

    /**
     * This method gets the maximum time the Connection Factory will wait for
     * a login to complete.
     *
     * @return  The time, in milliseconds, the Connection Factory will wait.
     */
    public long getMaxWait() {

        return maxWait;
    }

    /**
     * This method sets the maximum time the Connection Factory will wait for
     * a login to complete.
     *
     * @param maxWait  The time, in milliseconds, the Connection Factory will wait.
     */
    public void setMaxWait(long maxWait) {

        this.maxWait = maxWait;
    }

    /**
     * This method adds a DataSourceInfo to the set of DataSources managed.
     *
     * @param hostname The host name of the database server
     * @param username A user with privileges to read and modify tenant schemas
     * @param password The user's password
     * @return
     * @throws SQLException
     */
    private DataSourceInfo addDataSource(Databases dbProfile, String hostname, String username, String password) throws SQLException {

        DataSourceInfo cnxInfo = new DataSourceInfo(dbProfile, hostname, username, password,
                                                    maxActive, maxIdle, maxWait);
        getCatalogs(cnxInfo);
        dataSources.add(cnxInfo);
        return (cnxInfo);
    }

    /**
     * This private method examines a DataSource for tenant schemas. They are then
     * mapped such that the correct DataSource can be found for a given tenant ID.
     *
     * @param dataSource The DataSourceInfo containing tenant schemas.
     * @throws SQLException
     */
    private void getCatalogs(DataSourceInfo dataSource) throws SQLException {

        Connection cnx = null;
        ResultSet catalogRS = null;

        try {

            cnx = dataSource.getConnection();
            if (cnx == null) {
                throw new SQLException("Could not obtain a connection from DataSource.");
            }
            catalogRS = cnx.getMetaData().getCatalogs();
            while (catalogRS != null && catalogRS.next()) {
                String tenantID = catalogRS.getString(1);
                tenantMapping.put(tenantID, dataSource);

            }
        } catch (Exception sqlErr) {

            throw new SQLException("Error fetching catalogs.", sqlErr);
        } finally {

            try {
                catalogRS.close();
            } catch (Exception rsErr) {
            }
            try {
                cnx.close();
            } catch (Exception cnxErr) {
            }
        }
    }

    /**
     * This method returns a Connection from a DataSource associated with
     * the current Tenant. The tenant having been previously set via
     * <code>TenantUtils.setThreadTenant()</code>.
     *
     * @return A JDBC Connection object.
     * @throws SQLException If no tenant was set, no mapped DataSource was found
     *                      or no DataSource could be created from the tenant's DB
     *                      metadata.
     */
    @Override
    public Connection getConnection() throws SQLException {

        TenantMetaData tenantMeta = TenantUtils.getThreadTenant();
        if (tenantMeta == null) {
            throw new SQLException("No TenantMetaData found for thread.");
        }

        String tenantID = tenantMeta.getTenantIdentifier();

        if (tenantMapping.containsKey(tenantID)) {
            DataSourceInfo dataSource = tenantMapping.get(tenantID);
            Connection cnx = dataSource.getConnection();
            cnx.setCatalog(tenantID);
            return (cnx);
        }

        Map<String, DatabaseConnectionMetaData> dbMeta = tenantMeta.getDatabaseConnectionMetaData();
        if (dbMeta.containsKey(datasourceId)) {
            DatabaseConnectionMetaData meta = dbMeta.get(datasourceId);
            String server = meta.getServerName();
            String username = meta.getUserName();
            String password = meta.getPassword();
            Databases dbProfile = meta.getServerType();
            DataSourceInfo schemaInfo = addDataSource(dbProfile, server, username, password);
            Connection cnx = schemaInfo.getConnection();
            cnx.setCatalog(tenantID);
            return (cnx);
        }

        throw new SQLException("Could not find or create a suitable DataSource.");

    }

    /**
     * This method is unsupported. Use <code>addDataSource()</code> and <code>getConnection()</code>
     * instead.
     *
     * @param username
     * @param password
     * @return
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {

        throw new UnsupportedOperationException("Unsupported method. Use addDataSource() + getConnection() instead.");
    }

    /**
     * This method gets the LogWriter (a PrintWriter) for the DataSource.
     *
     * @return The PrintWriter
     * @throws SQLException
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {

        return logWriter;
    }

    /**
     * This method sets the LogWriter (a PrinterWriter) for the DataSource.
     * All managed DataSources will use the same PrintWriter.
     *
     * @param out A PrintWriter
     * @throws SQLException
     */
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

        logWriter = out;
    }

    /**
     * This method sets the login timeout for all managed DataSources.
     *
     * @param seconds The number of seconds to wait for a login.
     * @throws SQLException
     */
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    /**
     * Returns the login timeout of the managed DataSources.
     *
     * @return The login timout, in seconds, of the managed DataSources.
     * @throws SQLException
     */
    @Override
    public int getLoginTimeout() throws SQLException {

        return (0);
    }

    /**
     * Whatwhat?
     *
     * @param iface
     * @return
     * @throws SQLException
     */
    @Override
    public Object unwrap(Class iface) throws SQLException {

        if (iface == MultiTenantDataSource.class) {
            return (this);
        }

        throw new SQLException("Cannot unwrap this to an " + iface);
    }

    /**
     * This class is not a wrapper for a single DataSource, so this
     * will always return false.
     *
     * @param iface The interface.
     * @return false.
     * @throws SQLException
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {

        return (false);
    }
    
    
    
    

	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		// TODO Auto-generated method stub
		return null;
	}





	/**
     * This private class is used to store JDBC connection information and
     * return a JDBC connection when needed.
     */
    private class DataSourceInfo {

        private ConnectionPool pool;

        /**
         * Constructor.
         *
         * @param dbProfile     The Database Enum describing this database type.
         * @param hostname      Database host name.
         * @param username      A valid user with RW-CAD privileges
         * @param password      The user's password
         */
        public DataSourceInfo(Databases dbProfile, String hostname, String username, String password,
                              int maxActive, int maxIdle, long maxWait) {

            this.pool = new ConnectionPool(dbProfile, hostname, username, password, maxActive, maxIdle, maxWait);
        }

        /**
         * Returns a JDBC connection for the configured database.
         *
         * @return JDBC Connection.
         * @throws SQLException
         */
        public Connection getConnection() throws SQLException {

            try {
                return ( pool.checkOut() );
            } catch (Exception coErr) {
                throw new SQLException("Failure checking out connection.", coErr);
            }
        }

        /**
         * Returns the connection pool associated to this data source.
         *
         * @return  The connection pool.
         */
        public ConnectionPool getPool() {

            return ( pool );
        }

    }
}
