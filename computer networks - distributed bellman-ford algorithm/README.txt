1. General description of this program
The program was written in Windows 8.1 and the java version is 1.8.
In the client program, there are multiple threads: ReceiveDV to receive pkt from neighbors, SendDV to send pkt to neighbors (periodically or when table is changed), a Timer to find inactive links to neighbors, and a main thread providing user interfaces.
pkt: 1 byte stands for msg type (0 no change in table, 1 table changed, 2 linkdown msg, 3 linkup msg)
     1 byte pkt length (no larger than 128 bytes, so the number of clients MUST NOT EXCEED 10!)
     4 bytes source ip, 2 bytes source port, 2 bytes dest port
     In the routing table, each DV will be 10 bytes in pkts, so size of routing table should not exceed 10
2 UDP socket for sending msg pkts, rcvsocket (on receiving port), sdsocket (on sending port, equals receiving port+1)
clients identified by IP+RECEIVING PORT!!

2. Invoking and user interfaces
Invoking the client: after the client program start, input in the following format:
localport timeout ipaddress1 port1 weight1 ...
Here, the timeout should be an integer (in seconds).
Each client is identified by [ipaddress port](this port is receiving port), while its sending port number is port+1. If multiple clients has the same ipaddress, their port numbers should at least have a difference of 2.
The weight should also be an integer.
You can use this interface to initally the network or add a client to the original network at any time, following the inputing format

ShowRT: just input showrt
The distance to the client itself is not shown. Infinite distance (represented by -1) is NOT shown in this interface.

LinkDown: input like this:
linkdown ipaddress port
the ip+port identifies a neighbor, where the link in between is down.

LinkUp: input like this:
linkup ipaddress port
If the link to neighbor ip+port was set down, this is to recover the link.

Close: just input:
close
And then all links to this client are down. Then CTRL+C can be used to exit the client program.

3. Makefile
compile: make all
clean all .class files: make clean
execute: java bfclient

4. Additional features
For the linkdown and close interfaces, poison reverse is implemented to solve the count-to-infinity problem:
When a link is down, all other clients which have shortest paths through the down link will advertise their corresponding distances in theirs table as infinity, until new routes are found.
And there won't be many loops in the network, so the convergence will be in only a few steps.

NOTE: after using interfaces (especially linkdown and close), please wait for about HALF the TIMEOUT to wait for the routing table to converge.
This is because I make the timer in the program to check its table for every timeout/10 of time for slowing down, so that my old laptop CPU won't get hot when there are over 5 clients in testing...