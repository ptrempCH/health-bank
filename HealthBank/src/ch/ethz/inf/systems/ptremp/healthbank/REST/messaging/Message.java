package ch.ethz.inf.systems.ptremp.healthbank.REST.messaging;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
* Servlet implementation class Message
* In this servlet we implement the functionality to send and retrieve messages between users.
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "Send and get retrieved messages to/from other users", 
		urlPatterns = { 
				"/Message", 
				"/message"
		})
public class Message extends HttpServlet {
	private static final long serialVersionUID = 5249975928276077894L;

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
     * Default Constructor
     * @see HttpServlet#HttpServlet()
     */
    public Message() {
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
	 * The GET request allows the caller to check if there are new mails and to retrieve one or multiple messages. 
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Query if there are new messages:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - hasNew: Set this parameter to any value with at least one characters to check if there are new messages
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Query one single message:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the message to be queried
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Get all received messages:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * All messages to and from a single user:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - userId: The user id of the user to load the messages from. Messages sent to and received from this user will be returned
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * The information is stored in the 'values' attribute of the resulting JSON string.
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 * 
	 * TODO: 
	 * 	- functionality to load message entries in bunches of say 20 or 50 at a time 
	 * 	- group chats
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String values = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		boolean hasNewQuery = false;
		int unread = 0;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		String hasNew = request.getParameter("hasNew");
		String userId = request.getParameter("userId");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'pw' and 'username' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}
			if(hasNew!=null && hasNew.length()>0){hasNew = URLDecoder.decode(hasNew, "UTF-8");}
			if(userId!=null && userId.length()>0){userId = URLDecoder.decode(userId, "UTF-8");}
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in and get the records 
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"Message doGet: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					BasicDBList list = new BasicDBList();
					DBCursor res = null;
					if(id!=null && id.length()>0){
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.MESSAGES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						// since we display this message, it obviously has been read. hence we need to update the status of the message TODO: Make sure it is not updated, when you display sent messages
						HashMap<Object, Object> data = new HashMap<Object, Object>();
						data.put("read", "true");
						connector.update(MongoDBConnector.MESSAGES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
					} else if(hasNew!=null && hasNew.length()>0){
						list.add(manager.getUserID(credentials));
						res = (DBCursor) connector.query(MongoDBConnector.MESSAGES_COLLECTION_NAME, new BasicDBObject("recipientID", new BasicDBObject("$in", list)));
						if(res!=null & res.hasNext()){
							JSONParser parser = new JSONParser();
							while (res.hasNext()) {
								DBObject obj = res.next();
								JSONObject resObj = (JSONObject) parser.parse(obj.toString());
								Object s = resObj.get("read");
								if(s!=null && s.toString().equals("false")){
									unread++;
									hasNewQuery = true;
								}
							}
						}
					} else if(userId!=null && userId.length()>0){
						BasicDBList or = new BasicDBList();
						BasicDBList and = new BasicDBList();
						and.add(new BasicDBObject("senderID", userId));
						and.add(new BasicDBObject("recipientID", manager.getUserID(credentials)));
						or.add(new BasicDBObject("$and", and));
						and = new BasicDBList();
						and.add(new BasicDBObject("senderID", manager.getUserID(credentials)));
						and.add(new BasicDBObject("recipientID", userId));
						or.add(new BasicDBObject("$and", and));
						res = (DBCursor) connector.query(MongoDBConnector.MESSAGES_COLLECTION_NAME, new BasicDBObject("$or", or));
					} else {
						list.add(manager.getUserID(credentials));
						res = (DBCursor) connector.query(MongoDBConnector.MESSAGES_COLLECTION_NAME, new BasicDBObject("recipientID", new BasicDBObject("$in", list)));
					}
					if(res==null){
						errorMessage = "\"Message doGet: There was an error, we did not find any messages. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						JSONParser parser = new JSONParser();
						JSONArray resArray = new JSONArray();
						if(hasNewQuery){
							JSONObject resObj = new JSONObject();
							resObj.put("hasNew", "true");
							resArray.add(resObj);
							resObj = new JSONObject();
							resObj.put("unreadItems", unread);
							resArray.add(resObj);
						} else {
							while(res.hasNext()){
								DBObject obj = res.next();
								JSONObject resObj = (JSONObject) parser.parse(obj.toString());
								resArray.add(resObj);
							}
						}
						JSONObject result = new JSONObject();
						result.put("messages", resArray);
						values = result.toJSONString();
					}
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Message doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Message doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Message doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"Message doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned messages for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned messages for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * The POST request allows the caller to send a new message
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Create a new news entry:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - recipient: The recipient of the message
	 * - subject: A short preview for the news entry
	 * - message: The actual content of the news entry in HTML
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * The information is stored in the 'values' attribute of the resulting JSON string.
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 * 
	 *	TODO:
	 *	- So far we can only send messages to a single other user. Allow group chats
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String recipient = "", subject = "", message = "", userID = "";
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Message doPost: Please provide the parameters 'session' and 'credentials' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check record specific parameters
			recipient = request.getParameter("recipient");
			subject = request.getParameter("subject");
			message = request.getParameter("message");
			if(recipient==null || recipient.length()<2 || subject==null || subject.length()<1 || message==null || message.length()<1){
				errorMessage = "\"Message doPost: Please provide the parameters 'recipient', 'subject' and 'message' with the request.\"";
				wasError = true;
			}
			if(!wasError){
				try {
					recipient = URLDecoder.decode(recipient, "UTF-8");
					subject = URLDecoder.decode(subject, "UTF-8");
					message = URLDecoder.decode(message, "UTF-8");
					userID = manager.getUserID(credentials);
					
					// check DB connection
					if(!connector.isConnected()){
						manager.reconnect();
					}
					
					// check if user is logged in and save the new value
					if(!manager.isUserLoggedIn(session, credentials)){
						errorMessage = "\"Message doPost: You need to be logged in to use this service\"";
						wasError = true;
						isLoggedIn = false;
					} else {
						// check if recipient is a correct user id and save the message if so
						BasicDBList list = new BasicDBList();
						list.add(new ObjectId(recipient));
						DBCursor res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							HashMap<Object, Object> data = new HashMap<Object, Object>();
							data.put("recipientID", recipient);
							data.put("subject", subject);
							data.put("message", message);
							data.put("read", "false"); 
							DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
							data.put("timedate", dateFormat.format(new Date()));
							data.put("senderID", userID);
							connector.insert(MongoDBConnector.MESSAGES_COLLECTION_NAME, data);
						} else {
							errorMessage = "\"Message doPost: The recipient you sent with the request does not exist in the system.\"";
							wasError = true;
						}
					}
				} catch (NotConnectedException e) {
					errorMessage = "\"Message doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
					wasError = true;
				} catch (IllegalQueryException e) {
					errorMessage = "\"Message doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
					wasError = true;
				}
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Message sendsuccessfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Sent message from user "+userID+" to "+recipient+" by user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Message sendsuccessfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Sent message from user "+userID+" to "+recipient+" by user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

}
