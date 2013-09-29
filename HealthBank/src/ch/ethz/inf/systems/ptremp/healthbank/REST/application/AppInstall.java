package ch.ethz.inf.systems.ptremp.healthbank.REST.application;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.servlet.Servlet;
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

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
* Servlet implementation class AppInstall
* In this servlet we implement the functionality to install and uninstall applications and visualizations
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "Install and uninstall applications and visualizations. Only doPost is supported.", 
		urlPatterns = { 
				"/AppInstall", 
				"/appinstall", 
				"/appInstall"
		})
public class AppInstall extends HttpServlet {
	
	private static final long serialVersionUID = -1576909966241935541L;

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
     * Default constructor
     * @see HttpServlet#HttpServlet()
     */
    public AppInstall() {
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
	 * The GET request allows the caller to check for circles and spaces of the current installed application
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the installed application
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		String values = "";
		
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"AppInstall: Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					BasicDBList list = new BasicDBList();
					DBCursor res;
					if(id!=null && id.length()>0){
						list = new BasicDBList();
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							BasicDBObject obj = (BasicDBObject) res.next();
							String type = obj.getString("type");
							if(type!=null && type.equals("app")){
								list.add(manager.getUserID(credentials));
								BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
								BasicDBObject q2 = new BasicDBObject("appID", id);
								BasicDBList myList = new BasicDBList();
								myList.add(q1);
								myList.add(q2);
								BasicDBObject and = new BasicDBObject("$and", myList);
								res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
								if(res==null || !res.hasNext()){
									errorMessage = "\"AppInstall: We are sorry, but there is no application with this id to be found in the database or you do not have it installed yet.\"";
									wasError = true;
								}
								if(!wasError){
									JSONParser parser = new JSONParser();
									JSONArray resArray = new JSONArray();
									obj = (BasicDBObject) res.next();
									try{
										JSONObject resObj = (JSONObject) parser.parse(obj.toString());
										resArray.add(resObj);
									} catch(ParseException e){
										// ignore since we should not pass this to the user if we do not know what it is on our own.
									}
									JSONObject result = new JSONObject();
									result.put("application", resArray);
									values = result.toJSONString();
								}
							} else {
								errorMessage = "\"AppInstall: We are sorry, but this call is not supported for visualizations.\"";
								wasError = true;
							}
						} else {
							errorMessage = "\"AppInstall: We are sorry, but there is no application with this id to be found in the database.\"";
							wasError = true;
						}
					} else {
						errorMessage = "\"AppInstall: Please provide the parameter 'id' to decide which application/visualization you want to install or deinstall.\"";
						wasError = true;
					}
				} else {
					errorMessage = "\"AppInstall: Either your session timed out or you forgot to send me the session and credentials.\"";
					wasError = true;
					isLoggedIn = false;
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"AppInstall: There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"AppInstall: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} 
		}

		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned application install info to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned application install info to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

	/**
	 * The POST request allows the caller to install and uninstall applications or visualizations for the currently logged in user. If the application 
	 * or visualization is already registered with the user, it will be uninstalled. Else we install and register it with the user. 
	 * So far we only remember which application/visualization the user actually wanted to install. There is no more functionality yet. Likewise when the 
	 * application/visualization gets uninstalled, we only delete the reference on the user site. All records that were added by the application/visualization 
	 * will remain in the database and continue to be visible for the user and the people allowed to see it via circles. 
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * Un-/Install:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the application or visualization the user wants to install or uninstall to or from his account.
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Change circles of installed app:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the application or visualization the user wants to change.
	 * - circles: A list of selected circle ID's separated by a whitespace
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Change spaces of installed app:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of the application or visualization the user wants to change.
	 * - spaces: A list of selected space ID's separated by a whitespace
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server) 
	 * 
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String installed = "Installed";
		boolean wasError = false;
		boolean isLoggedIn = true;
		boolean update = false;
		
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		String spaces = request.getParameter("spaces");
		String circles = request.getParameter("circles");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"AppInstall: Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}
			if(spaces!=null && spaces.length()>0){spaces = URLDecoder.decode(spaces, "UTF-8");}
			if(circles!=null && circles.length()>0){circles = URLDecoder.decode(circles, "UTF-8");}
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					HashMap<Object, Object> data = new HashMap<Object, Object>();
					BasicDBList list = new BasicDBList();
					DBCursor res;
					if(id!=null && id.length()>0){
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							BasicDBObject obj = (BasicDBObject) res.next();
							int nrOfInstalls = -1;
							try{
								nrOfInstalls = obj.getInt("nrOfInstalls");
							} catch (ClassCastException e) {
								System.out.println("AppInstall: There was a ClassCastException: "+e.getMessage()); // ignore further steps, since nrOfInstalls remains -1
							}
						
							list = new BasicDBList();
							list.add(manager.getUserID(credentials));
							BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
							BasicDBObject q2 = new BasicDBObject("appID", id);
							BasicDBList myList = new BasicDBList();
							myList.add(q1);
							myList.add(q2);
							BasicDBObject and = new BasicDBObject("$and", myList);
							res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
							if(res!=null && res.hasNext()){
								if(spaces!=null){ // update spaces
									BasicDBList spas = new BasicDBList();
									for(String s: spaces.split(" ")){
										spas.add(new BasicDBObject("space", s));
									}
									if(!spas.isEmpty()){
										connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, and, new BasicDBObject("$set", new BasicDBObject("spaces", spas)));
									}
									update = true;
									installed = "Updated";
								} else if(circles!=null) { // update circles
									BasicDBList cirs = new BasicDBList();
									for(String s: circles.split(" ")){
										cirs.add(new BasicDBObject("circle", s));
									}
									if(!cirs.isEmpty()){
										connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, and, new BasicDBObject("$set", new BasicDBObject("circles", cirs)));
									}
									update = true;
									installed = "Updated";
								} else { // uninstall
									installed = "Uninstalled";
									// first uninstall it from the possible spaces where it still might be:
									myList = new BasicDBList();
									myList.add(q1);
									myList.add(new BasicDBObject("visualization", id));
									connector.update(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("$and", myList), new BasicDBObject("$unset", new BasicDBObject("visualization", id)));
									// now delete the install entry
									connector.delete(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
									if(nrOfInstalls>0){nrOfInstalls--;}
								}
							} else {  // install
								data.put("userID", manager.getUserID(credentials));
								data.put("appID", id);
								DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
								data.put("timedate", dateFormat.format(new Date()));
								connector.insert(MongoDBConnector.APPLICATION_COLLECTION_NAME, data);
								
								if(nrOfInstalls!=-1){nrOfInstalls++;}
							}
							if(nrOfInstalls!=-1 && !update){
								list.add(new ObjectId(id));
								connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", new BasicDBObject("nrOfInstalls", nrOfInstalls)));
							}
						} else {
							errorMessage = "\"AppInstall: We are sorry, but there is no application with this id to be found in the database.\"";
							wasError = true;
						}
					} else {
						errorMessage = "\"AppInstall: Please provide the parameter 'id' to decide which application/visualization you want to install or deinstall.\"";
						wasError = true;
					}
				} else {
					errorMessage = "\"AppInstall: Either your session timed out or you forgot to send me the session and credentials.\"";
					wasError = true;
					isLoggedIn = false;
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"AppInstall: There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"AppInstall: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} 
		}
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Installation successfull.\" }");
				if(MongoDBConnector.DEBUG){System.out.println(installed+" application to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Installation successfull.\" }");
				if(MongoDBConnector.DEBUG){System.out.println(installed+" application to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
