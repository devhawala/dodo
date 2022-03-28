/*
Copyright (c) 2018, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.hub;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Hub program simulating the thick yellow coax cable of a Xerox network.
 * <p>
 * This is in fact a simple packet forwarder, accepting connections on TCP
 * port 3333 (by default), with each packet received on one connections being
 * forwarded to the other connections. Each packet consists of a 2-byte length
 * (big endian) and the raw packet from the client machine.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class NetHub {
	
	private static final int HUB_SOCKET = 3333;
	
	private static final int PACKET_LENGTH = 1024;
	
	private static final int MAX_CONNECTIONS = 256;

	private static void log(String txt) {
		System.out.println(txt);
	}

	private static void logf(String pattern, Object... args) {
		System.out.printf(pattern, args);
	}
	
	private static class Packet {
		public final byte[] data = new byte[PACKET_LENGTH + 2]; // +2 for the length data
		public int packetLen; // net value = length of the content (payload)
	}
	
	/**
	 * Interface for connections to a NetHub.
	 */
	public interface iLine {
		
		/**
		 * Transmit a packet ingone from some other machine to the
		 * machine connected to this line (remote to local transmission).
		 * 
		 * @param p packet to be transmitted.
		 */
		void send(Packet p);
		
		/**
		 * Put a packet into the queue of packets to be sent to
		 * the machine connected to this line.
		 * 
		 * @param p the packet to be forwarded.
		 */
		void enqueueOutPacket(Packet p);
		
		/**
		 * Get the next packet from the packet queue to be transmitted
		 * to the machine connected to this line, possibly waiting for
		 * a packet to be enqueued.
		 * 
		 * @return the next packet to be transmitted.
		 * @throws InterruptedException
		 */
		Packet dequeuePacket() throws InterruptedException;
		
		/**
		 * end all transmissions.
		 */
		void stop();
		
	}
	
	private static final List<iLine> lines = new ArrayList<>();
	private static int connNo = 0;
	
	private static void addLine(Socket s) throws IOException {
		synchronized(lines) {
			if (lines.size() > MAX_CONNECTIONS) {
				log("Rejecting new line connection (MAX_CONNECTIONS reached)");
				s.close();
				return;
			}
			lines.add(new Line(s, connNo++));
		}
	}
	
	private static void dropLine(iLine f) {
		synchronized(lines) {
			lines.remove(f);
		}
	}
	
	private static List<iLine> getLinesExcept(iLine exception) {
		synchronized(lines) {
			List<iLine> otherLines = new ArrayList<>(lines);
			otherLines.remove(exception);
			return otherLines;
		}
	}
	
	private static class Forwarder implements Runnable {
		
		private final iLine line;
		
		public Forwarder(iLine line) {
			this.line = line;
		}

		@Override
		public void run() {
			while(true) {
				try {
					Packet packet = this.line.dequeuePacket(); 
					this.line.send(packet);
				} catch(Exception e) {
					log("Forwarder got exception(" + e.getClass().getCanonicalName() + "): " + e.getMessage());
					this.line.stop();
					break;
				}
			}
		}
		
	}
	
	private static void distribute(Packet p, iLine ingoingLine) {
		List<iLine> targets = getLinesExcept(ingoingLine);
		for(iLine t : targets) {
			if (t != ingoingLine) {
				t.enqueueOutPacket(p);
			}
		}
	}
	
	private static class Line implements iLine, Runnable {
		
		private final Socket socket;
		private final int connNo;
		private final InputStream is;
		private final OutputStream os;
		
		private final List<Packet> outPackets = new ArrayList<>();
		
		private Thread reader;
		private boolean stop = false;
		private boolean stopped = false;
		private boolean qemuLine = false;
		
		private final Thread forwarder;
		
		public Line(Socket socket, int connNo) throws IOException {
			this.socket = socket;
			this.connNo = connNo;
			this.is = socket.getInputStream();
			this.os = socket.getOutputStream();
			
			this.reader = new Thread(this);
			this.reader.setName("Reader #" + connNo);
			this.reader.start();
			
			this.forwarder = new Thread(new Forwarder(this));
			this.forwarder.setDaemon(true);
			this.forwarder.setName("Forwarder #" + connNo);
			this.forwarder.start();
		}
		
		@Override
		public void stop() {
			synchronized(this) {
				this.stop = true;
				try {
					if (this.reader != null) { this.reader.interrupt(); }
				} catch(Exception e) {
					log("Stopping connection #" + this.connNo + " got exception(" + e.getClass().getCanonicalName() + "): " + e.getMessage());
				}
			}
		}
		
		private boolean isQemuLine() {
			synchronized(this) {
				return this.qemuLine;
			}
		}
		
		private void switchToQemuMode() {
			synchronized(this) {
				this.qemuLine = true;
			}
		}
		
		@Override
		public void send(Packet p) {
			try {
				log("Distribute: connection #" + this.connNo + " => sending packet with net size: " + p.packetLen);
				OutputStream o = this.getOutputStream();
				if (o != null) {
					if (this.isQemuLine()) {
						byte[] lenBytes = { 0, 0, (byte)((p.packetLen >> 8) & 0xFF), (byte)(p.packetLen & 0xFF) };
						log("Distribute: (qemu) sending length");
						o.write(lenBytes);
						o.flush();
						log("Distribute: (qemu) sending data");
						o.write(p.data, 2, p.packetLen);
						o.flush();
						log("Distribute: (qemu) flushed data");
					} else {
						o.write(p.data, 0, p.packetLen + 2);
						log("Distribute: sent data");
						o.flush();
						log("Distribute: flushed data");
					}
				}
			} catch (IOException e) {
				System.out.printf("Line.send( conn #%d ) => exception %s\n", this.connNo, e.getMessage());
				// ignored
			}
		}
		
		@Override
		public void enqueueOutPacket(Packet p) {
			synchronized(this.outPackets) {
				this.outPackets.add(p);
				this.outPackets.notifyAll();
			}
		}
		
		@Override
		public Packet dequeuePacket() throws InterruptedException {
			synchronized(this.outPackets) {
				while(this.outPackets.isEmpty()) {
					this.outPackets.wait();
				}
				return this.outPackets.remove(0);
			}
		}
		
		private int getInt(int byteLen) throws IOException {
			int val = 0;
			for (int i = 0; i < byteLen; i++) {
				int b = this.is.read();
				if (b < 0) {
					log("Rcv-Thread: connection #" + this.connNo + " => got EOF byte(b) => connection closed");
					return -1;
				}
				val = (val << 8) + b;
			}
			logf(".. getInt(%d) -> %d\n", byteLen, val);
			return val;
		}
		
		private Packet getPacket(int pLen) throws IOException {
			Packet p = new Packet();
			p.data[0] = (byte)((pLen >> 8) & 0xFF);
			p.data[1] = (byte)(pLen & 0xFF);
			if (pLen > 0) {
				int remaining = pLen;
				int pos = 2;
				while(remaining > 0) {
					int rLen = this.is.read(p.data, pos, remaining);
					remaining -= rLen;
					pos += rLen;
				}
			}
			p.packetLen = pLen;
			return p;
		}

		@Override
		public void run() {
			boolean firstPacket = true;
			log("Rcv-Thread: connection #" + this.connNo + " => starting");
			int pNum = 0;
			try {
				while(!this.doStop()) {					
					int pLen = (this.isQemuLine()) ? this.getInt(4) : this.getInt(2);
					if (pLen == 0 && firstPacket) {
						pLen = this.getInt(2); // get the remaining 2 of 4 bytes
						if (pLen == 14) {
							Packet p = this.getPacket(pLen);
							boolean allZero = true;
							for (int i = 0; i < 14; i++) {
								if (p.data[i + 2] != 0) {
									allZero = false;
									break;
								}
							}
							if (allZero) {
								this.switchToQemuMode();
							} else { 
								distribute(p, this);
							}
							continue;
						}
					}
					firstPacket = false;
					
					if (pLen < 1) {
						log("Rcv-Thread: connection #" + this.connNo + " => packet size " + pLen + " ~ EOF => dropping line");
						break;
					}
					
					if (pLen > PACKET_LENGTH) {
						log("Rcv-Thread: connection #" + this.connNo + " => invalid packet size " + pLen + " => dropping line");
						break;
					}
					log("Rcv-Thread: connection #" + this.connNo + " => received packet "+ (++pNum) + " with net size: " + pLen);
					
					Packet p = this.getPacket(pLen);
					
					// forward the packet to the other lines
					distribute(p, this);
				}
			} catch (IOException e) {
				// leave the loop on error
				log("Rcv-Thread: connection #" + this.connNo + " got exception(" + e.getClass().getCanonicalName() + "): " + e.getMessage());
			}
			
			// close the line
			this.shutdown();
		}
		
		private boolean doStop() {
			synchronized(this) {
				return this.stop;
			}
		}
		
		private OutputStream getOutputStream() {
			synchronized(this) {
				if (this.stopped) { return null; }
				return this.os;
			}
		}
		
		private void shutdown() {
			log("Rcv-Thread: connection #" + this.connNo + " .. shutdown");
			
			dropLine(this);
			
			synchronized(this) {
				try { this.is.close(); } catch (IOException e) { /* ignored */ }
				try { this.os.close(); } catch (IOException e) { /* ignored */ }
				try { this.socket.close(); } catch (IOException e) { /* ignored */ }
				this.stopped = true;
				this.reader = null;
			}
		}
	}
	
	public static void main(String[] args) throws InterruptedException {
		log("Starting NetHub");
		
		ServerSocket serviceSocket = null;
		try {
			serviceSocket = new ServerSocket(HUB_SOCKET);
		} catch (IOException  e) {
			log("**** cannot listen to port " + HUB_SOCKET);
			return;
		}
		
		try {
			while(true) {
				Socket s = serviceSocket.accept();
				s.setTcpNoDelay(true);
				addLine(s);
			}
		} catch (IOException e) {
			// ignored
		}
		
		try { serviceSocket.close(); } catch (IOException e) { /* ignored */ }
	}
}
