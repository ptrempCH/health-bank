package ch.ethz.inf.systems.ptremp.healthbank.REST.user.authentication;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.*;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.IllegalQueryException;
import ch.ethz.inf.systems.ptremp.healthbank.exceptions.NotConnectedException;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 * Servlet implementation class Login
 */
@WebServlet(
		description = "Login to the system as a user.", 
		urlPatterns = { 
				"/Login", 
				"/login"
		})
public class Login extends HttpServlet {
	
	private static final long serialVersionUID = 6670295932140153609L;
	private MongoDBConnector connector; 
	private CoreManager manager;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Login() {
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
		String errorMessage = "";
		boolean wasError = false;
		boolean loggedIn = true;
		
		// check parameters user specific parameters
		String callback = request.getParameter("callback");
		String credentials = request.getParameter("credentials");
		String session = request.getParameter("session");
		if(credentials==null || credentials.length()<2 || session==null || session.length()<4){
			errorMessage = "\"In Login doGet: Please provide the parameters 'pw' and 'username' with the request.\"";
			wasError = true;
		}
		if(!wasError){
			credentials = URLDecoder.decode(credentials, "UTF-8");
			session = URLDecoder.decode(session, "UTF-8");
			credentials = StringUtils.newStringUtf8(Base64.decodeBase64(credentials));
			
			// check DB connection
			if(!connector.isConnected()){
				manager.reconnect();
			}
			
			// check if user is logged in
			try {
				if(!manager.isUserLoggedIn(session, credentials)){
					errorMessage = "\"In Login doGet: You need to be logged in to use this service. Check logged in failed!\"";
					wasError = true;
					loggedIn = false;
				} else {
					BasicDBList list = new BasicDBList();
					list.add(credentials.substring(0, credentials.lastIndexOf(':')));
					DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
					Date date = new Date((new Date()).getTime() + (60L*60L*1000L));  // get time plus an hour
					connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)), 
							new BasicDBObject("$set", new BasicDBObject("session_expires", dateFormat.format(date))));
				}
			} catch (IllegalQueryException e) {
				errorMessage = "\"In Login doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			} catch (NotConnectedException e) {
				errorMessage = "\"In Login doGet: There was an error. Did you provide the correct sessionKey and credentials? Error: "+e.getMessage()+"\"";
				wasError = true;
			}
			
			if(callback!=null && callback.length()>0){
				callback = URLDecoder.decode(callback, "UTF-8");
				if(!wasError){
					response.getOutputStream().println(callback+"( { \"result\": \"success\", \"status\": \"loggedIn\", \"message\": \"you are logged in.\" } );");
					if(MongoDBConnector.DEBUG){System.out.println("User with sessionKey: "+session+" is still logged in.");}
				}
				else {
					if(loggedIn){
						response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"status\": \"unknown\", \"error\": "+errorMessage+" } );");
					} else {
						response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"status\": \"loggedOut\", \"error\": "+errorMessage+" } );");
					}
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
				}
			} else {
				if(!wasError){
					response.getOutputStream().println("{ \"result\": \"success\", \"status\": \"loggedIn\", \"message\": \"you are logged in.\" }");
					if(MongoDBConnector.DEBUG){System.out.println("User with sessionKey: "+session+" is still logged in.");}
				}
				else {
					if(loggedIn){
						response.getOutputStream().println("{ \"result\": \"failed\", \"status\": \"unknown\", \"error\": "+errorMessage+" }");
					} else {
						response.getOutputStream().println("{ \"result\": \"failed\", \"status\": \"loggedOut\", \"error\": "+errorMessage+" }");
					}
					if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
				}
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
		
		// check parameters
		String callback = request.getParameter("jsonp_callback");
		String pw = request.getParameter("pw");
		String username = request.getParameter("username");
		if(pw==null || pw.length()<6 || username==null || username.length()<4){
			errorMessage = "\"Please provide the parameters 'pw' and 'username' with the request.\"";
			wasError = true;
		}
		String sessionKey = "";
		if(!wasError){
			pw = URLDecoder.decode(pw, "UTF-8");
			username = URLDecoder.decode(username, "UTF-8");
			String credentials = StringUtils.newStringUtf8(Base64.decodeBase64(pw));
			pw = credentials.substring(credentials.indexOf(":")+1);
			
			// check DB connection
			if(connector==null || !connector.isConnected()){
				manager.reconnect();
			}
			
			// query for the right user
			DBCursor res = null;
			BasicDBList list = new BasicDBList();
			list.add(username);
			try {
				res = (DBCursor) connector.query(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)));
				if(res==null){
					errorMessage = "\"There was an error. Did you provide the correct username and password?\"";
					wasError = true;
				}
				
				if(!wasError){
					DBObject user = res.next();
					String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(pw); // password is saved encrypted. Hence we need to encrypt it here too
					if(user.get("password").equals(md5)){
						DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
						Date date = new Date((new Date()).getTime() + (60L*60L*1000L));  // get time plus an hour
						sessionKey = dateFormat.format(new Date())
								+request.getRemoteAddr()
								+StringUtils.newStringUtf8(Base64.encodeBase64(credentials.getBytes()))
								+getMACAddress();
						sessionKey = org.apache.commons.codec.digest.DigestUtils.md5Hex(sessionKey);
						connector.update(MongoDBConnector.USER_COLLECTION_NAME, new BasicDBObject("username", new BasicDBObject("$in", list)), 
									new BasicDBObject("$set", new BasicDBObject("session_key", sessionKey).append("session_expires", dateFormat.format(date))
											.append("last_login", dateFormat.format(new Date()))));
					} else {
						wasError = true;
						errorMessage = "\"wrong password\"";
					}
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
			if(!wasError){
				response.getOutputStream().println(callback+"( { \"result\": \"success\", \"message\": \"logged in as: "+username+"\", " +
						"\"session\" : \""+sessionKey+"\" } );");
				if(MongoDBConnector.DEBUG){System.out.println("Logged in user: "+username+" with SessionKey: " + sessionKey);}
			}
			else {
				response.getOutputStream().println(callback+"( { \"result\": \"failed\", \"error\": "+errorMessage+" } );");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} else {
			if(!wasError){
				response.getOutputStream().println("{ \"result\": \"success\", \"message\": \"logged in as: "+username+"\", " +
						"\"session\" : \""+sessionKey+"\" }");
				if(MongoDBConnector.DEBUG){System.out.println("Logged in user: "+username+" with SessionKey: " + sessionKey);}
			}
			else {
				response.getOutputStream().println("{ \"result\": \"failed\", \"error\": "+errorMessage+" }");
				if(MongoDBConnector.DEBUG){System.out.println("There was an error: "+errorMessage+"!");}
			}
		} 
	}
	
	private String getMACAddress() {
		InetAddress ip;
		try {
			ip = InetAddress.getLocalHost();	 
			NetworkInterface network = NetworkInterface.getByInetAddress(ip);
			byte[] mac = network.getHardwareAddress();
	 
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mac.length; i++) {
				sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));		
			}
			return sb.toString();
		} catch (UnknownHostException | SocketException e) {
			return null;
		}
	}

}
