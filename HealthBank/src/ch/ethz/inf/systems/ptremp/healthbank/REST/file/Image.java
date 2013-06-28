package ch.ethz.inf.systems.ptremp.healthbank.REST.file;

import java.io.BufferedOutputStream;
import java.io.IOException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;


import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;

import ch.ethz.inf.systems.ptremp.healthbank.db.MongoDBConnector;
import ch.ethz.inf.systems.ptremp.healthbank.logic.CoreManager;

/**
 * Servlet implementation class Image
 * In this servlet we implement the functionality to query for images stored in the DB
 * 
 * @author Patrick Tremp
 */
@WebServlet(
		description = "Get images from the database", 
		urlPatterns = { 
				"/Image", 
				"/image"
		})
public class Image extends HttpServlet {

	// Constants ----------------------------------------------------------------------------------

	private static final long serialVersionUID = -2419172287586119980L;
	
	/**
	 * Defines the default buffer size used throughout all the buffers used.
	 */
    private static final int DEFAULT_BUFFER_SIZE = 10240; // 10KB.

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
    public Image() {
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
	 * The GET request allows the caller to retrieve an image from the database
	 * 
	 * For a successful call the following parameters need to be present in the URL:
	 * - name: The name of the image to retrieve. If an image with this name can not be found, a 404 Error is returned.
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 * 
	 * TODO: protect this with session and credentials. Only logged in users shall be allowed to see images AND the shall
	 * be allowed to only see their images and images of users who allowed them to see those. 
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.addHeader("Access-Control-Allow-Origin", "*");
		// Get Name from request.
        String fileName = request.getParameter("name");

        // Check if Name is supplied to the request.
        if (fileName == null) {
            // Throw an exception, or send 404.
            response.sendError(HttpServletResponse.SC_NOT_FOUND); // 404.
            return;
        }

        // Lookup File by FileName in database.
        GridFS gfsPhoto = new GridFS( connector.getRootDatabase(), MongoDBConnector.USER_COLLECTION_NAME );
		GridFSDBFile imageForOutput = gfsPhoto.findOne(fileName);
		
		if(imageForOutput==null){
			response.sendError(HttpServletResponse.SC_NOT_FOUND); // 404.
            return;
		}

        // Init servlet response.
        response.reset();
		response.addHeader("Access-Control-Allow-Origin", "*");
        response.setBufferSize(DEFAULT_BUFFER_SIZE);
        response.setContentType(imageForOutput.getContentType());
        response.setHeader("Content-Length", String.valueOf(imageForOutput.getLength()));
        response.setHeader("Content-Disposition", "attachment; filename=\"" + imageForOutput.getFilename() + "\"");

        // Prepare stream.
        BufferedOutputStream output = null;

        try {
            // Open stream.
			output = new BufferedOutputStream(response.getOutputStream(), DEFAULT_BUFFER_SIZE);
			byte[] image = IOUtils.toByteArray(imageForOutput.getInputStream());
			image = Base64.encodeBase64(image);
			response.setHeader("Content-Length", String.valueOf(image.length));
            
    		output.write(image);
            
            
        } finally {
        	try {
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
	}


}
