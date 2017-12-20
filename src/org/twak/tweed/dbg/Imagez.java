package org.twak.tweed.dbg;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageOutputStream;

import org.twak.utils.Filez;

public class Imagez {

	public static void writeSummary( File file, List<BufferedImage> images ) {

		int width = 0;
		int height = 0;
		for ( BufferedImage vi : images ) {
			width = Math.max( width, vi.getWidth() );
			height += vi.getHeight();
		}

		if ( width == 0 || height == 0 )
			return;

		BufferedImage out = new BufferedImage( width, height, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = out.createGraphics();

		int y = 0;
		for ( BufferedImage vi : images ) {
			g.drawImage( vi, ( width - vi.getWidth() ) / 2, y, vi.getWidth(), vi.getHeight(), null );
			y += vi.getHeight();
		}

		try {
			ImageIO.write( out, Filez.getExtn( file.getName() ), file );
		} catch ( IOException e ) {
			e.printStackTrace();
		}

	}

	public static void writeJPG( BufferedImage rendered, float quality, File f ) {

		try {
			JPEGImageWriteParam jpegParams = new JPEGImageWriteParam( null );
			jpegParams.setCompressionMode( ImageWriteParam.MODE_EXPLICIT );
			jpegParams.setCompressionQuality( 1f );

			ImageWriter writer = ImageIO.getImageWritersByFormatName( "jpeg" ).next();
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode( ImageWriteParam.MODE_EXPLICIT ); // Needed see javadoc
			param.setCompressionQuality( quality ); // Highest quality
			writer.setOutput( new FileImageOutputStream( f ) );
			writer.write( null, new IIOImage( rendered, null, null ), jpegParams );
		} catch ( Throwable th ) {
			th.printStackTrace();
		}
	}

}
