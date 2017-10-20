package org.twak.tweed.dbg;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.twak.utils.Filez;

public class Imagez {

	public static void writeSummary( File file, List<BufferedImage> images ) {

		int width = 0;
		int height = 0;
		for (BufferedImage vi : images) {
			width = Math.max(width, vi.getWidth());
			height += vi.getHeight();
		}
		
		if (width == 0 || height == 0)
			return;
		
		BufferedImage out = new BufferedImage( width, height, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D g = out.createGraphics();
		
		int y = 0;
		for (BufferedImage vi : images) {
			g.drawImage( vi, ( width - vi.getWidth() ) /2 , y, vi.getWidth(), vi.getHeight(), null );
			y += vi.getHeight();
		}
		
		
		
		try {
			ImageIO.write( out,  Filez.getExtn( file.getName() ) , file );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
	}

}
