package ch.ethz.inf.systems.ptremp.healthbank.REST.records;

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
 * Servlet implementation class Record
 */
@WebServlet(
		description = "Create, Edit, Read and Delete a record in the system", 
		urlPatterns = { 
				"/Record", 
				"/record", 
				"/rec"
		})
public class Record extends HttpServlet {
       
	private static final long serialVersionUID = 3311407018317758139L;
	private MongoDBConnector connector; 
	private CoreManager manager;

	/**
     * @see HttpServlet#HttpServlet()
     */
    public Record() {
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
		int nrRes=0;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String id = request.getParameter("userid");
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
					errorMessage = "\"Record doGet: You need to be logged in to use this service\"";
					wasError = true;
					isLoggedIn = false;
				} else {
					if(id!=null && id.length()>0){
						// TODO check if request provided an id field and wants to query for a certain user. If not, query is for current user
						// to do so: 
						// 1) check if id is valid user id
						// 2) check if current user is allowed to get this information (other has current in circle)
						// 3) Change list below to the provided id
						// 4) continue as before
					}
					BasicDBList list = new BasicDBList();
					list.add(manager.getUserID(credentials));
					DBCursor res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
					if(res==null){
						errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials?\"";
						wasError = true;
					}
					if(!wasError){
						nrRes = res.size();
						if(nrRes==0){
							values = "";
						} else {
							JSONParser parser = new JSONParser();
							JSONArray resArray = new JSONArray();
							while(res.hasNext()){
								DBObject obj = res.next();
								JSONObject resObj = (JSONObject) parser.parse(obj.toString());
								resArray.add(resObj);
							}
							JSONObject result = new JSONObject();
							result.put("records", resArray);
							values = result.toJSONString();
						}
					}
				}
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Record doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} catch (ParseException e) {
				errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"size\" : \""+nrRes+"\", \"values\" : "+values+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned "+nrRes+" records to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"size\" : \""+nrRes+"\", \"values\" : "+values+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned "+nrRes+" records to the user with sessionKey "+session);}
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
		String id = "";
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Record doPost: Please provide the parameters 'pw' and 'username' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check record specific parameters
			try {
				id = request.getParameter("id");
				if(id!=null && id.length()>0){ // update
					String circle = request.getParameter("circle");
					if(circle==null || circle.length()<2){
						errorMessage = "\"Record doPost: Please provide the parameters 'circle' with the request, if you want to add a new circle.\"";
						wasError = true;
					} else {
						HashMap<Object, Object> data = new HashMap<Object, Object>();
						for(String s: circle.split(" ")){
							data.put("circle", s);
						}
						BasicDBList list = new BasicDBList();
						list.add(new ObjectId(id));
						connector.update(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)), new BasicDBObject("$set", new BasicDBObject("circles", data)));
					}
				} else { // insert
					name = request.getParameter("name");
					String descr = request.getParameter("descr");
					String values = request.getParameter("values");
					if(name==null || name.length()<2 || values==null || values.length()<5 || descr==null || descr.length()<1){
						errorMessage = "\"Record doPost: Please provide the parameters 'name', 'descr' and 'values' with the request.\"";
						wasError = true;
					}
					if(!wasError){
						name = URLDecoder.decode(name, "UTF-8");
						descr = URLDecoder.decode(descr, "UTF-8");
						values = URLDecoder.decode(values, "UTF-8");
						
						// check DB connection
						if(!connector.isConnected()){
							manager.reconnect();
						}
						
						// check if user is logged in and save the new value
						if(!manager.isUserLoggedIn(session, credentials)){
							errorMessage = "\"Record doPost: You need to be logged in to use this service\"";
							wasError = true;
							isLoggedIn = false;
						} else {
							HashMap<Object, Object> data = new HashMap<Object, Object>();
							data.put("name", name);
							data.put("descr", descr);
							DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
							data.put("timedate", dateFormat.format(new Date()));
							
							JSONParser jsonParser = new JSONParser();
							data.put("userID", manager.getUserID(credentials));
							JSONObject json = (JSONObject) jsonParser.parse(values);
							for(Object key : json.keySet()){
								Object value = json.get(key);
								data.put(key, value);
							}
							
							// TODO  File upload
							
							connector.insert(MongoDBConnector.RECORDS_COLLECTION_NAME, data);
						}
					}
				}
			} catch (ParseException e) {
				errorMessage = "\"Record doPost: There was an error while parsing your JSON in parameter values. Did you provide correct JSON? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Record doPost: We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Record doPost: There was an internal error. Please contact an administrator and provide him/her with this message: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Stored record with name "+name+" to the user with sessionKey "+session);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\" : \""+errorMessage+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Entry stored successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Stored record with name "+name+" to the user with sessionKey "+session);}
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
		response.getWriter().append("{ \"error\" : \"Not yet implemented\"");
		// TODO
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
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// TODO: Do we really need the id here? And please make sure you are doing this right with objectID etc...
			String id = request.getParameter("id");
			if(id==null || id.length()<1){
				errorMessage = "\"Please provide the parameter 'id' with the request.\"";
				wasError = true;
			}
			if(!wasError){
				id = URLDecoder.decode(id, "UTF-8");
			
				// check DB connection
				if(connector==null || !connector.isConnected()){
					manager.reconnect();
				}
				
				// check if user is logged in
				try {
					if(manager.isUserLoggedIn(session, credentials)){
						BasicDBList list = new BasicDBList();
						list.add(manager.getUserID(credentials));
						DBCursor res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, new BasicDBObject("userID", new BasicDBObject("$in", list)));
						if(res==null){
							errorMessage = "\"Record doGet: There was an error. Did you provide the correct sessionKey and credentials?\"";
							wasError = true;
						}
						if(!wasError){
							while(res.hasNext()){
								res.next();
								if(res.curr().get("_id").equals(new ObjectId(id))){
									if(!connector.delete(MongoDBConnector.RECORDS_COLLECTION_NAME, res.curr())){
										errorMessage = "\"There was an error deleting the record. Did you provide the correct sessionKey and credentials?\"";
										wasError = true;
									}
									break;
								}
							}
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
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"User got deleted successfully.\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Profile doDelete: There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"User got deleted successfully.\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Deleted user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Profile doDelete: There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
