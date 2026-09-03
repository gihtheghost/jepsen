package client;

import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Client {

    private String host;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    public static final String ERRO = "ERRO: ";
    public static final String OK = "OK: ";

    //inicializa com ip e porta do servidor
    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    //cria o sockcet
    public void connect() throws IOException {
        this.socket = new Socket(this.host, this.port);
        System.out.println("Socket conectado!");
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public String put(String key, String value){
        try {
            out.println("p" + " " + key + " " + value);
            String response = in.readLine();
            return response;
        } catch (IOException e) {
            return ERRO + e.getMessage();
        }
    }

    public String get(String key){
        try {
            out.println("g" + " " + key + "\n");
            String response = in.readLine();
            System.out.println("Resposta do servidor:");
            return response;
        } catch (IOException e) {
            return ERRO + e.getMessage();
        }
    }

    public String remove(String key){
        try {
            out.println("r" + " " + key);
            String response = in.readLine();
            return response;
        } catch (IOException e) {
            return ERRO + e.getMessage();
        }
    }

    public String clear(){
        try {
            out.println("c");
            String response = in.readLine();
            return response;
        } catch (IOException e) {
            return ERRO + e.getMessage();
        }
    }

    public String checkpoint(){
        try {
            out.println("k");
            String response = in.readLine();
            return response;
        } catch (IOException e) {
            return ERRO + e.getMessage();
        }
    }

    public String list(){
        try {
            out.println("l");
            String response = in.readLine();
            return response;
        } catch (IOException e) {
            return ERRO + e.getMessage();
        }
    }

    public void quit() {
        out.println("q");
        try {
            this.socket.close();
        } catch (IOException e) {
            return;
        }
    }
    
    public static void main(String[] args) throws IOException {

        String host = args[0];
        Client c = new Client(host, 6666);
        c.connect();
        BufferedReader terminal =
            new BufferedReader(new InputStreamReader(System.in));
        System.out.println("ReplicatedMap Test Application");
        System.out.println("Put: p <key> <value>     Get: g <key>     Remove: r <key>");
        System.out.println("List: l                  Clear: c         Checkpoint: k");
        System.out.println("Quit: q");
        while (true) {
            System.out.print("> ");
            String command = terminal.readLine();
            if (command == null || command.length() == 0) {
                continue;
            }
            if ("q".equals(command)) {
                c.quit();
                System.exit(0);
            } else if ('g' == command.charAt(0) && command.length() > 2) {
                String key = command.split(" ")[1];
                System.out.println(c.get(key));
            } else if ('p' == command.charAt(0) && command.length() > 2) {
                String key = command.split(" ")[1];
                String value = command.split(" ", 3)[2];
                System.out.println(c.put(key, value));
            } else if ('r' == command.charAt(0) && command.length() > 2) {
                String key = command.split(" ")[1];
                System.out.println(c.remove(key));
            } else if ("c".equals(command)) {
                System.out.println(c.clear());
            } else if ("k".equals(command)) {
                System.out.println(c.checkpoint());
            } else if ("l".equals(command)) {
                String lista = c.list();
                System.out.println(lista);
            }
        }
    

    }

}
