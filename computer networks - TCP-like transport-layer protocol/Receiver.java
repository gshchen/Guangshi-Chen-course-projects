import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

public class Receiver{
	private int listening_port;
	private static final int snum=32;                    //range of sequence number 0-31
	private static final int headerlength=20;
	private static final int pktlength=512;              //total length of one pkt
	private int winsize=1;                               //window size should not be larger than 16, aligned to sender winsize
	private String filename;
	private int sender_port;
	private String log_filename;
	private String sender_IP;
	private InetAddress addr;
	private Receive rcv;
	private int current=0;                               //position of receiver window
	private int next=current+winsize;                    //position of receiver window
	private int[] state=new int[snum];                   //state=2 means received, 1 means this seq# in the receiver window
	private byte[] msg=new byte[pktlength];
	private byte[][] buffer=new byte[snum][];            //store received pkt, waiting to write to file
	private Socket socket;                               //TCP socket for sending ACKs
	private DatagramSocket ds;
	private DatagramPacket pkt;
	private boolean start=false;                         //indicates whether the receiver has terminated
	private boolean stdout;                              //whether keep log file or show in command line
	private File logfile;
	private OutputStreamWriter logoutput;
	private SimpleDateFormat df=new SimpleDateFormat("HH:mm:ss:SSS");      //Timestamp in log file
	
	///////initialzation///////////
	public Receiver() throws IOException{
		Scanner scan=new Scanner(System.in);
		String token=scan.nextLine();
		scan=new Scanner(token);
		if(scan.hasNext()&&scan.next().equalsIgnoreCase("receiver")){
			if(scan.hasNext()){
				filename=scan.next();
				if(scan.hasNext()){
					listening_port=Integer.parseInt(scan.next());
					if(scan.hasNext()){
						sender_IP=scan.next();
						addr=InetAddress.getByName(sender_IP);
						if(scan.hasNext()){
							sender_port=Integer.parseInt(scan.next());
							if(scan.hasNext()){
								log_filename=scan.next();
								stdout=log_filename.equalsIgnoreCase("stdout");
								start=true;
							}
						}
					}
				}
			}
		}
		if(start){
			for(int i=0;i<next;i++)
				state[i]=1;
			socket=new Socket(addr,sender_port);
			ds=new DatagramSocket(listening_port);
			pkt=new DatagramPacket(msg,pktlength);
			rcv=new Receive();
			if(!stdout){
				logfile=new File(log_filename);
				if(!logfile.exists())
					logfile.createNewFile();
				logoutput=new OutputStreamWriter(new FileOutputStream(logfile));
			}
			rcv.start();
		}
		else
			System.out.println("The receiver fail to start");
	}
	
	//converting integer to byte array
	public static byte[] intToByte2(int i){
		byte[] result=new byte[2];
		result[0]=(byte)((i>>8)&0xFF);
		result[1]=(byte)(i&0xFF);
		return result;
	}
	
	//converting byte array to integer
	public static int byteToInt(byte[] b){
		int value=0;
		for(int i=0;i<4;i++){
			int move=(3-i)*8;
			value+=(b[i]&0x000000FF)<<move;
		}
		return value;
	}
	
	public static int byteToInt2(byte[] b){
		int value=0;
		for(int i=0;i<2;i++){
			int move=(1-i)*8;
			value+=(b[i]&0x000000FF)<<move;
		}
		return value;
	}
	
	//computing checksum of pkt
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
	
	public static int min(int a, int b){
		if(a<b)
			return a;
		else
			return b;
	}
	
	private OutputStreamWriter getWriter(Socket socket)throws IOException{
		OutputStream socketOut=socket.getOutputStream();
		return new OutputStreamWriter(socketOut);
	} 
	
	/////////receiving pkt, storing data in buffer and extracting to files, dealing with pkt loss, delay, corruption and order/////////
	private class Receive extends Thread{
		public void run(){
			try{
				OutputStreamWriter osw=getWriter(socket);
				File file=new File(filename);
				if(!file.exists())
					file.createNewFile();
				FileOutputStream output=new FileOutputStream(file);
				byte[] seqnum=new byte[4];
				byte[] win=new byte[2];
				byte[] len=new byte[2];
				byte[] pktcheck=new byte[2];
				byte[] computecheck;
				boolean corrupt=false;
				String log;
				int length;
				int pktnum;
				ds.receive(pkt);
				if(msg[13]!=1){                                             //if it is a FIN message
					win[0]=msg[14];
					win[1]=msg[15];
					winsize=min(byteToInt2(win),snum/2);                    //adjust winsize to that of sender
					while((next>=current&&next-current<winsize)||(next<current&&next+snum-current<winsize)){
						state[next]=1;
						next=(next+1)%snum;
					}
					while(true){
						for(int i=0;i<4;i++)
							seqnum[i]=msg[i+4];
						pktnum=byteToInt(seqnum);                 //   datagram sequence number received
						if(state[pktnum]==1){                     //   if seq# in receiver window, consider for extraction
							pktcheck[0]=msg[16];
							pktcheck[1]=msg[17];
							len[0]=msg[18];
							len[1]=msg[19];
							length=byteToInt2(len);
							if(length<pktlength){
								byte[] newmsg=new byte[length];
								for(int i=0;i<length;i++)
									newmsg[i]=msg[i];
								computecheck=computeChecksum(newmsg);
								if(pktcheck[0]==computecheck[0]&&pktcheck[1]==computecheck[1]){        //if pkt corrupt
									buffer[pktnum]=new byte[length];
									for(int i=0;i<length;i++)
										buffer[pktnum][i]=newmsg[i];
								}
								else
									corrupt=true;
							}
							else{
								computecheck=computeChecksum(msg);
								if(pktcheck[0]==computecheck[0]&&pktcheck[1]==computecheck[1]){        //if corrupt
									buffer[pktnum]=new byte[pktlength];
									for(int i=0;i<pktlength;i++)
										buffer[pktnum][i]=msg[i];
								}
								else
									corrupt=true;
							}
							if(!corrupt){
								log=df.format(new Date())+", source = "+sender_IP+" "+sender_port+", dest = "+listening_port+", Sequence # = "+ pktnum+", FIN = 0\n";
								if(stdout)                                                             //stdout not maintaing log file but output to command line
									System.out.print(log);                                             //show or log received pkt
								else{
									logoutput.write(log);
									logoutput.flush();
								}
								state[pktnum]=2;                                                       //indicates right pkt received
								osw.write(pktnum+"\n");                   //send ACK via TCP
								osw.flush();
								if(pktnum==current){
									while(state[current]==2){
										state[current]=0;
										output.write(buffer[current],headerlength,buffer[current].length-headerlength);       //buffer extract to file
										current=(current+1)%snum;
									}
								}
								while((next>=current&&next-current<winsize)||(next<current&&next+snum-current<winsize)){
									state[next]=1;
									next=(next+1)%snum;
								}
								log=df.format(new Date())+", source = "+listening_port+", dest = "+sender_IP+" "+sender_port+", ACK # = "+ pktnum+", FIN = 0\n";
								if(stdout)                                                              //show or log ACK sent
									System.out.print(log);
								else{
									logoutput.write(log);
									logoutput.flush();
								}
							}
							corrupt=false;
						}
						ds.receive(pkt);
						if(msg[13]==1)
							break;
					}
				}
				osw.write("exit\n");        //FIN message via TCP
				osw.flush();
				try{
					Thread.sleep(1000);
				}catch(InterruptedException e){
					e.printStackTrace();
				}
				osw.write("exit\n");
				osw.flush();
				while(true){
					ds.receive(pkt);
					if(msg[12]==1)
						break;
				}
				try{
					Thread.sleep(1000);
				}catch(InterruptedException e){
					e.printStackTrace();
				}
			}catch(IOException e){
				e.printStackTrace();
			}
			System.out.println("Delivery completed successfully");
		}
	}
	
	public static void main(String[] args){
		try{
			Receiver rcvr=new Receiver();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
}
