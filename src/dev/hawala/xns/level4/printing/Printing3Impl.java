/*
Copyright (c) 2019, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.printing;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level4.common.BulkData1.DescriptorKind;
import dev.hawala.xns.level4.common.Time2.Time;
import dev.hawala.xns.level4.printing.InterpressUtils.InterpressException;
import dev.hawala.xns.level4.printing.Printing3.FormatterStatusEnum;
import dev.hawala.xns.level4.printing.Printing3.FormatterStatusRecord;
import dev.hawala.xns.level4.printing.Printing3.GetPrintRequestStatusParams;
import dev.hawala.xns.level4.printing.Printing3.GetPrintRequestStatusResults;
import dev.hawala.xns.level4.printing.Printing3.GetPrinterPropertiesResults;
import dev.hawala.xns.level4.printing.Printing3.GetPrinterStatusResults;
import dev.hawala.xns.level4.printing.Printing3.InsufficientSpoolSpaceRecord;
import dev.hawala.xns.level4.printing.Printing3.KnownSizeRecord;
import dev.hawala.xns.level4.printing.Printing3.MediaRecord;
import dev.hawala.xns.level4.printing.Printing3.MediumChoice;
import dev.hawala.xns.level4.printing.Printing3.MediumRecord;
import dev.hawala.xns.level4.printing.Printing3.OtherSizeRecord;
import dev.hawala.xns.level4.printing.Printing3.PagesToPrintRecord;
import dev.hawala.xns.level4.printing.Printing3.PaperChoice;
import dev.hawala.xns.level4.printing.Printing3.PaperKnownSize;
import dev.hawala.xns.level4.printing.Printing3.PaperRecord;
import dev.hawala.xns.level4.printing.Printing3.PrintAttributeChoice;
import dev.hawala.xns.level4.printing.Printing3.PrintOptionsChoice;
import dev.hawala.xns.level4.printing.Printing3.PrintParams;
import dev.hawala.xns.level4.printing.Printing3.PrintResults;
import dev.hawala.xns.level4.printing.Printing3.PrinterPropertiesChoice;
import dev.hawala.xns.level4.printing.Printing3.PrinterStatusChoice;
import dev.hawala.xns.level4.printing.Printing3.PrinterStatusEnum;
import dev.hawala.xns.level4.printing.Printing3.PrinterStatusRecord;
import dev.hawala.xns.level4.printing.Printing3.PriorityHint;
import dev.hawala.xns.level4.printing.Printing3.PriorityHintRecord;
import dev.hawala.xns.level4.printing.Printing3.RequestID;
import dev.hawala.xns.level4.printing.Printing3.RequestStatusChoice;
import dev.hawala.xns.level4.printing.Printing3.SpoolerStatusEnum;
import dev.hawala.xns.level4.printing.Printing3.SpoolerStatusRecord;
import dev.hawala.xns.level4.printing.Printing3.StatusEnum;
import dev.hawala.xns.level4.printing.Printing3.StatusRecord;
import dev.hawala.xns.level4.printing.Printing3.TransferErrorRecord;
import dev.hawala.xns.level4.printing.Printing3.TransferProblem;
import dev.hawala.xns.level4.printing.Printing3.ValueRecord;

/**
 * Implementation of an printing service for the Printing3 Courier protocol.
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class Printing3Impl {
	
	// initialization data
	private static String printServiceName = "?:?:?";
	private static int[] printSvcId = new int[2];
	private static String outputDirectoryName;
	private static boolean disassembleIpFiles;
	private static byte[] ip2psProc;
	private static String psPostprocessor;
	
	// print job id generation
	private static long lastPrintJobId = System.currentTimeMillis() & 0x0000_FFFF_FFFF_FFFFL;
	
	private static synchronized long getNextPrintJobId() {
		lastPrintJobId++;
		return lastPrintJobId;
	}
	
	// handling for print request status
	private static Map<String,StatusEnum> printRequestStatus = new HashMap<>();
	
	private static synchronized void writePrintRequestStatus(String jobName, StatusEnum status) {
		printRequestStatus.put(jobName, status);
	}
	
	private static synchronized StatusEnum readPrintRequestStatus(String jobName) {
		return printRequestStatus.get(jobName);
	}
	
	public static void init(
			String serviceName,
			String outputDirectory,
			boolean disassembleIp,
			String paperSizeCandidates,
			String ip2psProcFilename,
			String printServicePsPostprocessor) {
		int printSvcNameHash = serviceName.hashCode();
		printServiceName = serviceName;
		printSvcId[0] = (printSvcNameHash >>> 16);
		printSvcId[1] = printSvcNameHash & 0xFFFF;
		outputDirectoryName = outputDirectory;
		disassembleIpFiles = disassembleIp;
		
		if (paperSizeCandidates != null) {
			String[] cands = paperSizeCandidates.split(",");
			List<PaperKnownSize> newPaperSizes = new ArrayList<>();
			for (String cand : cands) {
				try {
					newPaperSizes.add(PaperKnownSize.valueOf(cand.trim()));
				} catch (Exception e) {
					// ignored
				}
			}
			if (newPaperSizes.size() > 0) {
				supportedPaperSizes.clear();
				supportedPaperSizes.addAll(newPaperSizes);
			}
		}
		StringBuilder sb = new StringBuilder();
		String sep = "";
		for (PaperKnownSize size: supportedPaperSizes) {
			sb.append(sep).append(size.toString());
			sep = ", ";
		}
		System.out.printf("####### Printing3, supported paperSizes: %s\n", sb.toString());

		ip2psProc = null;
		if (ip2psProcFilename != null) {
			File ip2psProcFile = new File(ip2psProcFilename);
			if (!ip2psProcFile.exists() || !ip2psProcFile.canRead()) {
				System.out.printf("##\n##### Printing3, ip-to-ps proc '%s' not found or not readable\n##\n", ip2psProcFilename);
				return;
			}
			try (FileInputStream fis = new FileInputStream(ip2psProcFilename)) {
				int remaining = (int)(int)ip2psProcFile.length();
				ip2psProc = new byte[remaining];
				while(remaining > 0) {
					remaining -= fis.read(ip2psProc, ip2psProc.length - remaining, ip2psProc.length);
				}
				System.out.printf("####### Printing3, using ip-to-ps proc: %s\n", ip2psProcFilename);
			} catch(IOException e) {
				System.out.printf("##\n##### Printing3, unable to read ip-to-ps proc: %s\n##\n", ip2psProcFilename);
				return;
			}
		}
		
		psPostprocessor = null;
		if (printServicePsPostprocessor != null) {
			File postProcessor = new File(printServicePsPostprocessor);
			if (postProcessor.exists() && postProcessor.canExecute()) {
				psPostprocessor = printServicePsPostprocessor;
				System.out.printf("####### Printing3, using ps post-processor: %s\n", psPostprocessor);
			} else {
				System.out.printf("##\n##### Printing3, specified ps post-processor not found or not executable: %s\n##\n", psPostprocessor);
			}
		}
	}
	
	// paper sizes supported by this print service (here defaulted, possibly overridden at service startup)
	private static final List<PaperKnownSize> supportedPaperSizes = new ArrayList<>(Arrays.asList(
									PaperKnownSize.a4,
									PaperKnownSize.usLetter,
									PaperKnownSize.usLegal));
	
	/*
	 * ************************* registration/deregistration
	 */
	
	/**
	 * register Courier-Program Printing3 with
	 * this implementation to Courier dispatcher.
	 */
	public static void register() {
		if (outputDirectoryName == null || outputDirectoryName.isEmpty()) {
			throw new IllegalStateException("Printing3Impl not correctly initialized (empty outputDirectoryName)");
		}
		File outDir = new File(outputDirectoryName);
		if (!outDir.exists() || !outDir.isDirectory() || !outDir.canWrite()) {
			throw new IllegalStateException("Printing3Impl not correctly initialized (outputDirectory missing or not writable)");
		}
		
		Printing3 prog = new Printing3();
		prog.Print.use(Printing3Impl::print);
		prog.GetPrinterProperties.use(Printing3Impl::getPrinterProperties);
		prog.GetPrintRequestStatus.use(Printing3Impl::getPrintRequestStatus);
		prog.GetPrinterStatus.use(Printing3Impl::getPrinterStatus);
		CourierRegistry.register(prog);
	}
	
	/**
	 * unregister Printing3 implementation from Courier dispatcher
	 */
	public static void unregister() {
		CourierRegistry.unregister(Printing3.PROGRAM, Printing3.VERSION);
	}
	
	/*
	 * ************************* implementation of service procedures
	 */
	
	/*
	 * Print: PROCEDURE [master: BulkData.Source, printAttributes: PrintAttributes, printOptions: PrintOptions]
	 *  RETURNS [printRequestID: RequestID]
	 *  REPORTS [Busy, ConnectionError, InsufficientSpoolSpace,
	 *     InvalidPrintParameters, MasterTooLarge, MediumUnavailable,
	 *     ServiceUnavailable, SpoolingDisabled, SpoolingQueueFull, SystemError,
	 *     TooManyClients, TransferError, Undefined] = 0;
	 */
	@SuppressWarnings("unchecked")
	public static void print(PrintParams params, PrintResults results) {
		// lower 48 bits of the jobId will be part of the printRequestID and filename
		long jobId = getNextPrintJobId();
		
		// all possible print attributes
		String printObjectName = null;
		long printObjectCreateDate = -1;
		String senderName = null;
		
		for (int i = 0; i < params.printAttributes.size(); i++) {
			CHOICE<PrintAttributeChoice> printAttribute = params.printAttributes.get(i);
			PrintAttributeChoice printAttributeChoice = printAttribute.getChoice();
			switch(printAttributeChoice) {
			case printObjectName:
				printObjectName = ((ValueRecord<STRING>)printAttribute.getContent()).value.get();
				break;
			case printObjectCreateDate:
				printObjectCreateDate = ((ValueRecord<Time>)printAttribute.getContent()).value.get();
				break;
			case senderName:
				senderName = ((ValueRecord<STRING>)printAttribute.getContent()).value.get();
				break;
			}
		}
		
		// all possible print job parameters (most are not of interest here)
		long printObjectSize = -1;
		String recipientName = null;
		String message = null;
		int copyCount = -1;
		int pagesToPrintBeginPage = -1;
		int pagesToPrintEndPage = -1;
		PaperChoice paperChoice = null;
		PaperKnownSize paperKnownSize = null;
		int paperWidth = -1;
		int paperHeight = -1;
		PriorityHint priorityHint = null;
		int releaseKey = -1;
		Boolean staple = null;
		Boolean twoSided = null;
		
		for (int i = 0; i < params.printOptions.size(); i++) {
			CHOICE<PrintOptionsChoice> printOption = params.printOptions.get(i);
			PrintOptionsChoice printOptionsChoice = printOption.getChoice();
			switch(printOptionsChoice) {
			case printObjectSize:
				printObjectSize = ((ValueRecord<LONG_CARDINAL>)printOption.getContent()).value.get();
				break;
			case recipientName:
				recipientName = ((ValueRecord<STRING>)printOption.getContent()).value.get();
				break;
			case message:
				message = ((ValueRecord<STRING>)printOption.getContent()).value.get();
				break;
			case copyCount:
				copyCount = ((ValueRecord<CARDINAL>)printOption.getContent()).value.get();
				break;
			case pagesToPrint:
				PagesToPrintRecord pagesToPrint = (PagesToPrintRecord)printOption.getContent();
				pagesToPrintBeginPage = pagesToPrint.beginningPageNumber.get();
				pagesToPrintEndPage = pagesToPrint.endingPageNumber.get();
				break;
			case mediumHint:
				MediumRecord mediumRecord = (MediumRecord)printOption.getContent();
				PaperRecord paperRecord = (PaperRecord)mediumRecord.value.getContent();
				paperChoice = paperRecord.value.getChoice();
				switch(paperChoice) {
				case unknown:
					break;
				case knownSize:
					paperKnownSize = ((KnownSizeRecord)paperRecord.value.getContent()).paperKnownSize.get();
					break;
				case otherSize:
					OtherSizeRecord otherSizeRecord = (OtherSizeRecord)paperRecord.value.getContent();
					paperWidth = otherSizeRecord.width.get();
					paperHeight = otherSizeRecord.length.get();
				}
				break;
			case priorityHint:
				priorityHint = ((PriorityHintRecord)printOption.getContent()).value.get();
				break;
			case releaseKey:
				releaseKey = ((ValueRecord<CARDINAL>)printOption.getContent()).value.get();
				break;
			case staple:
				staple = ((ValueRecord<BOOLEAN>)printOption.getContent()).value.get();
				break;
			case twoSided:
				twoSided = ((ValueRecord<BOOLEAN>)printOption.getContent()).value.get();
				break;
			}
		}
		
		// check for unit test
		boolean isUnitTest = 
				(params.master.descriptor.getChoice() == DescriptorKind.nullKind)
				&& "UNITTEST".equals(senderName)
				&& "UNITTEST".equals(printObjectName);
		
		// generate RequestID for this job, creating the complete procedure results
		// (dropped if an error occurs in further processing)
		RequestID requestId = results.printRequestID;
		if (isUnitTest) {
			requestId.get(0).set(0xFFFF);
			requestId.get(1).set(0xFFFF);
			requestId.get(2).set(0xFFFF);
			requestId.get(3).set(0xFFFF);
			requestId.get(4).set(0xFFFF);
		} else {
			requestId.get(0).set(printSvcId[0]);
			requestId.get(1).set(printSvcId[1]);
			requestId.get(2).set((int)((jobId >> 32) & 0xFFFFL));
			requestId.get(3).set((int)((jobId >> 16) & 0xFFFFL));
			requestId.get(4).set((int)(jobId & 0xFFFFL));
		}
		
		// log the new printjob
		System.out.printf(
				"####### %s :: new printjob => requestId[ %04X %04X %04X %04X %04X ]" +
				"\n  printObjectName......: %s" +
				"\n  printObjectCreateDate: %d" +
				"\n  senderName...........: %s" +
				"\n  printObjectSize......: %d" +
				"\n  recipientName........: %s" +
				"\n  message..............: %s" +
				"\n  copyCount............: %d" +
				"\n  beginPage............: %d" +
				"\n  endPage..............: %d" +
				"\n  paperChoice..........: %s" +
				"\n  paperKnownSize.......: %s" +
				"\n  paperWidth...........: %d" +
				"\n  paperHeight..........: %d" +
				"\n  priorityHint.........: %s" +
				"\n  releaseKey...........: %d" +
				"\n  staple...............: %s" +
				"\n  twoSided.............: %s" +
				"\n",
				printServiceName,
				requestId.get(0).get(),
				requestId.get(1).get(),
				requestId.get(2).get(),
				requestId.get(3).get(),
				requestId.get(4).get(),
				printObjectName,
				printObjectCreateDate,
				senderName,
				printObjectSize,
				recipientName,
				message,
				copyCount,
				pagesToPrintBeginPage,
				pagesToPrintEndPage,
				paperChoice,
				paperKnownSize,
				paperWidth,
				paperHeight,
				priorityHint,
				releaseKey,
				staple,
				twoSided
				);
		
		// save the interpress file
		if (isUnitTest) { return; }
		System.out.println("... begin receiving ip master");
		if (params.master.descriptor.getChoice() != DescriptorKind.immediate) {
			System.out.printf("##\n### master.descriptor != immediate => TransferError(TransferProblem.formatIncorrect)\n##\n");
			new TransferErrorRecord(TransferProblem.formatIncorrect).raise();
		}
		String jobName = String.format("%04X_%04X_%04X", requestId.get(2).get(), requestId.get(3).get(), requestId.get(4).get());
		String filenameBase = String.format("%s/job_%s", outputDirectoryName, jobName);
		String ipFilename = filenameBase + ".ip";
		try (FileOutputStream fos = new FileOutputStream(ipFilename)) {
			byte[] buffer = new byte[512];
			params.master.transferRaw(
					buffer,
					(b, num, isLast) -> {
						if (num > 0) { fos.write(b, 0, num); }
					});
		} catch (IOException e) {
			System.out.printf("##\n### IOException(%s) => InsufficientSpoolSpaceRecord()\n##\n", e.getMessage());
			e.printStackTrace();
			new InsufficientSpoolSpaceRecord().raise();
		} catch (Exception e) {
			System.out.printf("##\n### Exception(%s) => Courier.error(invalidArguments)\n##\n", e.getMessage());
			e.printStackTrace();
			throw e;
		}
		System.out.println("... done receiving ip master");
		
		writePrintRequestStatus(jobName, StatusEnum.pending);
		
		// TODO: check if we "can" process the master (e.g. do we support the requested page size, ...)
		
		new JobProcessor(jobName, ipFilename, filenameBase, printObjectName, senderName, paperKnownSize);
		System.out.printf("####### done printjob with requestId[ %04X %04X %04X %04X %04X ], background processing started\n",
				requestId.get(0).get(),
				requestId.get(1).get(),
				requestId.get(2).get(),
				requestId.get(3).get(),
				requestId.get(4).get());
	}
	
	/*
	 * GetPrinterProperties: PROCEDURE
	 *  RETURNS [properties: PrinterProperties]
	 *  REPORTS [ServiceUnavailable, SystemError, Undefined] = 1;
	 */
	// public for testability
	@SuppressWarnings("unchecked")
	public static void getPrinterProperties(RECORD params, GetPrinterPropertiesResults results) {
		System.out.printf("####### %s :: getPrinterProperties()\n", printServiceName);
		
		// add paper sizes supported by this printer
		MediaRecord media = (MediaRecord)results.properties.add().setChoice(PrinterPropertiesChoice.ppmedia);
		fillSupportedMedia(media);
		
		// tell: ppstapple = false
		ValueRecord<BOOLEAN> ppstappleRecord = (ValueRecord<BOOLEAN>)results.properties.add().setChoice(PrinterPropertiesChoice.ppstaple);
		ppstappleRecord.value.set(false);
		
		// tell: pptwoSided = false
		ValueRecord<BOOLEAN> pptwoSidedRecord = (ValueRecord<BOOLEAN>)results.properties.add().setChoice(PrinterPropertiesChoice.pptwoSided);
		pptwoSidedRecord.value.set(false);
	}
	
	/*
	 * GetPrintRequestStatus: PROCEDURE [printRequestID: RequestID]
	 *  RETURNS [status: RequestStatus]
	 *  REPORTS [ServiceUnavailable, SystemError, Undefined] = 2;
	 */
	public static void getPrintRequestStatus(GetPrintRequestStatusParams params, GetPrintRequestStatusResults results) {
		System.out.printf("####### %s :: getPrintRequestStatus( requestId: %04X %04X %04X %04X %04X )\n",
				printServiceName,
				params.printRequestID.get(0).get(),
				params.printRequestID.get(1).get(),
				params.printRequestID.get(2).get(),
				params.printRequestID.get(3).get(),
				params.printRequestID.get(4).get());
		
		StatusEnum statusValue;
		
		boolean isUnitTest 
				  = params.printRequestID.get(0).get() == 0xFFFF &&
					params.printRequestID.get(1).get() == 0xFFFF &&
					params.printRequestID.get(2).get() == 0xFFFF &&
					params.printRequestID.get(3).get() == 0xFFFF &&
					params.printRequestID.get(4).get() == 0xFFFF;
		
		if (isUnitTest) {
			statusValue = StatusEnum.rejected; // only unittests will have this status
		} else {
			RequestID reqId = params.printRequestID;
			String jobName = String.format("%04X_%04X_%04X", reqId.get(2).get(), reqId.get(3).get(), reqId.get(4).get());
			
			statusValue = readPrintRequestStatus(jobName);
			
			if (statusValue == null) {
				String ipFilename = String.format("%s/job_%s.ip", outputDirectoryName, jobName);
				File ipFile = new File(ipFilename);
				statusValue = (ipFile.exists())
						? StatusEnum.completed
						: StatusEnum.unknown;
			}
		}
		
		StatusRecord status = (StatusRecord)results.status.add().setChoice(RequestStatusChoice.status);
		status.value.set(statusValue);
	}
	
	/*
	 * GetPrinterStatus: PROCEDURE
	 *  RETURNS [status: PrinterStatus]
	 *  REPORTS [ServiceUnavailable, SystemError, Undefined] = 3;
	 */
	public static void getPrinterStatus(RECORD params, GetPrinterStatusResults results) {
		System.out.printf("####### %s :: getPrinterStatus()\n", printServiceName);
		
		SpoolerStatusRecord spoolerStatus = (SpoolerStatusRecord)results.status.add().setChoice(PrinterStatusChoice.spooler);
		spoolerStatus.value.set(SpoolerStatusEnum.available);
		
		FormatterStatusRecord formatterStatus = (FormatterStatusRecord)results.status.add().setChoice(PrinterStatusChoice.formatter);
		formatterStatus.value.set(FormatterStatusEnum.available);
		
		PrinterStatusRecord printerStatus = (PrinterStatusRecord)results.status.add().setChoice(PrinterStatusChoice.printer);
		printerStatus.value.set(PrinterStatusEnum.available);
		
		MediaRecord media = (MediaRecord)results.status.add().setChoice(PrinterStatusChoice.media);
		fillSupportedMedia(media);
	}
	
	/*
	 * internals
	 */
	
	private static void fillSupportedMedia(MediaRecord media) {
		for (PaperKnownSize paperSize : supportedPaperSizes) {
			CHOICE<MediumChoice> medium = media.value.add();
			PaperRecord paperRecord = (PaperRecord)medium.setChoice(MediumChoice.paper);
			KnownSizeRecord knownSizeRecord = (KnownSizeRecord)paperRecord.value.setChoice(PaperChoice.knownSize);
			knownSizeRecord.paperKnownSize.set(paperSize);
		}
	}
	
	private static class JobProcessor implements Runnable {
		private final String jobName;
		private final String ipFilename;
		private final String filenameBase;
		private final String printObjectName;
		private final String senderName;
		private final PaperKnownSize paperSize;
		
		private final Thread thread;
		
		private JobProcessor(
					String jobName,
					String ipFilename,
					String filenameBase,
					String printObjectName,
					String senderName,
					PaperKnownSize paperSize) {
			this.jobName = jobName;
			this.ipFilename = ipFilename;
			this.filenameBase = filenameBase;
			this.printObjectName = (printObjectName != null) ? printObjectName : "(no print object name)";
			this.senderName = (senderName != null) ? senderName : "(no sender name)";
			this.paperSize = (paperSize != null) ? paperSize : supportedPaperSizes.get(0);
						
			this.thread = new Thread(this);
			this.thread.setDaemon(true);
			this.thread.setName("PrintJob " + this.jobName);
			this.thread.start();
		}

		@Override
		public void run() {			
			try {
				InterpressUtils ipUtils = new InterpressUtils();
				
				// we start working...
				writePrintRequestStatus(jobName, StatusEnum.inProgress);
				System.out.printf("Job %s ... begin processing\n", this.jobName);
				
				// optionally produce a readable version of the interpress master
				if (disassembleIpFiles) {
					System.out.printf("Job %s   ... begin disassembling ip master\n", this.jobName);
					String ipTextFilename = filenameBase + ".interpress";
					try(InputStream bis = new BufferedInputStream(new FileInputStream(ipFilename));
						PrintStream ps = new PrintStream(new FileOutputStream(ipTextFilename))) {
						ipUtils.readableFromBinary(bis, ps);
					}
					System.out.printf("Job %s   ... done disassembling ip master\n", this.jobName);
				}
				
				// optionally process ip to postscript
				if (ip2psProc != null) {
					System.out.printf("Job %s   ... begin producing postscript for ip master\n", this.jobName);
					String psTextFilename = filenameBase + ".ps";
					try(InputStream bis = new BufferedInputStream(new FileInputStream(ipFilename));
						PrintStream ps = new PrintStream(new FileOutputStream(psTextFilename))) {
						ipUtils.generatePostscript(
								bis,
								ps,
								ip2psProc,
								this.jobName,
								this.printObjectName,
								this.senderName);
					}
					System.out.printf("Job %s   ... done producing postscript for ip master\n", this.jobName);
					
					// optionally invoke postprocessor for postscript file
					if (psPostprocessor != null) {
						System.out.printf("Job %s   ... begin post-processing generated postscript\n", this.jobName);
						ProcessBuilder pb = new ProcessBuilder(
								psPostprocessor,
								psTextFilename,
								this.paperSize.toString(),
								this.jobName,
								this.printObjectName,
								this.senderName)
							.redirectErrorStream(true);
						Process p = pb.start();
						String psTextFilenameProcessLog = filenameBase + ".ps.log";
						try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
							 PrintStream log = new PrintStream(new FileOutputStream(psTextFilenameProcessLog))) {
							String stdoutLine;
							while((stdoutLine = br.readLine()) != null) {
								log.println(stdoutLine);
							}
						}
						p.waitFor();
						int rc = p.exitValue();
						System.out.printf("Job %s   ... done post-processing generated postscript, rc = %d\n", this.jobName, rc);
					}
				}
				
				// done "without" problems
				writePrintRequestStatus(jobName, StatusEnum.completed);
				System.out.printf("Job %s ... done processing\n", this.jobName);
				return;
				
			} catch (IOException ioe) {
				System.out.printf("##\n### Job %s : IOException(%s) => \n##\n", this.jobName, ioe.getMessage());
				ioe.printStackTrace();
			} catch (InterpressException e) {
				// ignored...
				System.out.printf("## Job %s : InterpressException(%s) ... ignored\n", this.jobName, e.getMessage());
			} catch (Exception e) {
				System.out.printf("##\n### Job %s : Exception(%s) => \n##\n", this.jobName, e.getMessage());
				e.printStackTrace();
			}
			
			// as we had problems
			writePrintRequestStatus(jobName, StatusEnum.aborted);
			System.out.printf("Job %s ... processing aborted\n", this.jobName);
		}
		
	}
}
