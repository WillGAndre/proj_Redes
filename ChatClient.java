import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {
    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    private final String serverName;
    private final int serverPort;
    private PrintWriter serverOut;
    // private String uName = "guest";

    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message + "\r\n");
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocus();
            }
        });
        // --- Fim da inicialização da interface gráfica
        this.serverName = server;
        this.serverPort = port;
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
      if (!(message.contains("/priv") || message.contains("/leave") || message.contains("/join") || message.contains("/nick") 
      || message.contains("/bye")) && message.contains("/")) {
        message = '/'+message;
        // System.out.println("Msg: "+message);
      }
      serverOut.println(message);
      serverOut.flush();
    }

    // Método principal do objecto
    public void run() throws IOException, InterruptedException {
      Scanner in = new Scanner(System.in);
      Socket socket = new Socket(serverName, serverPort);
      this.serverOut = new PrintWriter(socket.getOutputStream(), false);
      Thread.sleep(1000);

      ClientThread clientThread = new ClientThread(this, socket);
      Thread clientAccessThread = new Thread(clientThread);
      clientAccessThread.start();
      in.close();
    }

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException, InterruptedException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}

class ClientThread implements Runnable {
  private Socket socket;
  private ChatClient client;

  public ClientThread(ChatClient client, Socket socket) {
    this.client = client;
    this.socket = socket;
  }

  @Override
  public void run() {
    try {
      InputStream serverInStream = socket.getInputStream();
      Scanner serverIn = new Scanner(serverInStream);

      while (!socket.isClosed()) {
        if (serverInStream.available() > 0) {
          if (serverIn.hasNextLine()) {
            String str = serverIn.nextLine();       // From server
            handleAnswer(str);
          }
        }
      }
      serverIn.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void handleAnswer(String answer) throws IOException {
    if (answer.equals("BYE")) {
      socket.close();
      Thread.currentThread().interrupt();
      System.exit(0);
    } else {
      client.printMessage(answer);
    }
  }
}
