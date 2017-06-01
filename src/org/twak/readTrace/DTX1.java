package org.twak.readTrace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public class DTX1 {
	
	private static byte [] dxtHeader = new byte[] {
			  0x44, 0x44, 0x53, 0x20, 0x7C, 0x00, 0x00, 0x00, 
			  0x07, 0x10, 0x08, 0x00, (byte)0x94, 0x00, 0x00, 0x00, 
			  (byte)0xD1, 0x00, 0x00, 0x00, (byte)0xA8, 0x01, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 
			  0x49, 0x4D, 0x41, 0x47, 0x45, 0x4D, 0x41, 0x47, 
			  0x49, 0x43, 0x4B, 0x00, 0x00, 0x00, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 
			  0x04, 0x00, 0x00, 0x00, 0x44, 0x58, 0x54, 0x31, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
			  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };


	public static File toPNG(byte[] dssNoHeader, File folder, int width, int height) throws Throwable {
		
		File uniquePNG;
		int i = 0;
		do {
			uniquePNG = new File (folder, String.format("%06d.png", i++) );
		} while (uniquePNG.exists());
		
		createDTX(dssNoHeader, folder, width, height, uniquePNG);
		
		return uniquePNG;
	}

	private static void createDTX(byte[] dssNoHeader, File folder, int width, int height, File out)
			throws IOException, InterruptedException {
		// as https://msdn.microsoft.com/en-us/library/windows/desktop/bb943982(v=vs.85).aspx
		byte[] header = Arrays.copyOf(dxtHeader, dxtHeader.length + dssNoHeader.length);
		header[12]=(byte) ( height & 0xff );
		header[13]=(byte) ((height >> 8) & 0xff);
		
		header[16]=(byte) ( width & 0xff );
		header[17]= (byte)((width >> 8) & 0xff);

		int pitch = Math.max(1, ((width+3)/4) * 8);
		
		header[20]=(byte) ( pitch & 0xff );
		header[21]= (byte)((pitch >> 8) & 0xff);
		
		System.arraycopy(dssNoHeader, 0, header, dxtHeader.length, dssNoHeader.length);
		
		File ddsFile = new File(folder, "tmp.dds");
		
		Files.write(ddsFile.toPath(), header);
		Runtime.getRuntime().exec(new String[] {"convert", ddsFile.getAbsolutePath(), out.getAbsolutePath() } ).waitFor();
		
		System.out.println("converted texture to png to " + out.getAbsolutePath() );
	}
	
	public static void main(String[] args) throws IOException, Throwable {
		
		File folder = new File ("/home/twak/dump/");
//		for (File f : folder.listFiles())
//			if (f.getName().endsWith(".bin"))
//				DTX1.createDTX(Files.readAllBytes(f.toPath()), 
//						new File("/home/twak/dump/"), 256, 256, new File(folder, f.getName()+".png"));

		DTX1.createDTX(Files.readAllBytes(new File(folder, "blob_call1796721.bin").toPath()), 
				new File("/home/twak/dump/"), 256, 512, new File(folder, "test.png"));
	}
}
