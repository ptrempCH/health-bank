package ch.ethz.inf.systems.ptremp.healthbank.REST.application;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.regex.Pattern;

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
import com.mongodb.DBObject;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
* Servlet implementation class AppQuery
* In this servlet we implement the functionality to query applications and visualizations in many different ways
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "Query for applications. Only doGet is supported", 
		urlPatterns = { 
				"/AppQuery", 
				"/appquery", 
				"/aq", 
				"/Appquery"
		})
public class AppQuery extends HttpServlet {

	private static final long serialVersionUID = 6160259291147408351L;

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
    public AppQuery() {
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
	 * The GET request allows the caller to query for applications and visualizations. There are multiple ways of calling this method. 
	 * Either one can query a specific application/visualization by its id or query applications/visualizations by a query string. If the
	 * caller neither provides the id nor the query parameter, all the applications/visualizations the current user has installed, are
	 * returned. The parameter type which can have the values "app" or "viz" determines if the caller likes the receive results of type 
	 * application or visualization.
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * With query:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - query: A query string which should match applications/visualizations with similar strings in attributes name, descr or institute name
	 * - type: Can have values "app" or "viz" to decide if we query for applications or visualizations
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * With id:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - id: The id of a specific application/visualization
	 * - type: Can have values "app" or "viz" to decide if we query for applications or visualizations
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * Without query or id:
	 * - credentials: This is a credentials string combining the password and user name in a hashed form for security.
	 * - session: This is the current session key of the user
	 * - type: Can have values "app" or "viz" to decide if we query for applications or visualizations
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server) 
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 * 
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String result = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String query = request.getParameter("query");
		String id = request.getParameter("id");
		String type = request.getParameter("type");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"AppQuery: Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		} else if(type==null || type.length()<2 || !(type.equals("app") || type.equals("viz")) ){
			errorMessage = "\"AppQuery: Please provide the parameter 'type' and set its value to either 'app' or 'viz' depending on if you like to retrieve application or visualizations as results.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(query!=null && query.length()>0){query = URLDecoder.decode(query, "UTF-8");}
			if(id!=null && id.length()>0){id = URLDecoder.decode(id, "UTF-8");}
			if(type!=null && type.length()>0){type = URLDecoder.decode(type, "UTF-8");}
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					BasicDBList list = new BasicDBList();
					DBCursor res;
					JSONArray resArray = new JSONArray();
					JSONParser parser = new JSONParser();
					JSONObject resObj;
					if(id!=null && id.length()>0){
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							while(res.hasNext()){
								resObj = (JSONObject) parser.parse(res.next().toString());
								String curType = (String) resObj.get("type");
								if(curType!=null && curType.equals(type)){
									resArray.add(resObj);
								}
							}
						} else {
							errorMessage = "\"No entry found for this id. \"";
							wasError = true;
						}
					} else if(query!=null && query.length()>0){ 
						Pattern regex = Pattern.compile("\\w*"+query+"\\w*");
						list.add(regex);
						regex = Pattern.compile("\\w*"+query.toLowerCase()+"\\w*");
						list.add(regex);
						regex = Pattern.compile("\\w*"+query.toUpperCase()+"\\w*");
						list.add(regex);
						regex = Pattern.compile("\\w*"+query.replace(query.charAt(0), query.substring(0, 1).toUpperCase().charAt(0))+"\\w*");
						list.add(regex);
						DBObject companyName = new BasicDBObject("companyName", new BasicDBObject("$in", list));
						DBObject name = new BasicDBObject("name", new BasicDBObject("$in", list));
						DBObject descr = new BasicDBObject("descr", new BasicDBObject("$in", list));
						BasicDBList or = new BasicDBList();
						or.add(companyName);
						or.add(name);
						or.add(descr);
						DBObject queryObject = new BasicDBObject("$or", or);
						res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, queryObject);
						if(res!=null && res.hasNext()){
							while(res.hasNext()){
								resObj = (JSONObject) parser.parse(res.next().toString());
								String curType = (String) resObj.get("type");
								String curOnline = (String) resObj.get("online");
								if(curType!=null && curType.equals(type) && curOnline!=null && curOnline.equals("online")){
									resArray.add(resObj);
								}
							}
						} else {
							errorMessage = "\"AppQuery: The database query did not return any result. Try to refrase the query!\"";
							wasError = true;
						}
					} else {
						list.add(new ObjectId(manager.getUserID(credentials)));
						res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.hasNext()){
							BasicDBObject myUser = (BasicDBObject) res.next();
							BasicDBList myUsersApps = (BasicDBList)  myUser.get("application");
							if(myUsersApps != null){
								list = new BasicDBList();
								for(Object o : myUsersApps){
									BasicDBObject appID = (BasicDBObject) o;
									String s = appID.getString("app");
									if(s!=null){
										list.add(new ObjectId(s));
									} else {
										s = appID.getString("viz");
										if(s!=null){
											list.add(new ObjectId(s));
										}
									}
								}
								res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
								if(res != null && res.hasNext()){
									while(res.hasNext()){
										resObj = (JSONObject) parser.parse(res.next().toString());
										String curType = (String) resObj.get("type");
										String curOnline = (String) resObj.get("online");
										if(curType!=null && curType.equals(type) && curOnline!=null && curOnline.equals("online")){
											resArray.add(resObj);
										}
									}
								} 
							} 
						}
						if(resArray.isEmpty()){
							errorMessage = "\"AppQuery: We did not find any applications for the currently logged in user.!\"";
							wasError = true;
						}
					}
					if(!wasError){
						resObj = new JSONObject();
						if(type.equals("app")){
							resObj.put("applications", resArray);
						} else {
							resObj.put("visualizations", resArray);
						}
						result = resObj.toJSONString();	
					}
				} else {
					errorMessage = "\"AppQuery: Either your session timed out or you forgot to send me the session and credentials.\"";
					wasError = true;
					isLoggedIn = false;
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"AppQuery: There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"AppQuery: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"AppQuery: There was an error. Did you provide the correct user name and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Gave application query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Gave application query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
