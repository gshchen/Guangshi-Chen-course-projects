import java.net.*;
import java.io.*;
import java.util.*;

public class Client{
	public String IP;                 //server IP address
	public int port;                //server port
	public Receiver rc;
	private Socket socket;
	private SocketAddress addr;                         //server IP address
	private Sender sd;                            //sending messages
	public static final int waiting=60000;         //time waiting to connect to server
	
	public Client(String IP,int port)throws IOException{
		this.IP=IP;
		this.port=port;
		rc=new Receiver();
		socket=new Socket();
		addr=new InetSocketAddress(IP,port);
		rc.start();
	}
	
	private PrintWriter getWriter(Socket socket)throws IOException{
		OutputStream socketOut=socket.getOutputStream();
		return new PrintWriter(socketOut,true);
	}
	
	private BufferedReader getReader(Socket socket)throws IOException{
		InputStream socketIn=socket.getInputStream();
		return new BufferedReader(new InputStreamReader(socketIn));
	}
	
	private class Sender extends Thread{                //thread send messages to server or other users
		public void run(){
			try{
				PrintWriter pw=getWriter(socket);
				BufferedReader localReader=new BufferedReader(new InputStreamReader(System.in));
				String msg;
				while(true){
					msg=localReader.readLine(); 
					if(msg.equalsIgnoreCase("logout"))         //when input "logout" the connection terminates
						break;
					pw.println(msg);
				}
				pw.println("logout");                          //tell the server about logout
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	public class Receiver extends Thread{
		public void run(){                                  //thread initialize connection and receive responses from server or messages from other users
			try{
				socket.connect(addr,waiting);
				if(socket.isConnected()){
					BufferedReader br=getReader(socket);
					PrintWriter pw=getWriter(socket);
					BufferedReader localReader=new BufferedReader(new InputStreamReader(System.in));
					String msg,ID;
					System.out.println(br.readLine());                  //response from server
					pw.println(ID=localReader.readLine());                   //input user ID
					System.out.println(msg=br.readLine());                  //response from server
					if(msg.equals("Please input your password:")){           //ID correct, no login, not blocked
						while(true){
							pw.println(localReader.readLine());                          //input password for up to 3 times
							System.out.println(msg=br.readLine());
							if(msg.equals("Connection successful!")||msg.equals(ID+" blocked!"))
								break;
						}
						if(msg.equals("Connection successful!")){           //password correct, communication start
							System.out.println("Welcome to simple chat server!");
							sd=new Sender();                                       //start the sender thread
							sd.start();
							while(true){                                          //receiving messages
								msg=br.readLine();
								if(msg.equals("Server: exit")||msg.equals("timeout")||msg.equals("logout"))       //messages from server to terminate connection
									break;
								System.out.println(msg);
							}
							if(sd.isAlive()){                                        //to stop the sender thread
								sd.interrupt();
							}
						}
					}
					if(msg.equals("Server: exit")){
						pw.println("exit");
						System.out.println("The server has terminated");
					}
					else if(msg.equals("timeout")){                                   //inactive, time out
						pw.println("timeout");
						System.out.println("Login time out!");
					}
					else
						System.out.println("Connection terminated");
					socket.close();
				}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args)throws IOException{
		Scanner scan=new Scanner(System.in);
		System.out.println("Please input the IP address of the server");
		String IP=scan.nextLine();
		System.out.println("Please input the port number of the server");
		int port=Integer.parseInt(scan.nextLine());
		Client cl=new Client(IP,port);
	}

}
