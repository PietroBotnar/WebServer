package webserver;

import in2011.http.MessageFormatException;
import in2011.http.Request;
import in2011.http.Response;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.CREATE;
import java.util.Date;
import org.apache.http.client.utils.DateUtils;

public class WebServer {

    private int port;
    private String rootDir;
    private boolean logging;

    public WebServer(int port, String rootDir) {
        this.port = port;
        this.rootDir = rootDir;
    }

    public void start() throws IOException, MessageFormatException {
        //create a server socket
        ServerSocket serverSocket = new ServerSocket(port);
        while (true) {
            //listen for a new connection on the server socket
            Socket connection = serverSocket.accept();
            //initialise a new thread for each client connected
            Thread clientThread = new Thread(new MultiThreadingService(connection, rootDir));
            //start the client thread
            clientThread.start();
        }
    }

    public static void main(String[] args) throws IOException, MessageFormatException {
        String usage = "Usage: java webserver.WebServer <port-number> <root-dir>";
        if (args.length != 2) {
            throw new Error(usage);
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new Error(usage + "\n" + "<port-number> must be an integer");
        }
        String rootDir = args[1];
        WebServer server = new WebServer(port, rootDir);
        server.start();
    }

    static class MultiThreadingService implements Runnable {

        private Socket client_socket;
        private String rootDir;

        MultiThreadingService(Socket socket, String rootDir) {
            this.client_socket = socket;
            this.rootDir = rootDir;
        }

        @Override
        public void run() {
            try {
                //get the input stream from the client
                InputStream is = client_socket.getInputStream();
                //get the output stream from the client
                OutputStream os = client_socket.getOutputStream();
                //declare a response message
                Response msg = null;
                //declare a request message
                Request request = null;
                try {
                    //parse the request from the input stream
                    request = Request.parse(is);
                    //check if the HTTP version is 1.1
                    if (request.getVersion().equals("1.1")) {
                        //check if the request starts with GET
                        if (request.getMethod().startsWith("GET")) {
                            //create the absolute path of the requested file and decode URI if encoded
                            String absolute_filepath = rootDir + URLDecoder.decode(request.getURI());
                            //create a Path object to the file and normalize it
                            Path path = Paths.get(absolute_filepath).normalize();
                            File file = new File(path.toString());
                            //create a boolean variable to check if the requested file exists
                            boolean fileExists = new File(path.toString()).exists();
                            //create last modified date and format it to an HTTP format
                            Date last_modified = new Date(file.lastModified());
                            String t = DateUtils.formatDate(last_modified);
                            Date tmp = DateUtils.parseDate(t);
                            //if the requested file exists
                            if (fileExists) {
                                //if there is not a If-Modified-Since header
                                if (request.getHeaderFieldValue("If-Modified-Since") == null) {
                                    //create a 200 response message
                                    msg = new Response(200);
                                } else {
                                    //if the If-Modified-Since header is defined
                                    try {
                                        //parse the date from the header and format it to an HTTP format
                                        String s = request.getHeaderFieldValue("If-Modified-Since");
                                        Date imsd = DateUtils.parseDate(s);
                                        Date d = DateUtils.parseDate(DateUtils.formatDate(imsd));
                                        //check if last-modified is before if-modified-since
                                        if (tmp.before(d)) {
                                            //if yes respond with 304
                                            msg = new Response(304);
                                        } else {
                                            //if not respond with 200
                                            msg = new Response(200);
                                        }
                                    } catch (Exception e) {
                                        //if an exception occurs respond with 400
                                        System.out.println(e);
                                        msg = new Response(400);
                                    }
                                }
                                //add Last-Modified header field to the response message
                                msg.addHeaderField("Last-Modified", DateUtils.formatDate(last_modified));
                                double file_size = file.length();
                                //add Content-Length header
                                msg.addHeaderField("Content-Length", Double.toString(file_size));
                                String type = Files.probeContentType(path);
                                //add Content-Type field
                                msg.addHeaderField("Content-Type", type);
                                //write the message to the output stream
                                msg.write(os);
                                //create a file input stream to read the file
                                InputStream fis = new FileInputStream(path.toString());
                                //read each byte and write it to the output stream
                                try {
                                    int b = fis.read();
                                    while (b != -1) {
                                        os.write(b);
                                        b = fis.read();
                                    }
                                } catch (IOException e) {
                                    System.out.println(e);
                                }
                            } else {
                                //if file is not found
                                if (request.getURI().startsWith("/")) {
                                    //if URI starts with "/" ,create 404 response message
                                    msg = new Response(404);
                                } else {
                                    //if URI does not start with "/", create 400 response message
                                    msg = new Response(400);
                                }
                                //write the message to the output stream
                                msg.write(os);
                            }
                            //check if the request starts with HEAD
                        } else if (request.getMethod().startsWith("HEAD")) {
                            //create the absolute path of the requested file
                            String absolute_filepath = rootDir + request.getURI();
                            //create a Path object to the file and normalize it
                            Path path = Paths.get(absolute_filepath).normalize();
                            //create a boolean variable to check if the requested file exists
                            boolean fileExists = new File(path.toString()).exists();
                            //if the requested file exists
                            if (fileExists) {
                                //respond with 200 response message
                                msg = new Response(200);
                                //write the message to the output stream
                                msg.write(os);
                            } else {
                                //if file is not found
                                if (request.getURI().startsWith("/")) {
                                    //if URI starts with "/" ,create 404 response message
                                    msg = new Response(404);
                                } else {
                                    //if URI does not start with "/", create 400 response message
                                    msg = new Response(400);
                                }
                                //write the message to the output stream
                                msg.write(os);
                            }
                            //check if the request starts with PUT
                        } else if (request.getMethod().startsWith("PUT")) {
                            //create the absolute path of the requested file
                            String absolute_filepath = rootDir + request.getURI();
                            //create a Path object to the file and normalize it
                            Path path = Paths.get(absolute_filepath).normalize();
                            //initialize a new File object to the filepath
                            File file = new File(path.toString());
                            //create a boolean variable to check if the file exists
                            boolean fileExists = file.exists();
                            //check if URI starts with "/"
                            if (request.getURI().startsWith("/")) {
                                //check if the selected folder exists
                                boolean folderExists = new File(file.getParent()).exists();
                                if (!folderExists) {
                                    //if the folder does not exists, initialize a new File object with the filepath of the folder
                                    File dir = new File(file.getParent());
                                    //create the directory/directories
                                    dir.mkdirs();
                                }
                                if (fileExists) {
                                    //if the file already exists create a new 403 response message
                                    msg = new Response(403);
                                    //write the message to the output stream
                                    msg.write(os);
                                } else if (!fileExists && request.getHeaderFieldValue("Content-Length") != null) {
                                    //if file does not exist and content-length value is not null
                                    String length = request.getHeaderFieldValue("Content-Length");
                                    double size = Double.valueOf(length);
                                    //check if the size exceeds 1mb
                                    if (size > (1024 * 1024)) {
                                        //if yes respond with 400
                                        msg = new Response(400);
                                        //write the message to the output stream
                                        msg.write(os);
                                    } else {
                                        //if size is not exceeded
                                        //create a new buffered output stream and atomically create the file
                                        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file.toPath(), CREATE))) {
                                            //read bytes from input stream and write them to the file
                                            int b = is.read();
                                            while (b != -1) {
                                                out.write(b);
                                                b = is.read();
                                            }
                                            //when the input stream is closed by the client,
                                            //a new 201 response message is created
                                            msg = new Response(201);
                                            //write the message to the output stream
                                            msg.write(os);
                                        } catch (Exception e) {
                                            System.out.println(e);
                                        }
                                    }
                                } else {
                                    //if URI does not start with "/", create 400 response message
                                    msg = new Response(400);
                                    //write the message to the output stream
                                    msg.write(os);
                                }
                            }
                        } else {
                            //if request is not GET, HEAD or PUT, the method is not implemented,
                            //create a new 501 response message
                            msg = new Response(501);
                            //write the message to the output stream
                            msg.write(os);
                        }
                    } else {
                        //if HTTP version is not 1.1
                        //create a new 505 response message
                        msg = new Response(505);
                        //write the message to the output stream
                        msg.write(os);
                    }
                } catch (MessageFormatException | IOException e) {
                    System.out.println(e);
                    //if an error occurs during the request parsing create 400 response
                    msg = new Response(400);
                    //write the message to the output stream
                    msg.write(os);
                }
                //close input stream
                is.close();
                //close output stream
                os.close();
                //close connection
                client_socket.close();
            } catch (IOException e) {
                System.out.println(e);
                try {
                    OutputStream os = client_socket.getOutputStream();
                    Response msg = new Response(500);
                    msg.write(os);
                } catch (IOException ex) {
                    System.out.println(ex);
                }
            }
        }
    }
};
