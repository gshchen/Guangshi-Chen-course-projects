import java.io.*;
import java.net.*;
import java.util.*;

public class bfclient extends Thread{
	private DatagramSocket rcvsocket;
	private DatagramSocket sdsocket;
	private int port;                                  ///identification port, receiving port
	private String localaddr;                          ///IP+port for this client
	private Map<String,Neighbor> neighbors;                                   //IP+port, timeout
	private Map<String,LinkCost> routetable;                         //neighbors with link cost, if not neighbors, value=-1
	private Map<String,Map<String,LinkCost>> neighbortable;         //keep tables of neighbors
	private long timeout=30000;
	private SendDV sddv;
	private ReceiveDV rcvdv;
	private Timer tm;
	private boolean exit;                                     ////close command
	private boolean modified;                                 ////route table has changed? initially yes
	
	public bfclient(String line) throws IOException{
		localaddr=null;
		exit=false;
		modified=true;
		Scanner scan=new Scanner(line);
		port=Integer.parseInt(scan.next());
		timeout=(long)(Integer.parseInt(scan.next())*1000);
		rcvsocket=new DatagramSocket(port);
		sdsocket=new DatagramSocket(port+1);                    //sending socket on port+1
		neighbors=new HashMap<String,Neighbor>();
		routetable=new HashMap<String,LinkCost>();
		neighbortable=new HashMap<String,Map<String,LinkCost>>();
		String addr;
		int value;
		while(scan.hasNext()){
			addr=scan.next();                              ///neighbor IP
			addr=addr+" "+scan.next();                          ///neighbor IP+port
			value=Integer.parseInt(scan.next());
			neighbors.put(addr,new Neighbor(value,System.currentTimeMillis()));
			routetable.put(addr,new LinkCost(value,addr));
		}
		sddv=new SendDV();
		rcvdv=new ReceiveDV();
		tm=new Timer();
	}
	
	public void linkDown(String addr){
		if(neighbors.containsKey(addr)&&!neighbors.get(addr).getDown()){
			neighbors.get(addr).downLink();
			byte[] dg=pktToSend();                                      ////message to the other side that the link is down
			dg[0]=2;                                                     ////indicate link is down
			Scanner scan=new Scanner(addr);
			String destip=scan.next();
			String destip2=destip.replace('.',' ');
			int destport=scan.nextInt();
			scan=new Scanner(destip2);                                   ////add dest ip to pkt
			for(int i=0;i<4;i++)
				dg[i+2]=(byte)(scan.nextInt());
			byte[] dport=intToByte2(destport);
			dg[6]=dport[0];                                             ////add dest port to pkt
			dg[7]=dport[1];
			boolean change=false;
			Set<String> newlink=new HashSet<String>();
			LinkCost lc;
			for(String element:routetable.keySet()){                   ////update own table due to linkdown change
				lc=routetable.get(element);
				if(lc.getNeighbor().equals(addr)){
					lc.setLinkCost(-1);
					change=true;
					if(neighbors.containsKey(element)&&!neighbors.get(element).getDown())
						newlink.add(element);                                                             /////wait and then send!!!!!!!
				}
			}
			try{
				DatagramPacket pkt=new DatagramPacket(dg,dg.length,InetAddress.getByName(destip),destport);
				sdsocket.send(pkt);
			}catch(IOException e){
				e.printStackTrace();
			}
			if(!newlink.isEmpty()){
				LinkTimer lt=new LinkTimer(newlink);
				lt.start();
			}
			if(change)
				modified=true;
		}			                   ///should send msg to neighbor addr
	}
	
	public void linkUp(String addr){
		if(neighbors.containsKey(addr)&&neighbors.get(addr).getDown()){
			neighbors.get(addr).upLink();
			byte[] dg=pktToSend();                                      ////message to the other side that the link is up
			dg[0]=3;                                                    ///indicate link is up
			Scanner scan=new Scanner(addr);
			String destip=scan.next();
			String destip2=destip.replace('.',' ');
			int destport=scan.nextInt();
			scan=new Scanner(destip2);                                   ////add dest ip to pkt
			for(int i=0;i<4;i++)
				dg[i+2]=(byte)(scan.nextInt());
			byte[] dport=intToByte2(destport);
			dg[6]=dport[0];                                             ////add dest port to pkt
			dg[7]=dport[1];
			boolean change=false;
			if(routetable.containsKey(addr)){                          ////update its own table due to linkup change
				int cxy;
				LinkCost lc=routetable.get(addr);
				if((cxy=neighbors.get(addr).getCXY())<lc.getLinkCost()){
					lc.setLinkCost(cxy);
					lc.setNeighbor(addr);
					change=true;
				}
			}
			else{
				routetable.put(addr, new LinkCost(neighbors.get(addr).getCXY(),addr));
				change=true;
			}
			try{
				DatagramPacket pkt=new DatagramPacket(dg,dg.length,InetAddress.getByName(destip),destport);
				sdsocket.send(pkt);
			}catch(IOException e){
				e.printStackTrace();
			}
			if(change)
				modified=true;
		}                    ///should send msg to neighbor addr    
	}
	
	public void showRT(){
		System.out.println("Distance vector list is:");
		LinkCost lc;
		for(String element:routetable.keySet()){
			lc=routetable.get(element);
			System.out.println("Destination = "+element+", Cost = "+lc.getLinkCost()+", Link = "+lc.getNeighbor());
		}
	}
	
	public void close(){
		byte[] dg=pktToSend();                                      ////message to the other side that the link is down
		dg[0]=2;
		Scanner scan;
		String destip;
		String destip2;
		int destport;
		DatagramPacket pkt;
		try{
			for(String element:neighbors.keySet()){             ////notify all neighbors all links are down
				if(!neighbors.get(element).getDown()){
					scan=new Scanner(element);
					destip=scan.next();
					destip2=destip.replace('.',' ');
					destport=scan.nextInt();
					scan=new Scanner(destip2);                                   ////add dest ip to pkt
					for(int i=0;i<4;i++)
						dg[i+2]=(byte)(scan.nextInt());
					byte[] dport=intToByte2(destport);
					dg[6]=dport[0];                                             ////add dest port to pkt
					dg[7]=dport[1];
					pkt=new DatagramPacket(dg,dg.length,InetAddress.getByName(destip),destport);
					sdsocket.send(pkt);
				}
			}
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	private class LinkTimer extends Thread{              ///used for poison reverse like algorithm
		private Set<String> newlink;
		
		public LinkTimer(Set<String> nl){
			newlink=nl;
		}
		
		public void run(){
			try{
				Thread.sleep(timeout/2);
			}catch(InterruptedException e){
				e.printStackTrace();
			}
			for(String element:newlink)
				routetable.put(element, new LinkCost(neighbors.get(element).getCXY(),element));
			modified=true;
		}
	}
	
	private class Neighbor{                         //information of neighbors
		private int cxy;                            //linkcost to neighbor
		private long last;                          //neighbor's last activity
		private boolean down;                      //link to this neighbor is down?
		
		public Neighbor(int value,long time){
			cxy=value;
			last=time;
			down=false;
		}
		
		public void upLink(){
			down=false;
		}
		
		public void downLink(){
			down=true;
		}
		
		public boolean getDown(){
			return down;
		}
		
		public int getCXY(){
			return cxy;
		}
		
		public long getLast(){
			return last;
		}
		
		public void setLast(long time){
			last=time;
		}
	}
	
	private class LinkCost{
		private int linkcost;                           //-1 indicates infinite
		private String neighbor;                        //through this neighbor to the lowest linkcost
		
		public LinkCost(int value,String addr){
			linkcost=value;
			neighbor=addr;
		}
		
		public LinkCost(int value){
			linkcost=value;
			neighbor=null;
		}
		
		public int getLinkCost(){
			return linkcost;
		}
		
		public String getNeighbor(){
			return neighbor;
		}
		
		public void setLinkCost(int value){
			linkcost=value;
		}
		
		public void setNeighbor(String addr){
			neighbor=addr;
		}
	}
	
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
	
	public byte[] pktToSend(){                                  ///construct pkt containing DV msg to be sent to neighbors
		byte[] dg=new byte[10*(routetable.size()+1)];
		Scanner token;
		int i=1;
		dg[0]=0;                                             /////when is set to 1, client table changed. 2 linkdown, 3 linkup. 0 no change
		dg[1]=(byte)routetable.size();
		byte[] thisport=intToByte2(port);
		dg[8]=thisport[0];                                   /////source port
		dg[9]=thisport[1];
		String addr;
		for(String element:routetable.keySet()){             ////put information of this client's table into pkt sent to neighbors
			addr=element.replace('.',' ');
			token=new Scanner(addr);
			for(int j=0;j<4;j++){
				dg[10*i+j]=(byte)token.nextInt();
			}
			byte[] portnum=intToByte2(token.nextInt());
			dg[10*i+4]=portnum[0];
			dg[10*i+5]=portnum[1];
			byte[] cost=intToByte(routetable.get(element).getLinkCost());
			for(int j=0;j<4;j++){
				dg[10*i+6+j]=cost[j];
			}
			i++;
		}
		return dg;
	}
	
	private class Timer extends Thread{                                ////looking for inactive neighbors
		public void run(){
			try{
				Thread.sleep(timeout/2);
			}catch(InterruptedException e){
				e.printStackTrace();
			}
			while(!exit){
                try{
                    Thread.sleep(timeout/10);
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
                boolean change=false;
				Set<String> newlink=new HashSet<String>();
				Neighbor nb;
				for(String element:neighbors.keySet()){
					nb=neighbors.get(element);
					if(!nb.getDown()&&System.currentTimeMillis()-nb.getLast()>3*timeout){     /////not heard for 3timeout, link is down
						nb.downLink();
						LinkCost lc;
						for(String addr:routetable.keySet()){
							lc=routetable.get(addr);
							if(lc.getNeighbor().equals(element)){
								lc.setLinkCost(-1);
								change=true;
								if(neighbors.containsKey(addr)&&!neighbors.get(addr).getDown())
									newlink.add(addr);                                                             /////wait and then send!!!!!!!
							}
						}               ///set to infinite in its route table at first, unreachable, poison reverse?
					}
				}
				if(!newlink.isEmpty()){
					LinkTimer lt=new LinkTimer(newlink);
					lt.start();
				}
				if(change)
					modified=true;
			}
		}
	}
	
	private class SendDV extends Thread{            /////sending pkt periodically
		public void run(){
			long time=System.currentTimeMillis();
			Set<String> delete=new HashSet<String>();
			while(!exit){
				System.out.print("");////////"just to prevent block"
                try{
                    Thread.sleep(timeout/10);
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
                if(modified||System.currentTimeMillis()-time>timeout){             ////timeout or table changed
					byte[] dg=pktToSend();
					if(modified){
						modified=false;                                            ////table has changed
						dg[0]=1;
					}
					Scanner scan;
					String destip;
					String destip2;
					int destport;
					DatagramPacket pkt;
					try{
						for(String element:neighbors.keySet()){                       /////sent pkts to neighbors
							if(!neighbors.get(element).getDown()){
								scan=new Scanner(element);
								destip=scan.next();
								destip2=destip.replace('.',' ');
								destport=scan.nextInt();
								scan=new Scanner(destip2);                                   ////add dest ip to pkt
								for(int i=0;i<4;i++)
									dg[i+2]=(byte)(scan.nextInt());
								byte[] dport=intToByte2(destport);
								dg[6]=dport[0];                                             ////add dest port to pkt
								dg[7]=dport[1];
								pkt=new DatagramPacket(dg,dg.length,InetAddress.getByName(destip),destport);
								sdsocket.send(pkt);////////send Distance Vector to Neighbors
							}
						}
					}catch(IOException e){
						e.printStackTrace();
					}
					for(String element:routetable.keySet())            ///"poison reverse like"
						if(routetable.get(element).getLinkCost()==-1)
							delete.add(element);
					for(String element:delete)
						routetable.remove(element);
					delete.clear();
					time=System.currentTimeMillis();
				}
			}
		}
	}
	
	private class ReceiveDV extends Thread{          ////receiving pkt from neighbors
		public void run(){
			byte[] buffer;
			DatagramPacket pkt;                ////number of clients better not exceed 10
			while(!exit){
				buffer=new byte[128];
				pkt=new DatagramPacket(buffer,buffer.length);
				try{
					rcvsocket.receive(pkt);
				}catch(IOException e){
					e.printStackTrace();
				}
				byte[] srcport=new byte[2];
				srcport[0]=buffer[8];
				srcport[1]=buffer[9];
				byte[] sourceip=pkt.getAddress().getAddress();               ////extract source ip+port
				int[] srcip=new int[4];
				for(int i=0;i<4;i++)
					srcip[i]=(int)sourceip[i];
				String naddr=srcip[0]+"."+srcip[1]+"."+srcip[2]+"."+srcip[3]+" "+byteToInt2(srcport);       ///who send this pkt,            need from packet
				if(buffer[0]==2){                                           ////link down msg from neighbor
					neighbors.get(naddr).downLink();
					boolean change=false;
					Set<String> newlink=new HashSet<String>();
					LinkCost lc;
					for(String element:routetable.keySet()){
						lc=routetable.get(element);
						if(lc.getNeighbor().equals(naddr)){
							lc.setLinkCost(-1);
							change=true;
							if(neighbors.containsKey(element)&&!neighbors.get(element).getDown())
								newlink.add(element);                                                             /////wait and then send!!!!!!!
						}
					}
					if(!newlink.isEmpty()){
						LinkTimer lt=new LinkTimer(newlink);
						lt.start();
					}
					if(change)
						modified=true;
					continue;                                            ///neighbor is down
				}
				if(buffer[0]==3){                                        ///linkup msg from neighbor
					Neighbor nb=neighbors.get(naddr);
					nb.upLink();
					nb.setLast(System.currentTimeMillis());
					if(routetable.containsKey(naddr)){
						int cxy;
						LinkCost lc=routetable.get(naddr);
						if((cxy=nb.getCXY())<lc.getLinkCost()){
							lc.setLinkCost(cxy);
							lc.setNeighbor(naddr);
							modified=true;
						}
					}
					else{
						routetable.put(naddr, new LinkCost(nb.getCXY(),naddr));
						modified=true;
					}
					continue;                                            ///neighbor is up (msg)
				}
				if(localaddr==null){
					int[] thisaddr=new int[4];
					for(int j=0;j<4;j++)
						thisaddr[j]=(int)buffer[2+j];
					localaddr=thisaddr[0]+"."+thisaddr[1]+"."+thisaddr[2]+"."+thisaddr[3]+" "+port;
				}
				if(neighbors.containsKey(naddr)){                                 //////link up
					Neighbor nb=neighbors.get(naddr);
					if(nb.getDown()){
						nb.upLink();
						if(routetable.containsKey(naddr)){
							int cxy;
							LinkCost lc=routetable.get(naddr);
							if((cxy=nb.getCXY())<lc.getLinkCost()){
								lc.setLinkCost(cxy);
								lc.setNeighbor(naddr);
								modified=true;
							}
						}
						else{
							routetable.put(naddr, new LinkCost(nb.getCXY(),naddr));
							modified=true;
						}
					}
					nb.setLast(System.currentTimeMillis());           ////refresh activity of neighbor
				}
				if(buffer[0]==1){                                    ////neighbor table has changed
					int tablelength=buffer[1];
					Map<String,LinkCost> ntable=new HashMap<String,LinkCost>();
					boolean change=false;
					for(int i=1;i<=tablelength;i++){                 ////extract information in neighbor's table
						int[] ipaddr=new int[4];
						for(int j=0;j<4;j++)
							ipaddr[j]=(int)buffer[10*i+j];
						byte[] portnum=new byte[2];
						portnum[0]=buffer[10*i+4];
						portnum[1]=buffer[10*i+5];
						String ntaddr=ipaddr[0]+"."+ipaddr[1]+"."+ipaddr[2]+"."+ipaddr[3]+" "+byteToInt2(portnum);
						byte[] ntlink=new byte[4];
						for(int j=0;j<4;j++)
							ntlink[j]=buffer[10*i+6+j];
						if(ntaddr.equals(localaddr)&&!neighbors.containsKey(naddr)){
							neighbors.put(naddr,new Neighbor(byteToInt(ntlink),System.currentTimeMillis()));    //////add new neighbor if it is
							routetable.put(naddr,new LinkCost(byteToInt(ntlink),naddr));
							change=true;
						}
						ntable.put(ntaddr,new LinkCost(byteToInt(ntlink)));
					}
					neighbortable.put(naddr, ntable);                                ////update neighbor's route table
					                                                              /////to implement bellman-ford algorithms******** to update this node's route table
					Set<String> newlink=new HashSet<String>();
					LinkCost nlc;
					int thiscxy=neighbors.get(naddr).getCXY();
					for(String element:ntable.keySet()){                         /////!!!!!!!!the most important and difficult part
						if(!element.equals(localaddr)){
							nlc=ntable.get(element);
							if(nlc.getLinkCost()==-1){                           ////"poison reverse like"
								if(routetable.containsKey(element)){
									LinkCost thislc=routetable.get(element);
									if(thislc.getNeighbor().equals(naddr)){
										thislc.setLinkCost(-1);
										change=true;
										if(neighbors.containsKey(element)&&!neighbors.get(element).getDown()){
											newlink.add(element);                                                 //////wait and then send!!!!!
										}
									}
								}
							}
							else if(routetable.containsKey(element)){
								LinkCost thislc=routetable.get(element);
								int value;
								if(!element.equals(localaddr)&&(value=nlc.getLinkCost()+thiscxy)<thislc.getLinkCost()){     ////Bellman-Ford algorithm
									thislc.setLinkCost(value);
									thislc.setNeighbor(naddr);
									change=true;
								}
							}
							else{
								routetable.put(element, new LinkCost(nlc.getLinkCost()+thiscxy,naddr));
								change=true;
							}
						}
					}
					if(!newlink.isEmpty()){
						LinkTimer lt=new LinkTimer(newlink);
						lt.start();
					}
					if(change)
						modified=true;                                              //////table has changed
				}
			}
		}
	}
	
	public void run(){                  ////executing user interface command
		tm.start();
		rcvdv.start();
		sddv.start();
		while(true){
			Scanner input=new Scanner(System.in);
			String line=input.nextLine();
			if(line.equalsIgnoreCase("close")){
				close();
				break;
			}
			else if(line.equalsIgnoreCase("showrt"))
				showRT();
			else{
				Scanner scan=new Scanner(line);
				String token=scan.next();
				if(token.equalsIgnoreCase("linkdown")){
					String addr=scan.next()+" "+scan.next();
					linkDown(addr);
				}
				if(token.equalsIgnoreCase("linkup")){
					String addr=scan.next()+" "+scan.next();
					linkUp(addr);
				}
			}
		}
		exit=true;
	}
	
	public static void main(String[] args)throws IOException{
        System.out.println("Please invoke the client:");
        Scanner scan=new Scanner(System.in);
        String line=scan.nextLine();
		bfclient client=new bfclient(line);
		client.start();
	}

}
