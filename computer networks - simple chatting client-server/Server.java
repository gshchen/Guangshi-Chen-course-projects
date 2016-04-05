import java.io.*;
import java.net.*;
import java.util.*;

public class Server{
	public int port;
	public String filename;
	public Communicator cm;
	private int num;                           //number of users
	private ServerSocket serverSocket;
	private Map<String,User> users;   //All data about users stored here
	private Service sv;               //listen to participants
	private ActiveTimer am;           //counting active time for login users
	public static final int blockt=3;         //chances for wrong password
	public static final long BLOCK_TIME=60000;
	public static final long LAST_HOUR=3600000;
	public static final long TIME_OUT=1800000;
	public static final long LAST_MINUTE=60000;
	
	public Server(int port,String filename) throws IOException{
		this.port=port;
		this.filename=filename;
		cm=new Communicator();
		serverSocket=new ServerSocket(port);
		users=new HashMap<String,User>();
		BufferedReader br;
		String line, ID, pw;
		Scanner sc;
		try{
			File f=new File(filename);                                 //read user IDs and passwords, initialize server
			br=new BufferedReader(new FileReader(f));
			num=0;
			while((line=br.readLine())!=null){
				sc=new Scanner(line);
				ID=sc.next();
				pw=sc.next();
				users.put(ID, new User(pw));
				num++;
			}
		}catch(FileNotFoundException e){
			e.printStackTrace();
		}catch(IOException e){
			e.printStackTrace();
		}
		cm.start();
	}
	
	private class User{                        //user data
		private Socket socket;
		private boolean state;                     //login or logout
		private String password;
		private long last;                        //last time logout
		private long active;                   //last time active
		private Set<InetAddress> blockaddrs;              //blocked IP addresses
		private LinkedList<String> offline;                 //offline messages
		public User(String pw){
			state=false;
			password=pw;
			last=0;
			active=0;
			blockaddrs=new HashSet<InetAddress>();
			offline=new LinkedList<String>();
		}
		public Socket getSocket(){
			return socket;
		}
		public void setSocket(Socket socket){
			this.socket=socket;
		}
		public boolean getState(){
			return state;
		}
		public void setState(boolean state){
			this.state=state; 
		}
		public String getPassword(){
			return password;
		}
		public long getLast(){
			return last;
		}
		public void setLast(long last){
			this.last=last;
		}
		public long getActive(){
			return active;
		}
		public void setActive(long active){
			this.active=active;
		}
		public Set<InetAddress> getBlockAddrs(){
			return blockaddrs;
		}
		public LinkedList<String> getOffline(){
			return offline;
		}
	}
	
	private String whoelse(String thisID){             //the whoelse command
		String s="";
		for(String ID:users.keySet())
			if(users.get(ID).getState()&&!ID.equals(thisID))
				s=s+ID+" ";
		if(s.equals(""))
			s="none";
		return s;
	}
	
	private String wholasthr(String thisID){                         //the wholasthr command
		return wholast(thisID,LAST_HOUR);
	}
	
	private String wholastmin(String thisID){
		return wholast(thisID,LAST_MINUTE);
	}
	
	private String wholast(String thisID,long time){
		long last,current=System.currentTimeMillis();
		String s="";
		for(String ID:users.keySet()){
			if(!ID.equals(thisID)){
				last=users.get(ID).getLast();
				if(users.get(ID).getState()||(current-last<time))
					s=s+ID+" ";
			}
		}
		if(s.equals(""))
			s="none";
		return s;
	}
	
	private void broadcast(String send,String msg){                       //for a user to broadcast to other users
		for(String ID:users.keySet())
			if(!ID.equals(send))
				communicate(send,ID,msg);
	}
	
	private void communicateAll(String msg){                     //for server to broadcast to all users
		for(String ID:users.keySet())
			communicate("Server",ID,msg);
	}
	
	private void communicate(String send, String receive, String msg){    //send a message to a user, if not online, stored in offline message list
		User us=users.get(receive);
		if(us.getState()){
			try{
				PrintWriter pw=getWriter(us.getSocket());
				pw.println(send+": "+msg);
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		else
			us.getOffline().addLast(send+": "+msg);
	}
	
	private PrintWriter getWriter(Socket socket)throws IOException{
		OutputStream socketOut=socket.getOutputStream();
		return new PrintWriter(socketOut,true);
	}
	
	private BufferedReader getReader(Socket socket)throws IOException{
		InputStream socketIn=socket.getInputStream();
		return new BufferedReader(new InputStreamReader(socketIn));
	}
	
	private class ActiveTimer extends Thread{            //counting login users active times
		public void run(){
			long active;
			PrintWriter pw;
			while(true){
				for(String ID:users.keySet()){
					if(users.get(ID).getState()){
						active=users.get(ID).getActive();
						if((System.currentTimeMillis()-active)>TIME_OUT){      //inactive uses will be forced to logout
							try{
								pw=getWriter(users.get(ID).getSocket());
								pw.println("timeout");
							}catch(IOException e){
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
	}
	
	private class Blocker extends Thread{                 //block wrong password users IP addresses
		private String ID;
		private InetAddress addr;
		public Blocker(String ID,InetAddress addr){
			this.ID=ID;
			this.addr=addr;
		}
		public void run(){
			User us=users.get(ID);
			us.getBlockAddrs().add(addr);
			try{
				Thread.sleep(BLOCK_TIME);                    //block for 1 minute
			}catch(InterruptedException e){
				e.printStackTrace();
			}
			us.getBlockAddrs().remove(addr);
		}
	}
	
	private class Service extends Thread{             //listen to participants  
		public void run(){
			Receiver rc;
			Socket socket;
			try{
				while(true){
					socket=serverSocket.accept();           //accept user requests and initialize connections to users
					rc=new Receiver(socket);
					rc.start();
				}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	private class Receiver extends Thread{                         //    one thread initializes connection and communicates with one user
		private Socket socket;
		public Receiver(Socket socket){
			this.socket=socket;
		}
		public void run(){
			try{
				BufferedReader br=getReader(socket);
				PrintWriter pw=getWriter(socket);
				pw.println("Please input your user ID:");
				String ID=br.readLine();
				if(users.containsKey(ID)){                  //check if ID is correct
					User us=users.get(ID);
					if(!us.getState()){                      //prohibit concurrent duplicate users
						InetAddress addr=socket.getInetAddress();
						if(!us.getBlockAddrs().contains(addr)){          //if IP not blocked
							boolean state=false;
							String password;
							pw.println("Please input your password:");
							for(int i=0;i<blockt;i++){                      //3 times for the right password
								password=br.readLine();
								if(us.getPassword().equals(password)){
									state=true;
									break;
								}
								if(i<2)
									pw.println("Wrong password! Please try again:");
							}
							if(state){                                 //password correct, communication starts
								us.setActive(System.currentTimeMillis());         //reset active time
								us.setState(true);                               //login
								us.setSocket(socket);
								System.out.println("New connection accepted: "+ID);
								pw.println("Connection successful!");
								Scanner sc;
								String msg;
								while((msg=us.getOffline().pollFirst())!=null)                //offline messages
									pw.println(msg);
								pw.println("Command:");
								while(true){
									msg=br.readLine();
									us.setActive(System.currentTimeMillis());          //if new message received, reset active time
									if(msg.equals("logout")||msg.equals("exit")||msg.equals("timeout"))
										break;
									else if(msg.equalsIgnoreCase("whoelse"))              //command from the user
										pw.println(whoelse(ID));
									else if(msg.equalsIgnoreCase("wholasthr"))
										pw.println(wholasthr(ID));
									else if(msg.equalsIgnoreCase("wholastmin"))
										pw.println(wholastmin(ID));
									else{
										sc=new Scanner(msg);
										String s=sc.next();
										if(s.equalsIgnoreCase("wholast")){
											if(sc.hasNext()){
												long time=(long)(60000*sc.nextInt());
												pw.println(wholast(ID,time));
											}
										}
										else if(s.equalsIgnoreCase("broadcast")){
											String rest="";
											while(sc.hasNext())
												rest=rest+sc.next()+" ";
											sc=new Scanner(rest);
											String[] IDs=new String[num];
											int i=0;
											while(sc.hasNext()&&i<num){
												if(users.containsKey(s=sc.next())){
													IDs[i]=s;
													i++;
												}
												else
													break;
											}
											while(sc.hasNext())
												s=s+" "+sc.next();
											if(i==0)
												broadcast(ID,s);
											else
												for(int j=0;j<i;j++)
													communicate(ID,IDs[j],s);
										}
										else if(s.equalsIgnoreCase("message")){
											String rcv=sc.next();
											if(users.containsKey(rcv)){
												int len=rcv.length();
												s=msg.substring(len+9);
												communicate(ID,rcv,s);
											}
											else
												pw.println("User ID does not exist!");
										}
										else{
											pw.println("Error: command unable to recognize");
										}
									}
								}
								if(msg.equals("logout")){
									pw.println("logout");
									System.out.println("Connection with "+ID+" terminated");
								}
								else if(msg.equals("timeout"))
									System.out.println("Connection with "+ID+" terminated");
								us.setState(false);                                  //logout
								us.setLast(System.currentTimeMillis());              //reset logout time
								socket.close();
							}
							else{
								pw.println(ID+" blocked!");                      //wrong password, block IP address
								Blocker bl=new Blocker(ID,addr);
								bl.start();
								socket.close();
							}
						}
						else{                                         //IP has been blocked
							pw.println(ID+" blocked!");
							socket.close();
						}
					}
					else{
						pw.println("Error: "+ID+" is connected");           //concurrent duplicate users
						socket.close();
					}
				}
				else{
					pw.println("ID does not exist!");                   //incorrect ID
					socket.close();
				}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	public class Communicator extends Thread{                       //for server to send messages to users
		public void run(){
			try{
				System.out.println("The server has started");
				sv=new Service();
				sv.start();
				am=new ActiveTimer();
				am.start();
				BufferedReader localReader=new BufferedReader(new InputStreamReader(System.in));
				String msg;
				Scanner sc;
				System.out.println("Command:");
				while(true){
					msg=localReader.readLine();
					if(msg.equalsIgnoreCase("exit")){
						break;
					}
					else if(msg.equalsIgnoreCase("who"))              //commands from the server, similar to those of users
						System.out.println(whoelse(""));
					else if(msg.equalsIgnoreCase("wholasthr"))
						System.out.println(wholasthr(""));
					else if(msg.equalsIgnoreCase("wholastmin"))
						System.out.println(wholastmin(""));
					else{
						sc=new Scanner(msg);
						String s=sc.next();
						if(s.equalsIgnoreCase("wholast")){
							if(sc.hasNext()){
								long time=(long)(60000*sc.nextInt());
								System.out.println(wholast("",time));
							}
						}
						else if(s.equalsIgnoreCase("broadcast")){
							String rest="";
							while(sc.hasNext())
								rest=rest+sc.next()+" ";
							sc=new Scanner(rest);
							String[] IDs=new String[num];
							int i=0;
							while(sc.hasNext()&&i<num){
								if(users.containsKey(s=sc.next())){
									IDs[i]=s;
									i++;
								}
								else
									break;
							}
							while(sc.hasNext())
								s=s+" "+sc.next();
							if(i==0)
								communicateAll(s);
							else
								for(int j=0;j<i;j++)
									communicate("Server",IDs[j],s);
						}
						else if(s.equalsIgnoreCase("message")){
							String rcv=sc.next();
							if(users.containsKey(rcv)){
								int len=rcv.length();
								s=msg.substring(len+9);
								communicate("Server",rcv,s);
							}
							else
								System.out.println("User ID does not exist!");
						}
						else{
							System.out.println("Error: command unable to recognize");
						}
					}
				}
				communicateAll("exit");
				sv.interrupt();
				am.interrupt();
				System.out.println("The server has terminated");
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args)throws IOException{
		Scanner scan=new Scanner(System.in);
		System.out.println("Please input the port number of the server");
		int port=Integer.parseInt(scan.nextLine());
		System.out.println("Please input the path of the file containing user IDs and passwords");
		String filename=scan.nextLine();
		Server sv=new Server(port,filename);
	}

}
