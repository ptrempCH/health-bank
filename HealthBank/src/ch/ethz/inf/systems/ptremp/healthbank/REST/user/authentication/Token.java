package ch.ethz.inf.systems.ptremp.healthbank.REST.user.authentication;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
* Servlet implementation class Token
* In this servlet we implement the functionality to get a identification token for applications
* 
* @author Patrick Tremp
*/
@WebServlet(
		description = "With this service an application can get a user specific token to upload data using its secret, the user id and the application id", 
		urlPatterns = { 
				"/Token", 
				"/token"
		})
public class Token extends HttpServlet {
	private static final long serialVersionUID = -2125057882116770L;

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
    public Token() {
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
	 * The GET request allows the caller to get a user specific token for an application
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * - appID: The application identification (ObjectID) of the calling application
	 * - userID: The ObjectID of the user the token shall be valid for
	 * - secret: The application secret of the calling application
	 * - (callback: (optional) For JSONP requests, one can add the callback parameter, which will result in a JSONP response from the server)
	 * 
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		String errorMessage = "";
		String token = "";
		boolean wasError = false;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String appId = request.getParameter("appId");
		String userId = request.getParameter("userId");
		String secret = request.getParameter("secret");
		if(appId==null || appId.length()<2 || !ObjectId.isValid(appId) || secret==null || secret.length()<4 || userId==null || userId.length()<4 || !ObjectId.isValid(userId)){
			errorMessage = "\"Please provide the parameters 'secret', 'userId' and 'appId' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			secret = URLDecoder.decode(secret, "UTF-8");
			appId = URLDecoder.decode(appId, "UTF-8");
			userId = URLDecoder.decode(userId, "UTF-8");
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in and get the records 
			try {
				DBCursor res;
				BasicDBList list = new BasicDBList();
				// first check if the app secret is correct
				list.add(new ObjectId(appId));
				res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, new BasicDBObject("_id", new BasicDBObject("$in", list)));
				if(res!=null && res.hasNext()){
					BasicDBObject app = (BasicDBObject) res.next();
					String appSecret = (String) app.get("secret");
					if(appSecret==null || appSecret.length()<1 || !appSecret.equals(secret)){
						wasError=true;
						errorMessage = "Secret not correct!";
					}
				}
				if(!wasError){ // then check if the user has installed the app
					BasicDBObject q1 = new BasicDBObject("userID", userId);
					BasicDBObject q2 = new BasicDBObject("appID", appId);
					ArrayList<BasicDBObject> myList = new ArrayList<BasicDBObject>();
					myList.add(q1);
					myList.add(q2);
					BasicDBObject and = new BasicDBObject("$and", myList);
					res = (DBCursor) connector.query(MongoDBConnector.APPLICATION_COLLECTION_NAME, and);
					if(res==null || !res.hasNext()){
						wasError=true;
						errorMessage = "User does not have this app installed!";
					}
					if(!wasError){ // now create a token
						DateFormat dateFormat = new SimpleDateFormat("ddMMyyyyHHmmss");
						token = userId + manager.randomString(32) + dateFormat.format(new Date()) + appId;
						token = org.apache.commons.codec.digest.DigestUtils.md5Hex(token);
						dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
						Date date = new Date((new Date()).getTime() + (5L*60L*1000L));
						connector.update(MongoDBConnector.APPLICATION_COLLECTION_NAME, and, new BasicDBObject("$set", new BasicDBObject("token", token).append("token_expires", dateFormat.format(date))));
					}
				}
				
				
			} catch (IllegalArgumentException e) {
				errorMessage = "\"Token doGet: There was an error. Did you provide the correct parameters? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (IllegalQueryException e) {
				errorMessage = "\"Token doGet: There was an error. Did you provide the correct parameters? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"Token doGet: There was a connection error to the database. Please try again later.\"";
				wasError = true;
			} 
		}
		
		// Finally give the user feedback
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"token\" : "+token+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Returned token for the app with appId "+appId);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"error\" : "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"token\" : "+token+" }");
				if(MongoDBConnector.DEBUG){System.out.println("Returned token for the app with appId "+appId);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"error\" : "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage);}
			}
		} 
	}

}
