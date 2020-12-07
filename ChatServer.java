import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class ChatServer {
  private static final int portNumber = 4444; // args[0]
  private int serverPort;
  private List<ServerThread> clients;
  private Map<String,Boolean> unames;
  private List<String> rooms;

  public static void main(String[] args) {
    ChatServer server = new ChatServer();
    server.start();
  }

  public List<ServerThread> getClients() {
    return clients;
  }

  public boolean lookupUname(String uname) {
    return unames.containsKey(uname);
  }

  public boolean getValue(String uname) {
    return unames.get(uname);
  }

  public void addUname(String uname) {
    unames.put(uname,true);
  }

  public void removeUname(String uname) {
    unames.put(uname,false);
  }

  public boolean lookupRoom(String room) {
    return rooms.contains(room);
  }

  public void addRoom(String room) {
    rooms.add(room);
  }

  private void start() {
    serverPort = portNumber;
    clients = new ArrayList<ServerThread>();
    unames = new HashMap<>();
    rooms = new ArrayList<String>();
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
        ServerThread client = new ServerThread(this, socket);
        Thread thread = new Thread(client);
        thread.start();
        clients.add(client);
      } catch (IOException e) {
        System.out.println("Could not accept client on: "+serverPort);
      }
    }
  }
}

class ServerThread extends ChatServer implements Runnable {   // This will handle the client
  private Socket socket;
  private PrintWriter out;    // Write data to client
  private ChatServer server;
  private String uName = "guest";   // Name of associated client
  private String room = "outside";  // Room of associated client (outside -> Same as being null)
  private String state = "init";    // Current state of client

  public ServerThread(ChatServer server, Socket socket) {
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

  public String getRoom() {
    return room;
  }

  public String getNick() {
    return uName;
  }

  public void handleRequest(String input) throws IOException {
    if (input.contains("/priv")) {
      handlePriv(input);
    } else if (input.contains("/leave")) {
      if (!room.equals("outside")) {
        out.println("OK");
        out.flush();
        relayMessage("LEFT "+uName);
        room = "outside";
      }
    } else if (input.contains("/join")) {
      handleJoin(input);
    } else if (input.contains("/nick")) {
      handleNick(input);
    } else if (input.equals("/bye")) {
      out.println("BYE");
      out.flush();
      if (!room.equals("outside")) {
       relayMessage("LEFT "+uName); 
      }
      Thread.currentThread().interrupt();
    } else {
      sendMsg(input);
    } 
  }

  public void sendMsg(String input) {
    String clientRoom = room;
    String clientUname = uName;
    for (ServerThread client : server.getClients()) {
      PrintWriter clientOut = client.getWriter();
      String clientOutRoom = client.getRoom();
      if (clientOut != null && clientRoom.equals(clientOutRoom)) {
        clientOut.println(clientUname+": "+input);
        clientOut.flush();
      }
    }
  }

  public void handlePriv(String input) {    // Done
    String input_name = input.substring(6,10).trim();
    String input_msg = input.substring(10).trim();
    String sender_name = getNick();
    boolean flag = false;
    for (ServerThread client : server.getClients()) {
      PrintWriter clientOut = client.getWriter();
      String clientOutNick = client.getNick();
      if (clientOutNick.equals(input_name)) {
        clientOut.println("PRIVATE "+sender_name+" "+input_msg);
        clientOut.flush();
        out.println("OK");
        out.flush();
        flag = true;
      }
    }
    if (!flag) {
      out.println("ERROR");
      out.flush();
    }
  }

  public void handleJoin(String input) {  // Done
    String input_room = input.substring(6);
    state = "inside";
    out.println("OK");
    out.flush();
    if (!server.lookupRoom(input_room)) {
      server.addRoom(input_room);
    }
    if (!room.equals("outside")) {
      relayMessage("LEFT "+uName);
    }
    room = input_room;
    relayMessage("JOINED "+uName);
  }

  public void handleNick(String input) {  // Done
    String cand_name = input.substring(6);
    if (state.equals("init")) {
      if (!server.lookupUname(cand_name)) {
        uName = cand_name;
        server.addUname(cand_name);
        state = "outside";
        out.println("OK");
        out.flush();
      } else if (!server.getValue(cand_name)) {
        server.removeUname(uName);
        server.addUname(cand_name);
        uName = cand_name;
        state = "outside";
        out.println("OK");
        out.flush();
      } else {
        out.println("ERROR");
        out.flush();
      }
    } else if (state.equals("outside")) {
      if (!server.lookupUname(cand_name)) {
        server.removeUname(uName);
        uName = cand_name;
        server.addUname(cand_name);
        out.println("OK");
        out.flush();
      } else if (!server.getValue(cand_name)) {
        server.removeUname(uName);
        server.addUname(cand_name);
        uName = cand_name;
        out.println("OK");
        out.flush();
      } else {
        out.println("ERROR");
        out.flush();
      }
    } else if (!room.equals("outside")) { // State.equals("inside")
      if (!server.lookupUname(cand_name)) {
        String oldName = uName;
        server.removeUname(uName);
        server.addUname(cand_name);
        uName = cand_name;
        out.println("OK");
        out.flush();
        relayMessage(oldName+" changed name to "+uName); //relayMessage("NEWNICK "+oldName+" "+uName);
      } else if (!server.getValue(cand_name)) {
        String oldName = uName;
        server.removeUname(uName);
        server.addUname(cand_name);
        uName = cand_name;
        out.println("OK");
        out.flush();
        relayMessage(oldName+" changed name to "+uName); //relayMessage("NEWNICK "+oldName+" "+uName);
      } else {
        out.println("ERROR");
        out.flush();
      }
    }
  }

  public void relayMessage(String input) {
    String clientRoom = room;
    String clientUname = uName;
    for (ServerThread client : server.getClients()) {
      PrintWriter clientOut = client.getWriter();
      String clientOutRoom = client.getRoom();
      String clientOutNick = client.getNick();
      if (clientOut != null && clientRoom.equals(clientOutRoom) && !clientUname.equals(clientOutNick)) {
        clientOut.println(input);
        clientOut.flush();
      }
    }
  }
}
