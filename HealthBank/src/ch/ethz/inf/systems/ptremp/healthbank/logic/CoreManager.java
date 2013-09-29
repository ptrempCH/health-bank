package ch.ethz.inf.systems.ptremp.healthbank.logic;

import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import org.bson.types.ObjectId;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

/**
 * This class provides some core functions used by multiple servlet's to reduce redundancy and the
 * excessive use of the copy and paste pattern.
 * 
 * @author Patrick Tremp
 */
public class CoreManager {

	private MongoDBConnector connector; 
	private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static Random rnd = new Random();
	
	/**
	 * Standard Constructor
	 */
	public CoreManager() {
		try {
	        connector = MongoDBConnector.getInstance();
			if(!connector.isConnected()){
				if(MongoDBConnector.DEBUG){System.out.println("CoreManager is calling connect in the constructor!");}
				connector.connect();
			}
        } catch (UnknownHostException e){
        	connector = null;
        }
	}
	
	/**
	 * Use this method to reconnect to the database, if we lost connection.
	 * @return True if the connection was successful, false otherwise.
	 */
	public boolean reconnect(){
		try {
	        if (connector == null ) {
	        	connector = MongoDBConnector.getInstance();
	        }
			if(!connector.isConnected()){
				if(MongoDBConnector.DEBUG){System.out.println("CoreManager is calling connect in the reconnect!");}
				connector.connect();
			}
			return true;
        } catch (UnknownHostException e){
        	connector = null;
        	return false;
        } catch (MongoException e){
        	connector = null;
        	return false;
        }
	}
	
	/**
	 * Check if the given user is logged in to the system.
	 * A user is logged in, when he/she has a valid session key, the provided password is correct and the session_expires attribute is still valid.
	 * @param sessionKey The session key of the user to check
	 * @param credentials The credentials of the user to check. Make sure that this is plain text and no longer hashed
	 * @return True if the user is still logged in and everything is valid. False otherwise
	 * @throws IllegalQueryException Thrown when there was a DB error or something else did not work out as it is supposed to
	 * @throws NotConnectedException Thrown when there is no connection to the DB
	 */
	public boolean isUserLoggedIn(String sessionKey, String credentials) throws IllegalQueryException, NotConnectedException{
		if(connector==null || !connector.isConnected()){ 
			if(!reconnect()) {
				throw new NotConnectedException("There was a problem connecting to the database. Make sure you have a valid internet connection and all servers are running."); }
			}
		
		// First search the user with given user name
		BasicDBList list = new BasicDBList();
		list.add(credentials.substring(0, credentials.lastIndexOf(':')));
		DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
		if(res==null || res.size()==0){
			if(MongoDBConnector.DEBUG){System.out.println("CoreManager isUserLoggedIn, res is null or size() is zero!");}
			return false; // if we did not find such a user, return false.
		}
		
		// Now get the first user and check if it has defined the session_expires attribute (User names should be unique )
		DBObject user = res.next();
		if(user.get("session_expires")==null || user.get("session_expires").equals("")){ 
			if(MongoDBConnector.DEBUG){System.out.println("CoreManager isUserLoggedIn, Session expires is null or empty!");}
			return false; 
		}
		
		// check if the password and session key match
		String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(credentials.substring(credentials.lastIndexOf(':')+1)); // password is saved encrypted. Hence we need to encrypt it here too
		if(user.get("password").equals(md5)){
			if(user.get("session_key")!=null && user.get("session_key").equals(sessionKey)){
				// finally check if the session is expired or not
				DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
				try {
					Date d = dateFormat.parse((String) user.get("session_expires"));
					if(d.before(new Date())){
						if(MongoDBConnector.DEBUG){System.out.println("CoreManager isUserLoggedIn, Session is expired!");}
						return false;
					} else {
						return true;
					}
				} catch (ParseException e) {
					throw new IllegalQueryException("Could not parse the session_expires field. Please contact an administrator.");
				}
			}else {
				if(MongoDBConnector.DEBUG){System.out.println("CoreManager isUserLoggedIn, Received session key is not equal to stored one!");}
			}
		} else {
			if(MongoDBConnector.DEBUG){System.out.println("CoreManager isUserLoggedIn, Received password is not equal to stored one!");}
		}
		
		return false;
	}
	
	/**
	 * Updates the session_expires field of the given user. 
	 * Before calling this, make sure that the user is logged in by calling {@link #isUserLoggedIn(String, String) isUserLoggedIn()}
	 * @param credentials The credentials of the user to check. Make sure that this is plain text and no longer hashed
	 * @throws IllegalQueryException
	 * @throws NotConnectedException
	 * @throws IllegalArgumentException
	 */
	public void updateUserSessionExpires(String credentials) throws IllegalQueryException, NotConnectedException, IllegalArgumentException{
		if(connector==null || !connector.isConnected()){ throw new NotConnectedException("We are not connected to a database, try to connect first."); }
				
		BasicDBList list = new BasicDBList();
		list.add(credentials.substring(0, credentials.lastIndexOf(':')));
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		Date date = new Date((new Date()).getTime() + (60L*60L*1000L));  // get time plus an hour
		connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)), 
				new BasicDBObject("$set", new BasicDBObject("session_expires", dateFormat.format(date))));
	}
	
	/**
	 * Get the id of the user by providing the credentials
	 * @param credentials The credentials of the user to check. Make sure that this is plain text and no longer hashed
	 * @return The "_id" field of the user. Null in case of an error, or if there is no such user
	 * @throws NotConnectedException
	 * @throws IllegalQueryException
	 */
	public String getUserID(String credentials) throws NotConnectedException, IllegalQueryException{
		if(connector==null || !connector.isConnected()){ throw new NotConnectedException("We are not connected to a database, try to connect first."); }
		// First search the user with given user name
		BasicDBList list = new BasicDBList();
		list.add(credentials.substring(0, credentials.lastIndexOf(':')));
		DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
		if(res==null || res.size()==0){
			return null; // if we did not found such a user, return false.
		}
		
		// Now get the first user and check if it has defined the session_expires attribute (User names should be unique )
		DBObject user = res.next();
		if(user.get("session_expires")==null || user.get("session_expires").equals("")){ 
			return null; 
		}
		
		return user.get("_id").toString();
	}
	
	/**
	 * Get all the circles of another user which the other user assigned the current user to. 
	 * @param myUserId The user id of the current logged in user
	 * @param otherUserId The user id of the other user, we are searching the circles 
	 * @return An {@link ArrayList} containing the ids of the circles of the other user, the current user is assigned to 
	 * @throws IllegalQueryException
	 * @throws NotConnectedException
	 */
	public ArrayList<String> getCirclesIAmInOfAUser(String myUserId, String otherUserId) throws IllegalQueryException, NotConnectedException, IllegalArgumentException
	{
		try {
			ArrayList<String> result = new ArrayList<>();
			BasicDBList list = new BasicDBList();
			DBCursor res;
			list.add(new ObjectId(otherUserId));
			res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
			if(res!=null && res.hasNext()){
				DBObject obj = res.next();
				boolean found = false;
				BasicDBList usersWhoHaveHim = (BasicDBList) obj.get("haveMeInCircle");
				if(usersWhoHaveHim!=null && !usersWhoHaveHim.isEmpty()){
					for (int i=0;i<usersWhoHaveHim.size();i++) { 
						if(usersWhoHaveHim.get(i).toString().contains(myUserId)){ 
							found = true; 
							break;
						}
					} 
				} 
				if(found){ // so far we know that the provided user has us in a circle
					BasicDBList cList = new BasicDBList();
					cList.add(otherUserId);
					res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", cList)));
					if(res!=null && res.hasNext()){
						while (res.hasNext()) {
							DBObject aCircle = res.next();
							BasicDBList aCirclesUsers = (BasicDBList) aCircle.get("users");
							if(aCirclesUsers!=null && !aCirclesUsers.isEmpty()){
								for (int i=0;i<aCirclesUsers.size();i++) { 
									if(aCirclesUsers.get(i).toString().contains(myUserId)){ 
										result.add(((ObjectId) aCircle.get("_id")).toString());
										break;
									}
								} 
							} 
						}
					}
				}
			}
			return result;
		} catch (IllegalQueryException | NotConnectedException e) {
			throw e;
		}
	}
	/**
	 * Create the four initial circles for a newly added user. 
	 * These four circles are: 'Family', 'Friends', 'Medical Professionals' and 'Wellness Professionals'.
	 * @param userid The user id of the user to set the circles to as {@link String}. 
	 * @return boolean True if all went well, false when no circles were created due to a problem
	 * @throws NotConnectedException 
	 * @throws IllegalQueryException 
	 */
	public boolean createCoreCircles(String userid) throws NotConnectedException, IllegalArgumentException {
		if(connector==null || !connector.isConnected()){ throw new NotConnectedException("We are not connected to a database, try to connect first."); }
		if(userid==null){ return false;}
		
		HashMap<Object, Object> data = new HashMap<Object, Object>();
		// Family
		data.put("name", "Family");
		data.put("descr", "A place to put all your family members such as wife, kids, parants and other relatives");
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		data.put("timedate", dateFormat.format(new Date()));
		data.put("userID", userid);
		data.put("color", "#FF6600");
		if(connector.insert(MongoDBConnector.CIRCLES_COLLECTION_NAME, data)==null){
			return false;
		}
		// Friends
		data = new HashMap<Object, Object>();
		data.put("name", "Friends");
		data.put("descr", "A place to put all your closest friends.");
		dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		data.put("timedate", dateFormat.format(new Date()));
		data.put("userID", userid);
		data.put("color", "#B13E0F");
		if(connector.insert(MongoDBConnector.CIRCLES_COLLECTION_NAME, data)==null){
			return false;
		}
		// Medical Professionals
		data = new HashMap<Object, Object>();
		data.put("name", "Medical Professionals");
		data.put("descr", "A list of all the medical professionals you trust.");
		dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		data.put("timedate", dateFormat.format(new Date()));
		data.put("userID", userid);
		data.put("color", "#E47833");
		if(connector.insert(MongoDBConnector.CIRCLES_COLLECTION_NAME, data)==null){
			return false;
		}
		// Wellness Professionals
		data = new HashMap<Object, Object>();
		data.put("name", "Wellness Professionals");
		data.put("descr", "A list of all the wellness professionals you trust.");
		dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		data.put("timedate", dateFormat.format(new Date()));
		data.put("userID", userid);
		data.put("color", "#C76114");
		if(connector.insert(MongoDBConnector.CIRCLES_COLLECTION_NAME, data)==null){
			return false;
		}

		return true;
	}

	/**
	 * Create the two initial spaces for a newly added user. The "All Entries" space is always visible and needs no entry in the DB
	 * These two spaces are: 'Medical' and 'Wellness'.
	 * @param userid The user id of the user to set the spaces to as {@link String}. 
	 * @return boolean True if all went well, false when no spaces were created due to a problem
	 * @throws NotConnectedException 
	 * @throws IllegalQueryException 
	 */
	public boolean createCoreSpaces(String userid) throws NotConnectedException, IllegalArgumentException {
		if(connector==null || !connector.isConnected()){ throw new NotConnectedException("We are not connected to a database, try to connect first."); }
		if(userid==null){ return false;}
		
		HashMap<Object, Object> data = new HashMap<Object, Object>();
		// Medical
		data.put("name", "Medical");
		data.put("descr", "All your medical data");
		data.put("hidden", "false");
		data.put("visualization", "");
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		data.put("timedate", dateFormat.format(new Date()));
		data.put("userID", userid);
		if(connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data)==null){
			return false;
		}
		// Wellness
		data = new HashMap<Object, Object>();
		data.put("name", "Wellness");
		data.put("descr", "All your wellness data");
		data.put("hidden", "false");
		data.put("visualization", "");
		dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		data.put("timedate", dateFormat.format(new Date()));
		data.put("userID", userid);
		if(connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data)==null){
			return false;
		}

		return true;
	}

	/**
	 * Creates a random string of given length using alphanumeric values
	 * @param len The length of the string to generate
	 * @return A random string of length len
	 */
	public String randomString( int len ) 
	{
	   StringBuilder sb = new StringBuilder( len );
	   for( int i = 0; i < len; i++ ) 
	      sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
	   return sb.toString();
	}

}
