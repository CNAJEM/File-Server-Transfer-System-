
import java.io.*;
import java.net.Socket;

public class Client {
    private static final int BUFFER_SIZE = 4096;
    private String serverName;
    private String fileName;
    private boolean isWriteOperation = false;
    private long startByte = -1;
    private long endByte = -1;
    private int port = 7583;

    public Client(String[] args) {
        if (!parseArguments(args)) {
            printUsage();
            return;
        }
        execute();
    }

    private boolean parseArguments(String[] args) {
        if (args.length < 2) {
            return false; // We expect at least server name and file name
        }

        int i = 0;

        // Loop to process flags, handle cases where -w comes before or after file name
        while (i < args.length) {
            //String arg = args[i];
            String arg = args[i].toLowerCase();  // Convert to lowercase for case-insensitive comparison

            switch (arg) {
                case "-w":
                    isWriteOperation = true; // Set the write flag
                    i++;
                    break;
                case "-s":
                    i++;
                    if (i < args.length) {
                        //startByte = Integer.parseInt(args[i]);
                        startByte = Long.parseLong(args[i]);
                        i++;
                    } else {
                        System.out.println("Error: Missing value for -s");
                        return false;
                    }
                    break;
                case "-e":
                    i++;
                    if (i < args.length) {
                        //endByte = Integer.parseInt(args[i]);
                        endByte = Long.parseLong(args[i]);
                        i++;
                    } else {
                        System.out.println("Error: Missing value for -e");
                        return false;
                    }
                    break;
                case "-p":
                    i++;
                    if (i < args.length) {
                        port = Integer.parseInt(args[i]);
                        i++;
                    } else {
                        System.out.println("Error: Missing value for -p");
                        return false;
                    }
                    break;
                default:
                    // Assume any non-flag argument is either server name or file name
                    if (serverName == null) {
                        serverName = arg; // First non-flag argument is server name
                    } else if (fileName == null) {
                        fileName = arg; // Second non-flag argument is file name
                    } else {
                        System.out.println("Error: Unknown argument " + arg);
                        return false; // Too many non-flag arguments
                    }
                    i++;
                    break;
            }
        }

        // Ensure both serverName and fileName are provided
        if (serverName == null || fileName == null) {
            System.out.println("Error: Missing server name or file name");
            return false;
        }

        return true;
    }

    private void execute() {
        try (Socket connection = new Socket(serverName, port);
             DataInputStream socketIn = new DataInputStream(connection.getInputStream());
             DataOutputStream socketOut = new DataOutputStream(connection.getOutputStream())) {

            // Build and send request
            StringBuilder request = new StringBuilder();
            request.append(isWriteOperation ? "WRITE " : "READ ").append(fileName);

            // Handle the case where only -e is provided
            if (startByte == -1 && endByte != -1) {
                startByte = 0;  // Default to 0 if only -e is provided
            }

            if (startByte != -1) {
                request.append(" -s").append(startByte);
            }
            if (endByte != -1) {
                request.append(" -e").append(endByte);
            }

            // Send the request to the server
            socketOut.writeUTF(request.toString());

            // Read server response
            String response = socketIn.readUTF();
            if (response.startsWith("ERROR")) {
                System.out.println(response);  // Print error message from the server
                return;
            }

            // If the server accepts the write operation
            if (isWriteOperation) {
                uploadFile(socketOut);
            } else {
                downloadFile(socketIn);
            }

        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
        }
    }

    private void uploadFile(DataOutputStream socketOut) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("Error: File does not exist.");
            return;
        }

        try (FileInputStream fileIn = new FileInputStream(file)) {
            long fileSize = file.length();
            socketOut.writeLong(fileSize);  // First, send the file size

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) > 0) {
                socketOut.write(buffer, 0, bytesRead);  // Send the file contents
            }
            System.out.println("File uploaded successfully.");
        }
    }


    private void downloadFile(DataInputStream socketIn) throws IOException {
        long fileSize = socketIn.readLong();
        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;
            while (totalBytesRead < fileSize && (bytesRead = socketIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            System.out.println("File downloaded successfully.");
        }
    }

    private void printUsage() {
        System.out.println("Usage: Client <server-name> [-w] <file-name> [-s START_BYTE -e END_BYTE] [-p PORT]");
        System.out.println("Example:");
        System.out.println("  client localhost example.txt");
        System.out.println("  client localhost -w example.txt");
        System.out.println("  client localhost example.txt -s 10 -e 100");
    }

    public static void main(String[] args) {
        new Client(args);
    }
}