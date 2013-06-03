package ch.ethz.inf.systems.ptremp.healthbank.db;

import java.net.UnknownHostException;
import java.util.HashMap;

import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;

public interface DBConnector {
	
	public abstract void connect() throws UnknownHostException;
	public abstract void disconnect();
	
	public abstract boolean createDB(String name) throws NotConnectedException;
	public abstract boolean deleteDB(String name) throws NotConnectedException;
	
	public abstract boolean createTable(String table) throws NotConnectedException;
	public abstract boolean deleteTable(String table) throws NotConnectedException, IllegalQueryException;
	
	public abstract Object insert(String table, HashMap<Object, Object> data) throws NotConnectedException;
	public abstract Object query(String table, Object query) throws IllegalQueryException, NotConnectedException;
	public abstract void update(String table, Object query, Object update) throws IllegalQueryException, IllegalArgumentException, NotConnectedException;
	public abstract boolean delete(String table, Object query) throws IllegalQueryException, NotConnectedException;

}
