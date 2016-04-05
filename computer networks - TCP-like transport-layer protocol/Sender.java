import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

public class Sender{
	private int remote_port;
	private int ack_port_num;
	private String remote_IP;
	private InetAddress addr;
	private long timeout=200000000;                          //initial time out value
	private static final int snum=32;                        //Sequence number, range 0-31;
	private int winsize=1;                                   //no larger than 16, default value 1
	private long rtt=100000000;
	private String filename;
	private String log_filename;
	private static final int pktlength=512;                  //pkt total length
	private static final int headerlength=20;
	private long[] pktsend=new long[snum];                   //nanoseconds used to compute RTT
	private Send sd;
	private ReceiveACK rcvack;
	private Timer[] tm=new Timer[snum];
	private int state[]=new int[snum];                       //1 indicates in-flight, 2 indicates received
	private int current=0;                                   //position of sender window
	private int next=0;
	private byte[] msg=new byte[pktlength];
	private byte[][] buffer=new byte[snum][];
	private byte[] fin=new byte[headerlength];               //FIN message
	private Socket socket;                                   //TCP socket to receive ACK
	private ServerSocket ss;
	private DatagramSocket ds;
	private DatagramPacket pkt;
	private boolean start=false;                             //indicates if sender is terminated
	private boolean end=false;
	private boolean runtimer=false;                          //used to control timers
	private int retran=0;
	private int pktsent=0;
	private int totalsent=0;
	private boolean stdout;
	private File logfile;
	private OutputStreamWriter logoutput;
	private SimpleDateFormat df=new SimpleDateFormat("HH:mm:ss:SSS");     //Timestamp log
	
	///////initialization////////
	public Sender() throws IOException{
		Scanner scan=new Scanner(System.in);
		String token=scan.nextLine();
		scan=new Scanner(token);
		if(scan.hasNext()&&scan.next().equalsIgnoreCase("sender")){
			if(scan.hasNext()){
				filename=scan.next();
				if(scan.hasNext()){
					remote_IP=scan.next();
					addr=InetAddress.getByName(remote_IP);
					if(scan.hasNext()){
						remote_port=Integer.parseInt(scan.next());
						if(scan.hasNext()){
							ack_port_num=Integer.parseInt(scan.next());
							if(scan.hasNext()){
								log_filename=scan.next();
								stdout=log_filename.equalsIgnoreCase("stdout");
								if(scan.hasNext()){
									winsize=min(Integer.parseInt(scan.next()),snum/2);
								}
								start=true;
							}
						}
					}
				}
			}
		}
		if(start){
			ss=new ServerSocket(ack_port_num);
			ds=new DatagramSocket(ack_port_num);
			sd=new Send();
			rcvack=new ReceiveACK();
			if(!stdout){
				logfile=new File(log_filename);
				if(!logfile.exists())
					logfile.createNewFile();
				logoutput=new OutputStreamWriter(new FileOutputStream(logfile));
			}
			sd.start();
		}
		else
			System.out.println("The sender fail to start");
	}
	
	private BufferedReader getReader(Socket socket)throws IOException{
		InputStream socketIn=socket.getInputStream();
		return new BufferedReader(new InputStreamReader(socketIn));
	}
	
	//////converting integer to byte array
	public static byte[] intToByte(int i){
		byte[] result=new byte[4];
		result[0]=(byte)((i>>24)&0xFF);
		result[1]=(byte)((i>>16)&0xFF);
		result[2]=(byte)((i>>8)&0xFF);
		result[3]=(byte)(i&0xFF);
		return result;
	}
	
	public static byte[] intToByte2(int i){
		byte[] result=new byte[2];
		result[0]=(byte)((i>>8)&0xFF);
		result[1]=(byte)(i&0xFF);
		return result;
	}
	
	/////converting byte array to integer
	public static int byteToInt2(byte[] b){
		int value=0;
		for(int i=0;i<2;i++){
			int move=(1-i)*8;
			value+=(b[i]&0x000000FF)<<move;
		}
		return value;
	}
	
	/////computing checksum of pkt
	public static byte[] computeChecksum(byte[] b){
		byte[] temp=new byte[2];
		int tempvalue;
		int value=0;
		for(int i=headerlength/2;i<b.length/2;i++){
			temp[0]=b[2*i];
			temp[1]=b[2*i+1];
			tempvalue=byteToInt2(temp);
			value+=tempvalue;
		}
		byte[] checksum=intToByte2(value);
		return checksum;
	}
	
	public static void addChecksum(byte[] b){
		byte[] checksum=computeChecksum(b);
		b[16]=checksum[0];
		b[17]=checksum[1];
	}
	
	public static int min(int a, int b){
		if(a<b)
			return a;
		else
			return b;
	}
	
	public static int max(int a, int b){
		if(a<b)
			return b;
		else
			return a;
	}
	
	///////Timer of each pkt sent, just like Selective Repeat, deal with retransmission////////
	private class Timer extends Thread{
		private int seqnum;
		public Timer(int num){
			seqnum=num;
		}
		public void run(){
			DatagramPacket rpkt;
			while(runtimer&&state[seqnum]==1){
				try{
					Thread.sleep(max((int)(timeout/1000000),1));
				}catch(InterruptedException e){
					e.printStackTrace();
				}
				if(runtimer&&state[seqnum]==1){
					try{
						retran++;
						rpkt=new DatagramPacket(buffer[seqnum],0,buffer[seqnum].length,addr,remote_port);   ////retransmit!!!!
						ds.send(rpkt);
					}catch(IOException e){
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	///////receiving ACK through TCP socket////////
	private class ReceiveACK extends Thread{
		public void run(){
			String ack;
			String log;
			try{
				BufferedReader br=getReader(socket);
				if(!(ack=br.readLine()).equals("exit")){
					//**********receive first ACK*******
					int acknum=Integer.parseInt(ack);          //ACK number
					state[acknum]=2;
					rtt=System.nanoTime()-pktsend[acknum];
					timeout=2*rtt;                             //initialize RTT and adjust timeout
					long samplertt;
					while(true){
						log=df.format(new Date())+", source = "+remote_IP+" "+remote_port+", dest = "+ack_port_num+", ACK # = "+ acknum+", FIN = 0, EstimatedRTT = "+rtt+"\n";
						if(stdout)
							System.out.print(log);             //log ACK received
						else{
							logoutput.write(log);
							logoutput.flush();
						}
						if(acknum==current){
							while(state[current]==2){
								state[current]=0;
								current=(current+1)%snum;
							}
						}
						//receive ACK socket
						if((ack=br.readLine()).equals("exit")) //TCP FIN ACK from receiver
							break;
						acknum=Integer.parseInt(ack);          //ACK sequence number
						state[acknum]=2;                       //indicates received
						samplertt=System.nanoTime()-pktsend[acknum];          //sample RTT
						if(samplertt-10*rtt<0){
							rtt=rtt*7/8+samplertt/8;                          //computing estimated RTT and adjust timeout value for Timers
							timeout=2*rtt;
						}
					}
				}
				start=false;
				ack=br.readLine();                             //receiving TCP FIN msg from receiver
				fin[12]=1;
				pkt=new DatagramPacket(fin,headerlength,addr,remote_port);
				ds.send(pkt);
				while(!end){
					try{
						Thread.sleep(max((int)(timeout/1000000),1));
					}catch(InterruptedException e){
						e.printStackTrace();
					}
					if(!end){
						ds.send(pkt);
					}
				}
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	////////read data from file and send pkts, initialize retransmission timers///////
	private class Send extends Thread{
		public void run(){
			try{
				socket=ss.accept();
				BufferedInputStream input=new BufferedInputStream(new FileInputStream(filename));  //input file
				int datalength;
				rcvack.start();
				msg[13]=0;                                        //FIN
				msg[12]=0;          //FIN2
				byte[] win=intToByte2(winsize);
				msg[14]=win[0];
				msg[15]=win[1];
				byte[] sourceport=intToByte2(ack_port_num);
				msg[0]=sourceport[0];
				msg[1]=sourceport[1];
				byte[] destport=intToByte2(remote_port);
				msg[2]=destport[0];
				msg[3]=destport[1];
				byte[] length=intToByte2(pktlength);
				msg[18]=length[0];
				msg[19]=length[1];
				if((datalength=input.read(msg,headerlength,pktlength-headerlength))!=-1){           //send
					runtimer=true;
					pktsent++;
					totalsent+=datalength;
					byte[] seqnum=intToByte(next);                        //seq# in header
					for(int i=0;i<4;i++)
						msg[i+4]=seqnum[i];
					if(datalength<pktlength-headerlength){
						byte[] newmsg=new byte[headerlength+datalength];
						for(int i=0;i<headerlength+datalength;i++)
							newmsg[i]=msg[i];
						byte[] newlength=intToByte2(headerlength+datalength);
						newmsg[18]=newlength[0];
						newmsg[19]=newlength[1];
						addChecksum(newmsg);                              //checksum
						buffer[next]=new byte[headerlength+datalength];
						for(int i=0;i<headerlength+datalength;i++)
							buffer[next][i]=newmsg[i];
						pkt=new DatagramPacket(newmsg,0,newmsg.length,addr,remote_port);
					}
					else{
						addChecksum(msg);                                 //checksum
						buffer[next]=new byte[pktlength];
						for(int i=0;i<pktlength;i++)
							buffer[next][i]=msg[i];
						pkt=new DatagramPacket(msg,0,msg.length,addr,remote_port);
					}
					ds.send(pkt);
					pktsend[next]=System.nanoTime();
					state[next]=1;                                       //indicates in-flight
					tm[next]=new Timer(next);                            //start timer for this pkt
					tm[next].start();
					String log=df.format(new Date())+", source = "+ack_port_num+", dest = "+remote_IP+" "+remote_port+", Sequence # = "+ next+", FIN = 0\n";
					if(stdout)
						System.out.print(log);                           //log pkt sent
					else{
						logoutput.write(log);
						logoutput.flush();
					}
					next++;
					while(true){
						System.out.print("");
						if((next>=current&&next-current<winsize)||(next<current&&next+snum-current<winsize)){
							if((datalength=input.read(msg,headerlength,pktlength-headerlength))!=-1){   //send
								pktsent++;
								totalsent+=datalength;
								seqnum=intToByte(next);                  //seq# in header
								for(int i=0;i<4;i++)
									msg[i+4]=seqnum[i];
								if(datalength<pktlength-headerlength){
									byte[] newmsg=new byte[headerlength+datalength];
									for(int i=0;i<headerlength+datalength;i++)
										newmsg[i]=msg[i];
									byte[] newlength=intToByte2(headerlength+datalength);
									newmsg[18]=newlength[0];
									newmsg[19]=newlength[1];
									addChecksum(newmsg);                              //checksum
									buffer[next]=new byte[headerlength+datalength];
									for(int i=0;i<headerlength+datalength;i++)
										buffer[next][i]=newmsg[i];
									pkt=new DatagramPacket(newmsg,0,newmsg.length,addr,remote_port);
								}
								else{
									addChecksum(msg);                                 //checksum
									buffer[next]=new byte[pktlength];
									for(int i=0;i<pktlength;i++)
										buffer[next][i]=msg[i];
									pkt=new DatagramPacket(msg,0,msg.length,addr,remote_port);
								}
								ds.send(pkt);
								pktsend[next]=System.nanoTime();
								state[next]=1;                                        //in-flight
								tm[next]=new Timer(next);                             //start timer
								tm[next].start();
								log=df.format(new Date())+", source = "+ack_port_num+", dest = "+remote_IP+" "+remote_port+", Sequence # = "+ next+", FIN = 0\n";
								if(stdout)
									System.out.print(log);                            //log pkt sent
								else{
									logoutput.write(log);
									logoutput.flush();
								}
								next=(next+1)%snum;
							}
							else
								break;
						}
					}
					try{
						Thread.sleep(timeout/200000);
					}catch(InterruptedException e){
						e.printStackTrace();
					}
					runtimer=false;
				}
				for(int i=0;i<headerlength;i++)
					fin[i]=msg[i];
				fin[13]=1;                                               //FIN request
				pkt=new DatagramPacket(fin,headerlength,addr,remote_port);
				ds.send(pkt);
				while(start){
					try{
						Thread.sleep(max((int)(timeout/1000000),1));
					}catch(InterruptedException e){
						e.printStackTrace();
					}
					if(start){
						ds.send(pkt);
					}
				}
				try{
					Thread.sleep(1000+timeout/250000);
				}catch(InterruptedException e){
					e.printStackTrace();
				}
				end=true;
				try{
					Thread.sleep(1000);
				}catch(InterruptedException e){
					e.printStackTrace();
				}
			}catch(IOException e){
				e.printStackTrace();
			}
			System.out.println("Delivery completed successfully");
			System.out.println("Total bytes sent = "+totalsent);
			System.out.println("Segments sent = "+pktsent);
			System.out.println("Segments retransmitted = "+retran);
		}
	}
	
	public static void main(String[] args){
		try{
			Sender sdr=new Sender();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
}
