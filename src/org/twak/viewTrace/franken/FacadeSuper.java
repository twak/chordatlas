package org.twak.viewTrace.franken;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.tweed.tools.FacadeTool;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.NormSpecGen;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.facades.Pix2Pix.Job;
import org.twak.viewTrace.facades.Pix2Pix.JobResult;

public class FacadeSuper extends App implements HasApp {

	FacadeApp parent;
	
	public FacadeSuper( FacadeApp parent ) {
		super( null, "super-facade", "super3", 8, 256 );
		this.hasA = this;
		this.parent = parent;
	}

	public FacadeSuper( FacadeSuper facadeCoarse ) {
		super( facadeCoarse );
	}

	@Override
	public App getUp() {
		return parent;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return new MultiMap<>();
	}

	@Override
	public App copy() {
		return new FacadeSuper( this );
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		Map<MiniFacade, FacState> todo = new LinkedHashMap();
		
		FacadeSuper fs = (FacadeSuper ) batch.get( 0 );
		
		MiniFacade mf = (MiniFacade) fs.parent.hasA;
		
		{
			try {

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( mf.app.texture ) );
//				src = Imagez.scaleLongest( src, 128 );
//				ImageIO.write( src, "png", new File( "/home/twak/Desktop/foo/" + System.nanoTime() + "_orig.png" ) );

				
				DRectangle mini = Pix2Pix.findBounds( mf );
				
				BufferedImage highRes = new BufferedImage ( 
						(int)( mini.width * FacadeTool.pixelsPerMeter),
						(int)( mini.height * FacadeTool.pixelsPerMeter), BufferedImage.TYPE_3BYTE_BGR);
				
				{
					Graphics2D g = highRes.createGraphics();
					g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
					g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
					g.drawImage( src, 0, 0, highRes.getWidth(), highRes.getHeight(), null );
					g.dispose();
				}
				
				todo.put( mf, new FacState(highRes) );
				
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
		}
		
		facadeContinue (todo, whenDone );
	}
	

	private synchronized void facadeContinue( Map<MiniFacade, FacState> todo, Runnable whenDone ) {

		if (todo.isEmpty()) {
			whenDone.run();
			return;
		}
		
		int MAX_CONCURRENT = 32;
		
			int count = 0;
			for ( Map.Entry<MiniFacade, FacState> e : todo.entrySet() ) {
				try {

					FacState state = e.getValue();

					String name = System.nanoTime() + "_" + count;

					BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );

					state.nextTile = name;
					
					{
						Graphics2D g = toProcess.createGraphics();
						g.drawImage( state.big, 
								-state.big.getWidth () + 209 * (state.nextX+1) + 47 + 256, 
								-state.big.getHeight() + 209 * (state.nextY+1) + 47, 
								state.big.getWidth(),
								state.big.getHeight(),
								null );
						
						g.dispose();
					}
					
					Pix2Pix.addInput( toProcess, name, netName );

//					ImageIO.write( toProcess, "png", new File( "/home/twak/Desktop/test_" + name + ".png" ) );

					System.out.println( "++" + state.nextX +", " + state.nextY );

					if ( count > MAX_CONCURRENT )
						break;
				} catch ( Throwable th ) {
					th.printStackTrace();
				}
			}
			
			Pix2Pix.submit ( new Job ( netName,  System.nanoTime() + "_" + zAsString(), new JobResult() {
				@Override
				public void finished( File f ) {
					
					
					// patch images with new tiles...
					for ( Map.Entry<MiniFacade, FacState> e : todo.entrySet() ) {
						try {

							FacState state = e.getValue();
							
							File texture = new File( f, state.nextTile + ".png" );
							if ( texture.exists() && texture.length() > 0 ) {

								BufferedImage rgb = ImageIO.read( texture );
								
								File orig = new File( f, state.nextTile + ".png_label" );
								
//								String name = Math.random()+"";
//								ImageIO.write( rgb, "png", new File( "/home/twak/Desktop/foo/" + name + ".png" ) );
//								ImageIO.write( ImageIO.read( orig ), "png", new File( "/home/twak/Desktop/foo/" + name + "_orig.png" ) );
								
								Graphics2D g = state.big.createGraphics();
								g.drawImage( rgb, 
										state.big.getWidth () - 209 * (state.nextX+1) -47, 
										state.big.getHeight() - 209 * (state.nextY+1) -47, 
										rgb.getWidth(), 
										rgb.getHeight(), null );
								g.dispose();
							}
							
							if ( state.nextY == state.maxY && state.nextX == state.maxX ) {
								state.nextX = state.nextY = -1; // done
							} else if ( state.nextX == state.maxX ) {
								state.nextY++;
								state.nextX = 0;
							} else {
								state.nextX++;
							}
							
							if ( count > MAX_CONCURRENT )
								break;
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
					}
					
//					update.run();
					
					// finished - import texture!
					for (MiniFacade mf : new ArrayList<> (todo.keySet())) {
						FacState state = todo.get( mf );
						
						if (state.nextY == -1) { // done
							
							todo.remove( mf );
							
//							File texture = new File( f, "images/" + state.nextTile + "_fake_B.png" );
							
							NormSpecGen ns = new NormSpecGen( state.big, null );

							String dest =  "scratch/facade_" + state.nextTile ;
							
							try {
								ImageIO.write( state.big, "png", new File( Tweed.DATA + "/" + ( dest + ".png" ) ) );
								ImageIO.write( ns.norm  , "png", new File( Tweed.DATA + "/" + ( dest + "_norm.png" ) ) );
//								ImageIO.write( ns.spec  , "png", new File( Tweed.DATA + "/" + ( dest + "_spec.png" ) ) );
								
								mf.app.textureUVs = TextureUVs.SQUARE;
								mf.app.texture = dest + ".png";
								
								
							} catch ( IOException e1 ) {
								e1.printStackTrace();
							}
						}
					}
					
					try {
						FileUtils.deleteDirectory( f );
					} catch ( IOException e1 ) {
						e1.printStackTrace();
					}

					facadeContinue( todo, whenDone );
				}
			} ) );
		
	}

	
	private static class FacState {
		
		BufferedImage big;
		int nextX = 0, nextY = 0, maxX, maxY;
		public String nextTile;
		
		public FacState( BufferedImage big ) {
			this.big = big;

			this.maxX = ( big.getWidth()  / 200 );
			this.maxY = ( big.getHeight() / 200 );
		}
	}
	
	
	private static class Meta {
		String name;
		DRectangle mask;

		private Meta( String name, DRectangle mask ) {
			this.name = name;
			this.mask = mask;
		}
	}
}
