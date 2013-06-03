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
    private static final int DEFAULT_BUFFER_SIZE = 10240; // 10KB.
    private MongoDBConnector connector; 
	private CoreManager manager;
       
    /**
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
