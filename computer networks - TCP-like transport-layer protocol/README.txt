I. Sender
a. There are 3 kinds of threads in the sender program: 
    Send: sends pkts. Also sends FIN request like TCP:
          sender -> receiver -> sender, then receiver -> sender -> receiver
    Timer: using Selective Repeating, one for each pkt;
    ReceiveACK: receives ACK from receiver via TCP socket. Also estimates RTT and adjusts timeout value:
                EstimatedRTT = EstimatedRTT * 7/8 + SampleRTT / 8
b. The structure of the header:
    first 4 bytes (0-3): source and dest port;
    4-7: sequence number of the packet;
    8-11: ACK number, not used, because ACKs are sent via TCP socket;
    12-13: flags, only FIN actually used, in FIN request set to 1;
    14-15: window size, for receiver to align it to that of sender, no larger than 16;
    16-17: checksum computed over pkt;
    18-19: indicating total length of the pkt (default: 512 bytes).
c. Invoke
    The sender MUST run first before the receiver runs. After running the sender program (after command: java Sender), input:
          sender <filename> <remote_IP> <remote_port> <ack_port_num> <log_filename> <window_size>
    If the window_size is missing, then the window size will be set to 1. (since sequence number ranging from 0-31, window size should be no larger than 16)
d. Log file
    Retransmitted pkts are not included. FIN message is not included (flag FIN is always 0 in log file). Estimated RTT is in the unit of nanoseconds.
e. Statistics
    Header length and retransmission not included in total data sent, i.e. total data sent = file size.

II. Receiver
a. Only uncorrupted pkts with seq # in the range of the receiver window will be extracted and sent back with ACK, or the Sender will keep sending. This helps to deal with pkt loss, delay, corruption and order.
b. Invoke
    Similar to Sender. After running Receiver program (command java Receiver), input:
           receiver <filename> <listening_port> <sender_IP> <sender_port> <log_filename>
   The receiver MUST run after the Sender is running.
c. Log file
    Similar to that of Receiver. Out of order and corrupted pkts received are not included. FIN request is not included.
d. ACKs are sent to Sender through TCP socket (Sender as the server and Receiver as the client).

III. Running
a. Makefile:
    Command $make all to compile both, $make Sender to compile Sender, $make Receiver to compile Receiver, $make clean to delete all .class files.
b. Testing:
    When using the proxy, it's better to make the delay no larger than 2 sec (best within 1 sec), since the RTT is estimated using System.nanoTime(), for the prevention of overflow.