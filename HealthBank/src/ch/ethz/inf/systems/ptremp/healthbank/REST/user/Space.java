package ch.ethz.inf.systems.ptremp.healthbank.REST.user;

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
 * Servlet implementation class Space
 */
@WebServlet(
		description = "Get and set information on user spaces", 
		urlPatterns = { 
				"/Space", 
				"/space"
		})
public class Space extends HttpServlet {

	private static final long serialVersionUID = -8550082360551645345L;
	private MongoDBConnector connector; 
	private CoreManager manager;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Space() {
        super();
        if(connector == null ) { 
        	connector = MongoDBConnector.getInstance();
        }
		if(manager == null) {
			manager = new CoreManager();
		}
    }

	/**
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
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
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
		String id = request.getParameter("spaceId");
		String name = request.getParameter("name");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'pw' and 'username' with the request.\"";
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
					errorMessage = "\"Space doGet: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					BasicDBList list = new BasicDBList();
					DBCursor res = null;
					if(name!=null && name.length()>0){
						BasicDBObject q1 = new BasicDBObject("userID", new BasicDBObject("$in", list));
						BasicDBObject q2 = new BasicDBObject("name", name);
						list.add(q1);
						list.add(q2);
						res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("$and", list));
					} else if(id!=null && id.length()>0){
						list.add(new ObjectId(id));
						res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
					} else {
						list.add(manager.getUserID(credentials));
						res = (DBCursor) connector.query(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
					}
					if(res==null){
						errorMessage = "\"Space doGet: There was an error, we did not find any spaces. Did you provide the correct sessionKey and credentials?\"";
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
						result.put("spaces", resArray);
						values = result.toJSONString();
					}
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Space doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Space doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Space doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"Space doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned spaces for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned spaces for the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String name = "";
		
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
			String descr = request.getParameter("descr");
			if(name==null || name.length()<2 || descr==null || descr.length()<1){
				errorMessage = "\"Space doPost: Please provide the parameters 'name' and 'descr' with the request.\"";
				wasError = true;
			}
			if(!wasError){
				name = URLDecoder.decode(name, "UTF-8");
				descr = URLDecoder.decode(descr, "UTF-8");
				
				// check DB connection
				if(!connector.isConnected()){
					manager.reconnect();
				}
				
				// check if user is logged in and save the new value
				try {
					if(!manager.isUserLoggedIn(session, credentials)){
						errorMessage = "\"Space doPost: You need to be logged in to use this service\"";
						wasError = true;
						isLoggedIn = false;
					} else {
						HashMap<Object, Object> data = new HashMap<Object, Object>();
						data.put("name", name);
						data.put("descr", descr);
						DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
						data.put("timedate", dateFormat.format(new Date()));
						data.put("userID", manager.getUserID(credentials));
						
						connector.insert(MongoDBConnector.SPACES_COLLECTION_NAME, data);
					}
				} catch (NotConnectedException e) {
					errorMessage = "\"Space doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
					wasError = true;
				} catch (IllegalQueryException e) {
					errorMessage = "\"Space doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
					wasError = true;
				}
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Space stored successfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Stored space with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Space stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Stored space with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * @see HttpServlet#doPut(HttpServletRequest, HttpServletResponse)
	 */
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String session = "", credentials = "";
		
		// check DB connection
		if(!connector.isConnected()){
			manager.reconnect();
		}

		String name = "", descr="", newCircleId = "", id = "";
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		credentials = request.getParameter("credentials");
		session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Space doPut: Please provide the parameters 'pw' and 'username' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check profile specific parameters
			name = request.getParameter("name");
			descr = request.getParameter("descr");
			newCircleId = request.getParameter("circleId");
			id = request.getParameter("id");
			
			if(id==null){
				errorMessage = "\"Space doPut: Please provide the parameters 'id' for the space to update with the request.\"";
				wasError = true;
			}
			if(!wasError){
				name = (name!=null)?URLDecoder.decode(name, "UTF-8"):null;
				descr = (descr!=null)?URLDecoder.decode(descr, "UTF-8"):null;	
				newCircleId = (newCircleId!=null)?URLDecoder.decode(newCircleId, "UTF-8"):null;	
				id = URLDecoder.decode(id, "UTF-8");		
				
				// check if user is logged in and save the new value
				try {
					HashMap<Object, Object> data = new HashMap<Object, Object>();
					if(!manager.isUserLoggedIn(session, credentials)){
						errorMessage = "\"Space doPut: You need to be logged in to use this service\"";
						wasError = true;
						isLoggedIn = false;
					} else {
		                BasicDBList list = new BasicDBList();
						list.add(new ObjectId(id));
						
						if(name!=null || descr!=null){
							if(name!=null){data.put("name", name);}
							if(descr!=null){data.put("descr", descr);}
							connector.update(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", data));
						}
						if(newCircleId!=null){
							BasicDBObject user = new BasicDBObject("userId", newCircleId);
							connector.update(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$push", new BasicDBObject( "circles", user )));
						}
					}
				} catch (NotConnectedException e) {
					errorMessage = "\"Space doPut: We lost connection to the DB. Please try again later. Sorry for that.\"";
					wasError = true;
				} catch (IllegalQueryException e) {
					errorMessage = "\"Space doPut: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
					wasError = true;
				} catch (IllegalArgumentException e){
					errorMessage = "\"Space doPut: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
					wasError = true;
				}
			}
		}
		
	
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Space stored successfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Updated space "+name+" of user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Space stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Updated space "+name+" of user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

	/**
	 * @see HttpServlet#doDelete(HttpServletRequest, HttpServletResponse)
	 */
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		String message = "Space got deleted successfully.";
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("id");
		String circleId = request.getParameter("circleId");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			id = (id!=null)?URLDecoder.decode(id, "UTF-8"):null;
			circleId = URLDecoder.decode(circleId, "UTF-8");
			if(id==null || id.length() < 1){
				errorMessage = "\"Please provide the parameter 'id' with the request.\"";
				wasError = true;
			}
			
			if(!wasError){
				// check DB connection
				if(connector==null || !connector.isConnected()){
					manager.reconnect();
				}
				
				// check if user is logged in
				try {
					if(manager.isUserLoggedIn(session, credentials)){
						BasicDBList list = new BasicDBList();
						if(circleId==null){
							list.add(new ObjectId(id));
							if(!connector.delete(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)))){
								errorMessage = "\"There was an error deleting the space. Did you provide the correct sessionKey and credentials?\"";
								wasError = true;
							}	
						} else {
							BasicDBObject user = new BasicDBObject("circleId", circleId);
							connector.update(MongoDBConnector.SPACES_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$pull", new BasicDBObject( "circles", user ))); // TODO, does not seem to work
							message = "Removed circle from space successfully.";
						}
					} else {
						errorMessage = "\"Either your session timed out or you forgot to send me the session and credentials.\"";
						wasError = true;
						isLoggedIn = false;
					}
				} catch (IllegalQueryException e) {
					errorMessage = "\"There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
					wasError = true;
				} catch (NotConnectedException e) {
					errorMessage = "\"We lost connection to the DB. Please try again later. Sorry for that.\"";
					wasError = true;
				}
			}
		}
				
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \""+message+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted space with id: "+id+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Space doDelete: There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \""+message+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted space with id: "+id+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Space doDelete: There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
