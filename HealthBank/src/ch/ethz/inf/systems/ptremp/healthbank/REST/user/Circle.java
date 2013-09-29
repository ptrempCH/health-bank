package ch.ethz.inf.systems.ptremp.healthbank.REST.user;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
 * Servlet implementation class Circle
 * In this servlet we implement the functionality to create, edit, query and delete circles. 
 * Circles are a collection of different other user then the provided one. It provides the
 * functionality to group 'friends' and known people into categories of different interests.
 * 
 * @author Patrick Tremp
 */
@WebServlet(
		description = "Query info about circles of users, add new ones, edit entries and delete them again", 
		urlPatterns = { 
				"/Circle", 
				"/circle"
		})
public class Circle extends HttpServlet {

	private static final long serialVersionUID = -2862730299484987696L;

	/**
	 *  Instance of the {@link MongoDBConnector}, which is responsible for the connection to the DB
	 */
	private MongoDBConnector connector; 
	
	/**
	 * Instance of the {@link CoreManager}, which is responsible for some core functionalities used
	 * in several different servlet.
	 */
	private CoreManager manager;
    
	/**
     * Main constructor
     * @see HttpServlet#HttpServlet()
     */
    public Circle() {
        super();
        if(connector == null ) { 
        	connector = MongoDBConnector.getInstance();
        }
		if(manager == null) {
			manager = new CoreManager();
		}
    }

	/**
	 * This method will initialize the {@link MongoDBConnector} and {@link CoreManager} it they have not yet
	 * been initialized via constructor. 
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		if(connector == null ) { 
        	connector = MongoDBConnector.getInstance();
        }
		if(manager == null) {
			manager = new CoreManager();
		}
	}

	/**
	 * The GET request allows the caller to return one or more specific circle(s). There are three different possibilities to make this call.
	 * By providing the 'name' parameter we search for the circle with exact this name. By providing the 'id' attribute we do the same for
	 * circles with this id. And finally if the caller omits both of these parameters, we will return all the circles of the given user.
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * By name:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - name: The name of the circle to search for
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * By id:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the circle to search for
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Return all circles of the given user:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 * 
	 * All the returned circles are in the 'values' field of the returned JSON
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String values = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		String name = request.getParameter("name");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'session' and 'credentials' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in and get the records 
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Circle doGet: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					BasicDBList list = new BasicDBList();
					DBCursor res = null;
					if(name!=null && name.length()>0){
						list.add((manager.getUserID(credentials)));
						BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
						BasicDBObject q2 = new BasicDBObject("name", name);
						ArrayList<BasicDBObject> myList = new ArrayList<BasicDBObject>();
						myList.add(q1);
						myList.add(q2);
						res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("$and", myList));
					} else if(id!=null && id.length()>0){
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
					} else {
						list.add(manager.getUserID(credentials));
						res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
						if(res==null){ // maybe due to some issues, the user does not have the basic circles registered. So register them for him and try again.
							manager.createCoreCircles(manager.getUserID(credentials));
							res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
						}
					}
					if(res==null){
						errorMessage = "\"Circle doGet: There was an error, we did not find any circles. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						JSONParser parser = new JSONParser();
						JSONArray resArray = new JSONArray();
						while(res.hasNext()){
							DBObject obj = res.next();
							JSONObject resObj = (JSONObject) parser.parse(obj.toString());
							resArray.add(resObj);
						}
						JSONObject result = new JSONObject();
						result.put("circles", resArray);
						values = result.toJSONString();
					}
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Circle doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Circle doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Circle doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"Circle doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned circles for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned circles for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * The POST request allows the caller to create new circles, edit them, add and remove users to it and finally to 
	 * delete the circle again. There are multiple ways to call this request for the described operations. We will show
	 * them in detail in the following.
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Create a new circle:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - name: The name of the circle
	 * - color: The color for the circle
	 * - descr: The description for the circle
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Edit an existing circle:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The object id of the circle to edit
	 * - name: The name of the circle  (either name or descr or both of them have to be provided)
	 * - descr: The description for the circle (either name or descr or both of them have to be provided)
	 * - color: The color for the circle
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Delete an existing circle:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - del: This parameter needs to have the value set to 'true' if the caller wants to delete the circle
	 * - id: The id of the circle to delete
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Add a new user to the circle:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the circle to add to
	 * - userId: The id of the user the caller wants to add to the circle
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Remove an user from the circle:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the circle to remove the user from
	 * - userId: The id of the user the caller wants to remove from the circle
	 * - del: This parameter needs to have the value set to 'true' if the caller wants to remove the user
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String name = "", descr="", color ="", newUserId = "", id = "", del = "";
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Circle doPost: Please provide the parameters 'session' and 'credentials' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check record specific parameters
			name = request.getParameter("name");
			descr = request.getParameter("descr");
			newUserId = request.getParameter("userId");
			color = request.getParameter("color");
			id = request.getParameter("id");
			del = request.getParameter("del"); // used for deletions of circles and/or users in circles
			
			// check if user is logged in and save the new value
			try {
				HashMap<Object, Object> data = new HashMap<Object, Object>();
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Circle doPost: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {

					// check DB connection
					if(!connector.isConnected()){
						manager.reconnect();
					}

					name = (name!=null)?URLDecoder.decode(name, "UTF-8"):null;
					descr = (descr!=null)?URLDecoder.decode(descr, "UTF-8"):null;	
					newUserId = (newUserId!=null)?URLDecoder.decode(newUserId, "UTF-8"):null;	
					id = (id!=null)?URLDecoder.decode(id, "UTF-8"):null;
					color = (color!=null)?URLDecoder.decode(color, "UTF-8"):null;
					if(id==null){ // insert a new one
						if(name==null || name.length()<2 || descr==null || descr.length()<1){
							errorMessage = "\"Circle doPost: Please provide the parameters 'name' and 'descr' with the request to create a new circle.\"";
							wasError = true;
						}
						if(!wasError){														
							data = new HashMap<Object, Object>();
							data.put("name", name);
							data.put("descr", descr);
							data.put("color", color);
							DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
							data.put("timedate", dateFormat.format(new Date()));
							data.put("userID", manager.getUserID(credentials));
							
							connector.insert(MongoDBConnector.CIRCLES_COLLECTION_NAME, data);
						}
					} else { // update an existing one and/or add additional users		
		                BasicDBList list = new BasicDBList();
						list.add(new ObjectId(id));
						
						if(name!=null || descr!=null){ // update circle info
							if(name!=null){data.put("name", name);}
							if(descr!=null){data.put("descr", descr);}
							if(color!=null){data.put("color", color);}
							connector.update(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
						} else if(newUserId!=null){ // add or remove users from circle
							// first check if user is not already present, or if it is really present when you want to delete it
							BasicDBObject user = new BasicDBObject("userId", newUserId);
							BasicDBList testList = new BasicDBList();
							testList.add(new BasicDBObject("_id", new BasicDBObject("$in", list)));
							testList.add(new BasicDBObject("users", user));
							DBCursor res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("$and", testList));
							boolean userExists = false;
							if(res!=null && res.hasNext()) {
								userExists = true;
							}
							if(del!=null && del.equals("true")){ // remove user from circle
								if(userExists){
									connector.update(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$pull", new BasicDBObject( "users", user )));
									removeFromHaveInCircleList(newUserId, credentials);
								} else {
									errorMessage = "\"The user you want to delete from this circle is not present in the circle.\"";
									wasError = true;
								}
							} else { // add user to circle
								if(!userExists){
									connector.update(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$push", new BasicDBObject( "users", user )));
									addToHaveInCircleList(newUserId, credentials);
								} else {
									errorMessage = "\"The user you wanted to add already exists in this circle.\"";
									wasError = true;
								}
							}
						} else if(del!=null && del.equals("true")){ // delete circle
							connector.delete(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						}
					}
				}
			} catch (NotConnectedException e) {
				errorMessage = "\"Circle doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Circle doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalArgumentException e){
				errorMessage = "\"Circle doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
				wasError = true;
			} 
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Circle stored successfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Stored circle with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Circle stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Stored circle with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * Updates the haveMeInCircle field of the profile of the user that was removed from a circle
	 * if this particular user is not in any other circles of the current user. 
	 * @param userId The user id of the user we need to check
	 * @param credentials The credentials of the current user
	 */
	private void removeFromHaveInCircleList(String userId, String credentials) {
		try {
			BasicDBList list = new BasicDBList();
			list.add(manager.getUserID(credentials));
			// first check if the user is not in any other circle of the current user anymore, else do nothing
			DBCursor res = (DBCursor) connector.query(MongoDBConnector.CIRCLES_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
			if(res!=null){
				boolean found = false;
				while(res.hasNext()){
					BasicDBList userIds = (BasicDBList) res.next().get("users");
					if(userIds!=null && !userIds.isEmpty()){
						for (int i=0;i<userIds.size();i++) { 
							if(userIds.get(i).toString().contains(userId)){ 
								found = true; 
								break;
							}
						} 
					} 
					if(found){ break; }
				}
				if(!found){ // we need to remove ourself from the other users list
					list = new BasicDBList();
					list.add(new ObjectId(userId));
					res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
					if(res!=null && res.hasNext()){
						BasicDBList usersWhoHaveHim = (BasicDBList) res.next().get("haveMeInCircle");
						if(usersWhoHaveHim!=null && !usersWhoHaveHim.isEmpty()){
							BasicDBObject user = new BasicDBObject("userId", manager.getUserID(credentials));
							connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$pull", new BasicDBObject( "haveMeInCircle", user )));
							System.out.println("updated user collection haveMeInCircle. Deleted from user with id: "+(new ObjectId(userId)).toString());
						} 
					}
				}
			}
			
			
		} catch (NotConnectedException | IllegalQueryException e) {
			if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+e.getMessage());}
		}
	}

	/**
	 * Updates the haveMeInCircle field of the profile of the user that was added to a circle
	 * if this particular user is not already present in this list.
	 * @param userId The user id of the user we need to check
	 * @param credentials The credentials of the current user
	 */
	private void addToHaveInCircleList(String userId, String credentials) {
		try {
			String myId = manager.getUserID(credentials);
			BasicDBList list = new BasicDBList();
			list.add(new ObjectId(userId));
			DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
			if(res!=null && res.hasNext()){
				boolean found = false;
				BasicDBList usersWhoHaveHim = (BasicDBList) res.next().get("haveMeInCircle");
				if(usersWhoHaveHim!=null && !usersWhoHaveHim.isEmpty()){
					for (int i=0;i<usersWhoHaveHim.size();i++) { 
						if(usersWhoHaveHim.get(i).toString().contains(myId)){ 
							found = true; 
							break;
						}
					} 
				} 
				if(!found){
					BasicDBObject user = new BasicDBObject("userId", myId);
					connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$push", new BasicDBObject( "haveMeInCircle", user )));
					System.out.println("updated user collection haveMeInCircle. Added to user with id: "+(new ObjectId(userId)).toString());
				}
			}
		} catch (NotConnectedException | IllegalQueryException e) {
			if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+e.getMessage());}
		}
	}

}
