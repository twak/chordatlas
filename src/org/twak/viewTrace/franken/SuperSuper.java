package org.twak.viewTrace.franken;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.camp.Tag;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.utils.Imagez;
import org.twak.utils.Mathz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;

public abstract class SuperSuper <A> extends App {

	public double scale = 120;
	
	final static int overlap = 20, tileWidth = 256 - overlap * 2, outputPPM = 40, MAX_CONCURRENT = 32;
	
	public SuperSuper() {}
	
	public SuperSuper( SuperSuper facadeCoarse ) {
		super( (App) facadeCoarse );
	}

	@Override
	public abstract App getUp(AppStore ac);

	@Override
	public MultiMap<String, App> getDown(AppStore ac) {
		return new MultiMap<>();
	}

	@Override
	public abstract App copy();

	// add to the todo list; remember to pad coarse by overlap in all directions
	public abstract void drawCoarse( MultiMap<A, FacState> todo, AppStore ac ) throws IOException;
	
	public abstract void setTexture( FacState<A> state, BufferedImage maps, AppStore ac );
	
	
	@Override
	public JComponent createNetUI( Runnable globalUpdate, SelectedApps apps ) {
		
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add ( new AutoDoubleSlider( this, "scale", "scale", 20, 200 ) {
			public void updated(double value) {
				
				for (App a : apps)
					((SuperSuper)a).scale = value;
				
				globalUpdate.run();
			};
		}.notWhileDragging() );
		
		return out;
	}
	
	
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch, AppStore ac) {
		
		MultiMap<A, FacState> todo = new MultiMap<>();
		
		for (App a : batch)
		{
			SuperSuper fs = (SuperSuper ) a;
			
			try {
				fs.drawCoarse( todo, ac );
				
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
		}
		
		facadeContinue (todo, whenDone, ac );
	}

	
	private synchronized void facadeContinue( MultiMap<A, FacState> todo, Runnable whenDone, AppStore ac ) {

		if (todo.isEmpty()) {
			whenDone.run();
			return;
		}

		Pix2Pix p2 = new Pix2Pix ( NetInfo.get(this) );
		
		int count = 0;
		for (A a : todo.keySet() ) 
			for (FacState<A> state : todo.get(a)) {
			try {

				System.out.println("super batch " + state.nextTiles.size());
				
				while ( count < MAX_CONCURRENT && !state.nextTiles.isEmpty() ) {
					
					TileState ts = state.nextTiles.remove( 0 );
					
					{
						BufferedImage toProcess = new BufferedImage( 256, 256, BufferedImage.TYPE_3BYTE_BGR );
						
						Graphics2D g = toProcess.createGraphics();
						
						g.drawImage( state.bigCoarse, 
								- tileWidth * ts.nextX ,
								- tileWidth * ts.nextY ,
								null );
						g.dispose();
						
						ts.coarse = toProcess;
						
						SuperSuper ss = ac.get(this.getClass(), a);
						
						System.out.println (" z is " + Arrays.toString( ss.styleZ ) );
						
						p2.addInput( toProcess, null, null, ts, ss.styleZ, null );
					}
					count++;
				}
								
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}
			
		p2.submit ( new Job ( new JobResult() {
				@Override
				public void finished( Map<Object, File> results ) {
					
					
					// patch images with new tiles...
					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						TileState ts = (TileState) e.getKey();
						FacState state = ts.state;
						
						try {
							
							File texture = e.getValue();
							
							if ( texture.exists() && texture.length() > 0 ) {

								BufferedImage rgb = ImageIO.read( texture );
								
								rgb = createAlphaFeather (5, overlap-5, rgb);
								
								Graphics2D g = state.bigFine.createGraphics();
								
								g.drawImage( rgb, //ts.coarse, 
										
										tileWidth *  ts.nextX    ,
										tileWidth *  ts.nextY    ,
										tileWidth * (ts.nextX+1) + overlap * 2,
										tileWidth * (ts.nextY+1) + overlap * 2,
										
										0,0,rgb.getWidth(), rgb.getHeight(),
										
										null );
										
//								g.setStroke( new BasicStroke( 4 ) );
//								g.drawRect( tileWidth * ts.nextX     + overlap,
//										tileWidth * ts.nextY     + overlap,
//										tileWidth, tileWidth );
								
								g.dispose();
							}
							
						} catch ( Throwable th ) {
							th.printStackTrace();
						}
					}
					
					MultiMap<A, FacState> nextTime = new MultiMap<>();
					
					// finished - import texture!
					for (A mf : new ArrayList<> (todo.keySet())) 
						
						for (FacState<A> state : todo.get (mf) ) {
						
						if (!state.nextTiles.isEmpty()) {
							nextTime.put( mf, state );
						}
						else {
							
							DRectangle mfb = state.boundsInMeters;
							
							int dim = Mathz.nextPower2( (int)  Math.max( mfb.width * outputPPM, mfb.height * outputPPM)  );
							
//							try {
//								ImageIO.write( state.bigFine, "png", new File ("/home/twak/Desktop/blah_fine.png") );
//								ImageIO.write( state.bigCoarse, "png", new File ("/home/twak/Desktop/blah_coarse.png") );
//								ImageIO.write( ImageIO.read( Tweed.toWorkspace( ((FacadeTexApp) parent).coarse ) ), "png", new File ("/home/twak/Desktop/blah_low.png") );
//							} catch ( IOException e2 ) {
//								e2.printStackTrace();
//							}
							
							BufferedImage cropped = new BufferedImage( dim, dim, BufferedImage.TYPE_3BYTE_BGR );
							{
								Graphics2D g = cropped.createGraphics();
								
								g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
								g.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
								
								g.drawImage( state.bigFine,
										0, 0,
										cropped.getWidth(), cropped.getHeight(), 
										overlap, overlap,
										state.bigFine.getWidth() - overlap, state.bigFine.getHeight() - overlap, null );
								
								g.setComposite( AlphaComposite.getInstance( AlphaComposite.SRC_OVER, (float) TweedSettings.settings.superResolutionBlend ) );
								
								g.drawImage( state.bigCoarse,
										0, 0,
										cropped.getWidth(), cropped.getHeight(), 
										overlap, overlap,
										state.bigFine.getWidth() - overlap, state.bigFine.getHeight() - overlap, null );
							}
							
							ac.get(SuperSuper.this.getClass(), mf).setTexture( state, cropped, ac );// new BufferedImage[] { cropped, ns.norm, ns.spec} );

						}
					}

					facadeContinue( nextTime, whenDone, ac );
				}

			} ) );
	}
	
	private BufferedImage createAlphaFeather( int nothing, int linear, BufferedImage rgb ) {
		
		BufferedImage withAlpha = new BufferedImage( rgb.getWidth(), rgb.getHeight(), BufferedImage.TYPE_4BYTE_ABGR );
//		BufferedImage withAlpha = new BufferedImage( rgb.getWidth(), rgb.getHeight(), BufferedImage.TYPE_3BYTE_BGR );
		

//		Graphics2D g = withAlpha.createGraphics();
//		g.drawImage( rgb, 0, 0, rgb.getWidth(), rgb.getHeight(), null );
//		g.dispose();

//		if (false)
		for (int x = 0; x < rgb.getWidth(); x++)
			for (int y = 0; y < rgb.getHeight(); y++) {
				
				int col = rgb.getRGB( x, y );
				
				int alpha = Math.min( fade(x, rgb.getWidth(), nothing, linear), fade(y, rgb.getHeight(), nothing,linear) ) ;
				
//				System.out.println(alpha);
//				(255- + 
				col = col & 0xffffff | (alpha << 24 );
//						0x77 << 24 +
//					  alpha << 16 +
//					  alpha << 8 +
//					  alpha << 0;
				
				withAlpha.setRGB( x, y, col );
			}
		
		return withAlpha;
	}
	
	private static int fade( int x, int max, int nothing, int linear ) {
		
		int middle = max / 2;
		
		return Mathz.clamp ( (-Math.abs ( -x + middle ) + (middle - nothing)) * 255 / linear, 0, 255 );
	}

	static class FacState<A> {
		
		public A mf;

		BufferedImage bigCoarse, bigFine;
		
		public List<TileState> nextTiles = new ArrayList<>();

		public DRectangle boundsInMeters;

		public Tag tag;
		
		public FacState( BufferedImage coarse, 	A mf, DRectangle boundsInMeters, Tag tag ) {
			
			this.bigCoarse = coarse;
			this.mf = mf;
			this.boundsInMeters = boundsInMeters;
			this.bigFine = Imagez.clone( coarse );
			this.tag = tag; // only roofs
		}
	}
	
	static class TileState {
		
		public BufferedImage coarse;
		int nextX = 0, nextY = 0;
		FacState state;
		
		public TileState( FacState state, int x, int y ) {
			this.state = state;
			this.nextX = x;
			this.nextY = y;
		}
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {AppMode.Off, AppMode.Net};
	}
}
