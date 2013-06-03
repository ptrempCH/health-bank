package ch.ethz.inf.systems.ptremp.healthbank.REST.user.authentication;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
 * Servlet implementation class Logout
 */
@WebServlet(
		description = "Logout off the system as a user.", 
		urlPatterns = { 
				"/Logout", 
				"/logout"
		})
public class Logout extends HttpServlet {
       
	private static final long serialVersionUID = 1046607983902042202L;
	private MongoDBConnector connector; 
	private CoreManager manager;

	/**
     * @see HttpServlet#HttpServlet()
     */
    public Logout() {
        super();
	    connector = MongoDBConnector.getInstance();
		manager = new CoreManager();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		response.addHeader("Access-Control-Allow-Origin", "*");
		
		String callback = request.getParameter("callback");
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"error\": \"Please use a Post request for the purpose of logging out.\" } );");
			if(MongoDBConnector.DEBUG){System.out.println("Someone accessed doGet of Logout");}
		} else {
			response.getOutputStream().println("{ \"result\": \"failed\", \"error\": \"Please use a Post request for the purpose of logging out.\" }");
			if(MongoDBConnector.DEBUG){System.out.println("Someone accessed doGet of Logout");}
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
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			BasicDBList list = new BasicDBList();
			list.add(credentials.substring(0, credentials.lastIndexOf(':')));
			try {
				if(manager.isUserLoggedIn(session, credentials)){
					connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)), 
							new BasicDBObject("$set", new BasicDBObject("session_key", "").append("session_expires", "")));
				} else {
					errorMessage = "\"Either your session timed out or you forgot to send me the session and credentials.\"";
					wasError = true;
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"There was an error. Did you provide the correct username and password? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"We lost connection to the DB. Please try again later. Sorry for that.\"";
				wasError = true;
			}
		}
				
		
		if(callback!=null && callback.length()>0){
			callback = URLDecoder.decode(callback, "UTF-8");
			if(!wasError) {
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"message\": \"logged out successfully\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Logged out user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError) {
				response.getOutputStream().println("{ \"result\": \"success\", \"message\": \"logged out successfully\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Logged out user with sessionKey: "+session+"!");}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}

}
