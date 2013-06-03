package ch.ethz.inf.systems.ptremp.healthbank.REST.records;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
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
 * Servlet implementation class RecordQuery
 */
@WebServlet(
		description = "Query for Records", 
		urlPatterns = { 
				"/RecordQuery", 
				"/recordquery", 
				"/rq"
		})
public class RecordQuery extends HttpServlet {

	private static final long serialVersionUID = 3599963803086380098L;
	private MongoDBConnector connector; 
	private CoreManager manager;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public RecordQuery() {
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
		String result = "";
		boolean wasError = false;
		boolean isLoggedIn = true;
		
		// check parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		String query = request.getParameter("query");
		String recordId = request.getParameter("id");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"Please provide the parameters 'credentials' and 'session' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			if(query!=null && query.length()>0){query = URLDecoder.decode(query, "UTF-8");}
			if(recordId!=null && recordId.length()>0){recordId = URLDecoder.decode(recordId, "UTF-8");}
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					BasicDBList list = new BasicDBList();
					DBCursor res;
					if(recordId!=null && recordId.length()>0){
						list.add(recordId);
						res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
						if(res!=null && res.size()>0){
							// TODO, here query is an id. We need to:
							// 1) check if the current user is allowed to see anything (is in circle of other user)
							// 2) retrieve info from other and send back to caller
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
						DBObject app = new BasicDBObject("app", new BasicDBObject("$in", list));
						DBObject name = new BasicDBObject("name", new BasicDBObject("$in", list));
						DBObject descr = new BasicDBObject("descr", new BasicDBObject("$in", list));
						BasicDBList or = new BasicDBList();
						or.add(app);
						or.add(name);
						or.add(descr);
						DBObject queryObject = new BasicDBObject("$or", or);
						int foundItemsCount = 0;
						JSONArray resArray = new JSONArray();
						res = (DBCursor) connector.query(MongoDBConnector.RECORDS_COLLECTION_NAME, queryObject);
						if(res!=null && res.size()>0){
							JSONParser parser = new JSONParser();
							while(res.hasNext()){
								DBObject obj = res.next();
								JSONObject resObj = (JSONObject) parser.parse(obj.toString());
								resArray.add(resObj);
								foundItemsCount++;
							}
						} 
						if(foundItemsCount>0){
							JSONObject resObj = new JSONObject();
							resObj.put("records", resArray);
							result = resObj.toJSONString();						
						} else {
							errorMessage = "\"The database query did not return any result. Try to refrase the query!\"";
							wasError = true;
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
			} catch (ParseException e) {
				errorMessage = "\"There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
		}
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Gave user query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"loggedOut\": \""+!isLoggedIn+"\", \"message\": \"Here are the results.\", \"values\" : "+result+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Gave user query answer to user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"loggedOut\": \""+!isLoggedIn+"\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
