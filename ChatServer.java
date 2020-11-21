import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.List;

public class ChatServer {
  private static final int portNumber = 4444; // args[0]
  private int serverPort;
  private List<ClientThread> clients;
  private List<String> unames;

  public static void main(String[] args) {
    ChatServer server = new ChatServer();
    server.start();
  }

  public List<ClientThread> getClients() {
    return clients;
  }

  public boolean lookupUname(String uname) {
    return unames.contains(uname);
  }

  public void addUname(String uname) {
    unames.add(uname);
  }

  private void start() {
    serverPort = portNumber;
    clients = new ArrayList<ClientThread>();
    unames = new ArrayList<String>();
    ServerSocket serverSocket = null;
    try {
      serverSocket = new ServerSocket(serverPort);
      acceptClients(serverSocket);
    } catch (IOException e) {
      System.err.println("Could not list on: "+serverPort);
      System.exit(1);
    }
  }

  private void acceptClients(ServerSocket serverSocket) {
    System.out.println("server started on: " +serverSocket.getLocalSocketAddress());
    while(true) {
      try {
        Socket socket = serverSocket.accept();
        System.out.println("accepted: "+socket.getRemoteSocketAddress());
        ClientThread client = new ClientThread(this, socket);
        Thread thread = new Thread(client);
        thread.start();
        clients.add(client);
      } catch (IOException e) {
        System.out.println("Could not accept client on: "+serverPort);
      }
    }
  }
}

class ClientThread extends ChatServer implements Runnable {   // This will handle the client
  private Socket socket;
  private PrintWriter out;    // Write data to client
  private ChatServer server;
  private String uName = "guest";

  public ClientThread(ChatServer server, Socket socket) {
    this.server = server;
    this.socket = socket;
  }

  @Override
  public void run() {
    try {
      this.out = new PrintWriter(socket.getOutputStream(), false);
      Scanner in = new Scanner(socket.getInputStream());

      while (!socket.isClosed()) {
        if (in.hasNextLine()) {
          String input = in.nextLine();
          handleRequest(input);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public PrintWriter getWriter() {
    return out;
  }

  public void handleRequest(String input) throws IOException {
    if (input.contains("/nick")) {
      handleNick(input);
    } else if (input.equals("/bye")) {
      out.println("BYE");
      out.flush();
      Thread.currentThread().interrupt();
    } else {
      relayMessage(input);
    } 
  }

  public void handleNick(String input) {
    String cand_name = input.substring(6);
      if (!server.lookupUname(cand_name)) {
        String oldName = uName;
        uName = cand_name;
        server.addUname(cand_name);
        out.println("/nick "+cand_name);    // Note -> *1
        out.flush();
        relayMessage("NEWNICK "+oldName+" "+uName);
      } else {
        out.println("ERROR");
        out.flush();
      }
  }

  public void relayMessage(String input) {
    for (ClientThread client : server.getClients()) {
      PrintWriter clientOut = client.getWriter();
      if (clientOut != null) {
        clientOut.println(input);
        clientOut.flush();
      }
    }
  }
}

/*
  Instead of the "server" (ClientThread) sending a confirmation OK, it
  sends back the nickname so the actual client can update its nickname.
  If we were to send a OK confirmation, ambiguity would arise due to the 
  message command also requesting OK confirmation or ERROR. -WA
*/
