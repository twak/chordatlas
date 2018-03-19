package org.twak.viewTrace.facades;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.twak.utils.Mathz;
import org.twak.utils.ui.Show;
import org.twak.viewTrace.Bin;

/**
 * https://github.com/serhiy/texture-manipulation/tree/master/src/main/java/xyz/codingdaddy/texture/manipulation
 *
 */

public class NormSpecGen {

	BufferedImage toProcess;

	public NormSpecGen( BufferedImage read ) {
		this.toProcess = read;
	}

	public BufferedImage norm() {

		Bin colors = new Bin( 0, 256, 256, false );
		
		int width = toProcess.getWidth(), height = toProcess.getHeight();
		
		double[][][] heights = new double[toProcess.getWidth()][toProcess.getHeight()][4];
		
		for ( int line = 0; line < height; line++ ) {
			for ( int column = 0; column < width; column++ ) {
				int color = toProcess.getRGB( column, line );
				double grey = ( ( ( ( color ) & 0xFF ) * .299f ) + ( ( ( color >> 8 ) & 0xFF ) * .587f ) + ( ( color >> 16 ) & 0xFF ) * .114f );
				heights[column][line][0] = grey / 256;
				colors.add( grey, 1 );
			}
		}

		double middle = colors.maxI() / 256.;

		
		double[][] octaves = new double[][] {{1, 0.5}, {3, 0.5}};//, {0.5, 5} };
		
		for (int x = 0; x < width; x++ )
			for (int y = 0; y < height; y++ ) 
				for (double[] octave : octaves) {
				
					int dist = (int) octave[0];
					double weight = octave[1];
					
				int     xm1 = Math.max( 0  , x-dist        ),
						xp1 = Math.min( x+dist, width -1   ),
						ym1 = Math.max( 0  , y-dist        ),
						yp1 = Math.min( y+dist, height - 1 );
				
				normNorm (
						Math.abs( heights[x][ym1][0] - middle ), 
						Math.abs( heights[x][yp1][0] - middle ), 
						Math.abs( heights[xm1][y][0] - middle ), 
						Math.abs( heights[xp1][y][0] - middle ), weight, heights[x][y] );
			}
		
		BufferedImage nmap = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		
		for ( int x = 0; x < width; x++ )
			for ( int y = 0; y < height; y++ ) {
				
				double ilen =  0.5 / Math.sqrt( heights[x][y][1] * heights[x][y][1] + heights[x][y][2] * heights[x][y][2] + 1 );
				
				int rgb = 
						( ( (int) ( (heights[x][y][1] * ilen + 0.5) * 255 ) ) << 16 ) + 
						( ( (int) ( (heights[x][y][2] * ilen + 0.5) * 255 ) ) <<  8 ) + 
						( ( (int) ( (                   ilen + 0.5) * 255 ) ) <<  0 );

				nmap.setRGB( x, y, rgb );
			}

		return nmap;
	}

	private void normNorm( double ym, double yp, double xm, double xp, double weight, double[] result ) {
		
		// normals, normalized in r3 between 0 and 1.
		double xd = xp-xm, yd = yp - ym, zd = 1;
		
		result[ 1 + 0 ] += xd * weight;
		result[ 1 + 1 ] += yd * weight;
		result[ 1 + 2 ] += zd * weight;
	}

	public static void main( String[] args ) {

		try {

			BufferedImage hm = new NormSpecGen( ImageIO.read( new File( "/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/regent/tex.jpg" ) ) ).norm();

			new Show( hm );

		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}
}
