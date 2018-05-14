package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.twak.utils.Mathz;
import org.twak.utils.ui.Show;
import org.twak.viewTrace.Bin;

/**
 * https://github.com/serhiy/texture-manipulation/tree/master/src/main/java/xyz/codingdaddy/texture/manipulation
 *
 */

public class NormSpecGen {

	public BufferedImage rgb, labels;
	public BufferedImage norm;
	public BufferedImage spec;
	public Map<Color, Color> specLookup;
	
	public NormSpecGen( BufferedImage rgb, BufferedImage labels, Map<Color, Color> spedLookup ) {
		this.rgb = rgb;
		this.labels = labels;
		this.specLookup = spedLookup;
		
		buildMaps();
	}

	public void buildMaps() {

		Bin colors = new Bin( 0, 256, 256, false );
		
		int width = rgb.getWidth(), height = rgb.getHeight();
		
		double[][][] heights = new double[rgb.getWidth()][rgb.getHeight()][4];
		
		for ( int line = 0; line < height; line++ ) {
			for ( int column = 0; column < width; column++ ) {
				int color = rgb.getRGB( column, line );
				double grey = ( ( ( ( color ) & 0xFF ) * .299f ) + ( ( ( color >> 8 ) & 0xFF ) * .587f ) + ( ( color >> 16 ) & 0xFF ) * .114f );
				heights[column][line][0] = grey / 256;
				colors.add( grey, 1 );
			}
		}

		double middle = colors.maxI() / 256.;
		double specThreshold = colors.getAccumulative( 0.95 ) / 256.;

		
		double[][] octaves = new double[][] {{1, 0.5}, {3, 0.5}};//, {0.5, 5} };
		int[] t1 = new int[3], t2 = new int[3];
		
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
		
		norm = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		spec = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		
		for ( int x = 0; x < width; x++ )
			for ( int y = 0; y < height; y++ ) {
				
				double ilen =  0.5 / Math.sqrt( heights[x][y][1] * heights[x][y][1] + heights[x][y][2] * heights[x][y][2] + 1 );
				
				int normOut = 
						( ( (int) ( (heights[x][y][1] * ilen + 0.5) * 255 ) ) << 16 ) + 
						( ( (int) ( (heights[x][y][2] * ilen + 0.5) * 255 ) ) <<  8 ) + 
						( ( (int) ( (                   ilen + 0.5) * 255 ) ) <<  0 );

				int specOut = Color.darkGray.getRGB();
				
				if ( specLookup != null ) {
					
					int s = labels.getRGB( x, y );
					
					specOut = Color.black.getRGB();
					
					for (Color src : specLookup.keySet()) {
						Color dest = specLookup.get( src );
						
						if ( distance( s, src.getRGB(), t1, t2 ) < 10 ) {
							specOut = dest.getRGB();
							
							if (specOut == Color.white.getRGB()) // shiny things are float
								normOut = 0x8080ff;
						}
					}
					
					
//					if ( distance( s, Pix2Pix.CMPLabel.Window.rgb.getRGB(), t1, t2 ) < 10 ) {
//						specOut = Color.white.getRGB();
//						normOut = 0x8080ff;
//					} else if ( distance( s, Pix2Pix.CMPLabel.Door.rgb.getRGB(), t1, t2 ) < 10 || 
//							    distance( s, Pix2Pix.CMPLabel.Shop.rgb.getRGB(), t1, t2 ) < 10 ) {
//						specOut = Color.darkGray.getRGB();
//					} else {
//					
//					}
				}
				
				spec.setRGB( x, y, specOut );
				norm.setRGB( x, y, normOut );
			}
	}

	private void normNorm( double ym, double yp, double xm, double xp, double weight, double[] result ) {
		
		// normals, normalized in r3 between 0 and 1.
		double xd = xp-xm, yd = yp - ym, zd = 1;
		
		result[ 1 + 0 ] += xd * weight;
		result[ 1 + 1 ] += yd * weight;
		result[ 1 + 2 ] += zd * weight;
	}

	private double distance( int a, int b, int[] t1, int[]t2 ) {
		toComp(a, t1);
		toComp(b, t2);
		
		return Mathz.L2( t1, t2);
	}

	private void toComp( int c, int[] t2 ) {
		t2[0] =  ( ( c >> 16 ) & 0xFF );
		t2[1] =  ( ( c >> 8  ) & 0xFF );
		t2[2] =  ( ( c       ) & 0xFF );
	}
	
//	public static void main( String[] args ) {
//
//		try {
//
//			NormSpecGen hm = new NormSpecGen( 
//					ImageIO.read( new File( "/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/regent/tex.jpg" ) ), 
//					ImageIO.read( new File( "/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/regent/labels.jpg" ) )
//				);
//
//			new Show( hm.norm );
//			new Show( hm.spec );
//
//		} catch ( IOException e ) {
//			e.printStackTrace();
//		}
//	}
}
