package ch.ethz.inf.systems.ptremp.healthbank.exceptions;

/**
 * Thrown when there is no valid connection to the database.
 * 
 * @author Patrick Tremp
 */
public class NotConnectedException extends Exception {

	private static final long serialVersionUID = -7705828440786487563L;
	
	public NotConnectedException() {
		super("We are not connected yet. Please call connect first on the DB Connector. And make sure your DB is up and running.");
	}

	public NotConnectedException(String message) {
		super(message);
	}
}
