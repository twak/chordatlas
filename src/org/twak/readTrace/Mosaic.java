package org.twak.readTrace;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.twak.utils.Parallel;

public class Mosaic {

	public Mosaic( File file ) {

		File result = new File( file.getParentFile(), "results" );
		result.mkdirs();

		try {
			List<String> lines = Files.walk( file.toPath() ).filter( x -> !x.toFile().isDirectory() ).flatMap( f -> {
				try {
					return Files.lines( f );
				} catch ( Throwable th ) {
					return Stream.empty();
				}
			} ).collect( Collectors.toList() );

			processLines( lines, result );

		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
	
	public Mosaic( List<String> lines, File result ) {
		processLines( lines, result );
	}

	public void processLines( List<String> lines, File result ) {
		
		lines.stream().forEach( s -> System.out.println( s ) );
		System.out.println( lines.size() + " lines found" );

		for (String s : lines) {
			System.out.println( "downloading " + s );
			tile (s, result);
		}
		
//		new Parallel<String, Integer>( lines, s -> tile( s, result ), s -> System.out.println( "complete " + s.stream().mapToInt( x -> x ).sum() ), true );
	}

	Pattern p = Pattern.compile( "[^_]*_[^_]*_[^_]*_[^_]*_[^_]*_[^_]*_(.*)" );

	public int tile( String s, File result ) {

		Matcher m = p.matcher( s );

		if ( m.matches() ) {

			String panoid = m.group( 1 );
			BufferedImage mosaic = new BufferedImage( 26 * 512, 13 * 512, BufferedImage.TYPE_3BYTE_BGR );
			Graphics g = mosaic.getGraphics();

			for ( int x = 0; x <= 25; x++ )
				for ( int y = 0; y <= 12; y++ ) {
					URL url;
					try {
						
//						int server = 0;
						url = new URL( "http://cbk0.google.com/cbk?output=tile&panoid=" + panoid + "&zoom=5&x=" + x + "&y=" + y );
//										http://cbk0.google.com/cbk?output=tile&panoid=IQ-D9TjXbY4xA3lNyFsdcg&zoom=5&x=25&y=2
//						https://geo1.ggpht.com/cbk?cb_client=apiv3&panoid=cN9xDztcLMyKpquQerqUFQ&output=tile&x=3&y=2&zoom=3&nbt&fover=2
							
						BufferedImage img = null;
//						while ( server < 4 ) {
//							url = new URL( "https://geo" + server + ".ggpht.com/cbk?output=tile&panoid=" + panoid + "&zoom=5&x=" + x + "&y=" + y );
//							try {
								img = ImageIO.read( url );
//							} catch ( IIOException e ) {
//								server++;
//								System.out.println("trying geo"+server);
//							}
//						}

						if (img != null)
							g.drawImage( img, x * 512, y * 512, null );
						
					} catch ( Throwable th ) {
						th.printStackTrace();
					}
				}
			g.dispose();

			try {

				ImageWriter writer = ImageIO.getImageWritersByFormatName( "jpg" ).next();
				ImageWriteParam param = writer.getDefaultWriteParam();
				param.setCompressionMode( ImageWriteParam.MODE_EXPLICIT ); // Needed see javadoc
				param.setCompressionQuality( 1f ); // Highest quality

				ImageOutputStream ios = ImageIO.createImageOutputStream( new File( result, s + ".jpg" ) );
				writer.setOutput( ios );
				writer.write( mosaic );

			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}

		return 1;
	}

	public static void main( String[] args ) {
		try {
			new Mosaic( new File( args[ 0 ] ) );
		} catch ( ArrayIndexOutOfBoundsException e ) {
			System.out.println( "usage: mosaic directory" );
		}
	}

}
