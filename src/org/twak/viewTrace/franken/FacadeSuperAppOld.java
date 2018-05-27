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
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.apache.commons.io.FileUtils;
import org.twak.tweed.Tweed;
import org.twak.tweed.tools.FacadeTool;
import org.twak.utils.Imagez;
import org.twak.utils.Mathz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.NormSpecGen;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public class FacadeSuperAppOld extends App implements HasApp {

	FacadeTexApp parent;
	public double scale = 120;
	
	public FacadeSuperAppOld( FacadeTexApp parent ) {
		super( (HasApp) null );
		this.hasA = this;
		this.parent = parent;
	}

	public FacadeSuperAppOld( FacadeSuperAppOld facadeCoarse ) {
		super( (App) facadeCoarse );
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
		return new FacadeSuperAppOld( this );
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add ( new AutoDoubleSlider( this, "scale", "scale", 20, 200 ) {
			public void updated(double value) {
				System.out.println( globalUpdate );
//				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		return out;
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		Map<MiniFacade, FacState> todo = new LinkedHashMap();
		
		FacadeSuperAppOld fs = (FacadeSuperAppOld ) batch.get( 0 );
		
		MiniFacade mf = (MiniFacade) fs.parent.hasA;
		
		{
			try {

				BufferedImage src = ImageIO.read( Tweed.toWorkspace( ((FacadeTexApp) parent).coarse ) );
//				src = Imagez.scaleLongest( src, 128 );
//				ImageIO.write( src, "png", new File( "/home/twak/Desktop/foo/" + System.nanoTime() + "_orig.png" ) );

				
				DRectangle mini = Pix2Pix.findBounds( mf );
				
//				BufferedImage tiny = Imagez.scaleTo( src, (int) ( mini.width * 10 ), (int) ( mini.width * 10 ) );
//				Imagez.gaussianNoise( tiny, 0.03 );
				
				BufferedImage highRes = Imagez.scaleTo( src,
						(int)( mini.width * scale),
						(int)( mini.height * scale) );
						
//						, BufferedImage.TYPE_3BYTE_BGR);
//				{
//					Graphics2D g= tiny.createGraphics();
//					g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
//					g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
//					g.drawImage( src, 0, 0, highRes.getWidth(), highRes.getHeight(), null );
//					g.dispose();
//				}
				
//				BufferedImage blurred = new FastBlur().processImage( src, 5 );
				
//				{
//					Graphics2D g = highRes.createGraphics();
//					g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
//					g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
//
//					g.drawImage( src, 0, 0, highRes.getWidth(), highRes.getHeight(), null );
//					g.dispose();
//				}
				
				todo.put( mf, new FacState(highRes) );
				
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
		}
		
		facadeContinue (todo, whenDone );
	}
	
	final static int overlap = 0, nonOverlap = 256 - overlap; 
	
	private synchronized void facadeContinue( Map<MiniFacade, FacState> todo, Runnable whenDone ) {

		if (todo.isEmpty()) {
			whenDone.run();
			return;
		}

		int MAX_CONCURRENT = 32;

		Pix2Pix p2 = new Pix2Pix ( NetInfo.get(this) );
		
		int count = 0;
		for ( Map.Entry<MiniFacade, FacState> e : todo.entrySet() ) {
			try {

				FacState state = e.getValue();
				state.nextTiles.clear();

				for ( int z = 0; z <= state.next; z++ ) {

					int x = z, y = state.next - z;

					if ( x > state.maxX || y > state.maxY )
						continue;

					BufferedImage toProcess = new BufferedImage( 512, 256, BufferedImage.TYPE_3BYTE_BGR );

					TileState ts = new TileState( state, System.nanoTime() + "_" + x + "_" + y + "_" + count, x, y );
					state.nextTiles.add( ts );
					{
						Graphics2D g = toProcess.createGraphics();
						g.drawImage( state.big, 
								-state.big.getWidth() + nonOverlap * ( x + 1 ) + overlap + 256,
								-state.big.getHeight() + nonOverlap * ( y + 1 ) + overlap,
								state.big.getWidth(), state.big.getHeight(), null );

						g.dispose();
					}
					
					p2.addInput( toProcess, ts, e.getKey().app.zuper.styleZ );

					System.out.println( "++" + x + ", " + y );
					count++;
				}
				
				state.next++;
				
//				if ( count > MAX_CONCURRENT )
//					break;
				
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}
			
		p2.submit ( new Job ( new JobResult() {
				@Override
				public void finished( Map<Object, File> results ) {
					
					
					// patch images with new tiles...
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						TileState tile = (TileState) e.getKey();
						FacState state = tile.state;
//					for ( FacState state : todo.values() ) {
//						for ( TileState tile : state.nextTiles ) 
						{
						try {
							
							File texture = e.getValue();//new File( f, tile.nextTile + ".png" );
							if ( texture.exists() && texture.length() > 0 ) {

								BufferedImage rgb = ImageIO.read( texture );
								Graphics2D g = state.big.createGraphics();
								
								g.drawImage( rgb, 
										state.big.getWidth () - nonOverlap * (tile.nextX+1) -overlap, 
										state.big.getHeight() - nonOverlap * (tile.nextY+1) -overlap, 
										rgb.getWidth(), 
										rgb.getHeight(), null );
								g.dispose();
							}
							
//							if ( state.nextY == state.maxY && state.nextX == state.maxX ) {
//								state.nextX = state.nextY = -1; // done
//							} else if ( state.nextX == state.maxX ) {
//								state.nextY++;
//								state.nextX = 0;
//							} else {
//								state.nextX++;
//							}
							
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
						}
					}
					
//					update.run();
					
					// finished - import texture!
					for (MiniFacade mf : new ArrayList<> (todo.keySet())) {
						
						FacState state = todo.get( mf );
						
						if (state.next > state.maxX + state.maxY ) { // done
							
							todo.remove( mf );
							
							int dim = Mathz.nextPower2( Math.max( state.big.getWidth(), state.big.getHeight())  );
							state.big = Imagez.scaleTo( state.big, dim, dim);
							
							NormSpecGen ns = new NormSpecGen( state.big, null, null );

							String dest = "scratch/"+System.nanoTime() +"_"+ Math.random();
							
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
					
//					try {
//						FileUtils.deleteDirectory( f );
//					} catch ( IOException e1 ) {
//						e1.printStackTrace();
//					}

					facadeContinue( todo, whenDone );
				}
			} ) );
		
	}
		
	private static class FacState {
		
		BufferedImage big;
		int next = 0, maxX, maxY;
		
		public List<TileState> nextTiles = new ArrayList<>();
		
		public FacState( BufferedImage big ) {
			this.big = big;

			this.maxX = ( big.getWidth()  / nonOverlap );
			this.maxY = ( big.getHeight() / nonOverlap );
		}
	}
	
	private static class TileState {
		
		int nextX = 0, nextY = 0;
		public String nextTile;
		FacState state;
		
		public TileState( FacState state, String name, int x, int y ) {
			this.state = state;
			this.nextX = x;
			this.nextY = y;
			this.nextTile = name;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
}
