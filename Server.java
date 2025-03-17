
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static final int BUFFER_SIZE = 4096;
    private int port = 7583;
    private boolean debug = false;

    public Server(String[] args) {
        if (!parseArguments(args)) {
            printUsage();
            return;
        }
        runServer();
    }

    private boolean parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            //String arg = args[i];
            String arg = args[i].toLowerCase();  // Convert to lowercase for case-insensitive comparison

            if (arg.equals("debug=1")) {
                debug = true;
            } else if (arg.equals("-p")) {
                // Ensure the port number is provided in the next argument
                if (i + 1 < args.length) {
                    try {
                        port = Integer.parseInt(args[++i]); // Parse the next argument as the port number
                    } catch (NumberFormatException e) {
                        System.out.println("Error: Invalid port number");
                        return false;
                    }
                } else {
                    System.out.println("Error: Missing port number after -p");
                    return false;
                }
            } else {
                System.out.println("Error: Unknown argument " + arg);
                return false;
            }
        }
        return true;
    }


    private void runServer() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            if (debug) {
                System.out.println("Server started on port " + port);
            }
            while (true) {
                try (Socket connection = serverSocket.accept();
                     DataInputStream socketIn = new DataInputStream(connection.getInputStream());
                     DataOutputStream socketOut = new DataOutputStream(connection.getOutputStream())) {

                    String clientIP = connection.getInetAddress().getHostAddress();
                    String request = socketIn.readUTF();
                    String[] tokens = request.split(" ");
                    String command = tokens[0];
                    String fileName = tokens[1];

                    if (command.equals("READ")) {
                        handleReadRequest(socketIn, socketOut, clientIP, fileName, tokens);
                    } else if (command.equals("WRITE")) {
                        handleWriteRequest(socketIn, socketOut, clientIP, fileName);
                    } else {
                        socketOut.writeUTF("ERROR: Invalid command");
                    }
                } catch (Exception e) {
                    System.out.println("Error handling client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }


    private void handleReadRequest(DataInputStream socketIn, DataOutputStream socketOut,
                                   String clientIP, String fileName, String[] tokens) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            socketOut.writeUTF("ERROR: File not found");
            return;
        }

        long fileSize = file.length();
        long startByte = 0;  // Default to 0 (start of the file)
        long endByte = fileSize - 1;  // Default to the end of the file

        // Check if -s or -e are provided
        if (tokens.length >= 3) {
            try {
                if (tokens[2].startsWith("-s")) {
                    startByte = Long.parseLong(tokens[2].substring(2));
                }
                if (tokens.length >= 4 && tokens[3].startsWith("-e")) {
                    endByte = Long.parseLong(tokens[3].substring(2));
                }
            } catch (NumberFormatException e) {
                socketOut.writeUTF("ERROR: Invalid byte range arguments");
                return;
            }
        }

        // Validate the byte range
        if (startByte < 0 || endByte >= fileSize || startByte > endByte) {
            socketOut.writeUTF("ERROR: Invalid byte range");
            return;
        }

        socketOut.writeUTF("OK");

        if (debug) {
            System.out.println("Sending " + fileName + " to " + clientIP + " from byte " + startByte + " to " + endByte);
        }

        try (FileInputStream fileIn = new FileInputStream(file)) {
            // Skip to the startByte
            fileIn.skip(startByte);
            long bytesToSend = endByte - startByte + 1;

            socketOut.writeLong(bytesToSend);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesSent = 0;

            // Track the last percentage reported
            int lastReportedPercentage = 0;

            while (totalBytesSent < bytesToSend && (bytesRead = fileIn.read(buffer, 0, (int) Math.min(BUFFER_SIZE, bytesToSend - totalBytesSent))) != -1) {
                socketOut.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;

                // Calculate the current percentage of the file sent
                int currentPercentage = (int) ((totalBytesSent * 100) / bytesToSend);

                // Check if we have crossed a 10% boundary
                if (currentPercentage >= lastReportedPercentage + 10) {
                    // Print progress for each 10% step passed
                    while (lastReportedPercentage + 10 <= currentPercentage) {
                        lastReportedPercentage += 10;
                        System.out.println("Sent " + lastReportedPercentage + "% of " + fileName);
                    }
                }
            }
        }

        if (debug) {
            System.out.println("Finished sending " + fileName + " to " + clientIP);
        }
    }



    private void handleWriteRequest(DataInputStream socketIn, DataOutputStream socketOut,
                                    String clientIP, String fileName) throws IOException {
        File file = new File(fileName);

        // Check if the file already exists on the server
        if (file.exists()) {
            socketOut.writeUTF("ERROR: File already exists");
            return;
        }

        // Let the client know that the server is ready to receive the file
        socketOut.writeUTF("OK");

        // Read the file size from the client
        long fileSize = socketIn.readLong();

        if (debug) {
            System.out.println("Receiving " + fileName + " from " + clientIP);
        }

        // Begin receiving the file data from the client
        try (FileOutputStream fileOut = new FileOutputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesReceived = 0;

            while (totalBytesReceived < fileSize && (bytesRead = socketIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytesReceived += bytesRead;
            }
        }

        if (debug) {
            System.out.println("Finished receiving " + fileName + " from " + clientIP);
        }
    }

    private void printUsage() {
        System.out.println("Usage: Server [DEBUG=1] [-p PORT]");
        System.out.println("Example:");
        System.out.println("  server");
        System.out.println("  server DEBUG=1");
        System.out.println("  server -p 6000");
    }

    public static void main(String[] args) {
        new Server(args);
    }
}

